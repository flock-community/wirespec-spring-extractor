package community.flock.wirespec.spring.extractor.extract.kafka

import io.github.classgraph.ClassGraph
import java.lang.reflect.ParameterizedType

/**
 * Finds classes that hold a `KafkaTemplate<K, V>` field and recovers the
 * concrete value type `V` per field via reflection on the field's generic
 * signature.
 *
 * Discovery is class-name-string based: Spring Kafka is intentionally not on
 * extractor-core's main classpath, so the scanner cleanly returns empty when
 * `KafkaTemplate` is absent.
 */
internal object KafkaProducerScanner {

    private const val KAFKA_TEMPLATE_FQN = "org.springframework.kafka.core.KafkaTemplate"

    private val FRAMEWORK_EXCLUSIONS = listOf(
        "org.springframework",
        "org.springdoc",
        "org.apache",
    )

    /**
     * @property ownerClass class declaring the KafkaTemplate field.
     * @property fieldName name of the KafkaTemplate field (used by the
     *   bytecode walker to match GETFIELD instructions).
     * @property valueClass concrete `V` recovered from the field's generic signature.
     */
    data class TemplateField(
        val ownerClass: Class<*>,
        val fieldName: String,
        val valueClass: Class<*>,
    )

    fun scan(
        classLoader: ClassLoader,
        scanPackages: List<String>,
        basePackage: String?,
        onWarn: (String) -> Unit = {},
    ): List<TemplateField> {
        val graph = ClassGraph()
            .overrideClassLoaders(classLoader)
            .ignoreParentClassLoaders()
            .enableClassInfo()
            .enableFieldInfo()
            // Kotlin constructor-injected `private val` becomes a private field on
            // the bytecode side; the default ClassGraph filter would hide it.
            .ignoreFieldVisibility()

        val accepted = scanPackages.filter { it.isNotBlank() }
        if (accepted.isNotEmpty()) graph.acceptPackages(*accepted.toTypedArray())

        graph.scan().use { result ->
            val out = mutableListOf<TemplateField>()
            for (ci in result.allClasses) {
                if (FRAMEWORK_EXCLUSIONS.any { ci.name.startsWith("$it.") }) continue
                if (basePackage != null && !(ci.name.startsWith("$basePackage.") || ci.name == basePackage)) continue
                // `typeDescriptor` is the erased descriptor; for `KafkaTemplate<K, V>` it
                // stringifies to the bare FQN regardless of generics, so equality suffices.
                if (ci.fieldInfo.none { it.typeDescriptor?.toString() == KAFKA_TEMPLATE_FQN }) continue

                val cls = try { ci.loadClass() } catch (t: Throwable) {
                    onWarn("kafka.producer: skipping ${ci.name}: ${t.message}")
                    continue
                }
                for (field in cls.declaredFields) {
                    if (field.type.name != KAFKA_TEMPLATE_FQN) continue
                    val v = (field.genericType as? ParameterizedType)
                        ?.actualTypeArguments
                        ?.getOrNull(1) as? Class<*>
                    if (v == null || v == Any::class.java || v == java.lang.Object::class.java) {
                        onWarn("kafka.producer: skipping ${cls.name}.${field.name}: KafkaTemplate value type unresolved")
                        continue
                    }
                    out += TemplateField(cls, field.name, v)
                }
            }
            return out
        }
    }
}

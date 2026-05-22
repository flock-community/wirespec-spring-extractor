package community.flock.wirespec.spring.extractor.extract.kafka

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

/**
 * Discovers `KafkaTemplate.send` call sites in a class by linear bytecode scan.
 *
 * For each `INVOKEVIRTUAL KafkaTemplate.send(...)`, the most recent `GETFIELD`
 * is inspected to identify which `KafkaTemplate` field it was invoked on. That
 * field is looked up in [templateFields] to recover the concrete value type `V`,
 * which becomes the channel payload.
 *
 * Returns one [ProducerSite] per (enclosingMethod, valueClass) pair —
 * duplicate calls within the same method collapse, calls across different
 * methods do not.
 *
 * Overloads taking `ProducerRecord` or `Message<?>` are recognised but
 * skipped via [onWarn], because their `V` is not on the KafkaTemplate field
 * — it would require call-site argument stack tracking, which is out of
 * scope for this iteration.
 */
internal object KafkaProducerBytecodeWalker {

    private const val KAFKA_TEMPLATE_INTERNAL = "org/springframework/kafka/core/KafkaTemplate"
    private const val PRODUCER_RECORD_INTERNAL = "org/apache/kafka/clients/producer/ProducerRecord"
    private const val MESSAGE_INTERNAL = "org/springframework/messaging/Message"

    data class ProducerSite(
        val ownerClass: Class<*>,
        val enclosingMethod: String,
        val valueClass: Class<*>,
    )

    fun walk(
        clazz: Class<*>,
        templateFields: List<KafkaProducerScanner.TemplateField>,
        onWarn: (String) -> Unit = {},
    ): List<ProducerSite> {
        val fieldsForClass = templateFields.filter { it.ownerClass == clazz }
        if (fieldsForClass.isEmpty()) return emptyList()
        val fieldByName = fieldsForClass.associateBy { it.fieldName }

        val cn = readClass(clazz.classLoader ?: ClassLoader.getSystemClassLoader(), classResource(clazz.name)) ?: return emptyList()

        val seen = linkedSetOf<ProducerSite>()
        for (m in cn.methods) {
            if ((m.access and Opcodes.ACC_BRIDGE) != 0) continue
            if ((m.access and Opcodes.ACC_SYNTHETIC) != 0) continue
            var lastTemplateField: String? = null
            var i: AbstractInsnNode? = m.instructions?.first
            while (i != null) {
                when (i) {
                    is FieldInsnNode -> if (i.opcode == Opcodes.GETFIELD && i.desc == "L$KAFKA_TEMPLATE_INTERNAL;") {
                        lastTemplateField = i.name
                    }
                    is MethodInsnNode -> if (i.opcode == Opcodes.INVOKEVIRTUAL && i.owner == KAFKA_TEMPLATE_INTERNAL && i.name == "send") {
                        // Reject overloads taking ProducerRecord or Message — the V type
                        // for those lives on the argument, not the field generic.
                        if (i.desc.contains("L$PRODUCER_RECORD_INTERNAL;") || i.desc.contains("L$MESSAGE_INTERNAL;")) {
                            onWarn("kafka.producer: skipping ${clazz.name}.${m.name}: ProducerRecord/Message send overload not yet supported")
                        } else {
                            val tf = lastTemplateField?.let(fieldByName::get)
                            if (tf != null) {
                                seen += ProducerSite(clazz, m.name, tf.valueClass)
                            }
                        }
                    }
                }
                i = i.next
            }
        }
        return seen.toList()
    }

    private fun classResource(fqn: String): String = fqn.replace('.', '/') + ".class"

    private fun readClass(loader: ClassLoader, resource: String): ClassNode? {
        val stream = loader.getResourceAsStream(resource) ?: return null
        return stream.use {
            val reader = ClassReader(it)
            val node = ClassNode()
            reader.accept(node, ClassReader.SKIP_FRAMES)
            node
        }
    }
}

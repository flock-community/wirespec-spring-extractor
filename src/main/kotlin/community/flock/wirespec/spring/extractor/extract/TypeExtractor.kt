package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.model.WireType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.UUID

/**
 * Walks Java/Kotlin types and produces internal WireType references plus
 * a registered set of top-level definitions (Object/Enum/Refined).
 *
 * One TypeExtractor instance is used per scan run so the `definitions` set
 * accumulates across all controllers.
 */
open class TypeExtractor {

    private val cache = mutableMapOf<String, WireType>()
    protected val _definitions = linkedSetOf<WireType>()

    val definitions: Set<WireType> get() = _definitions

    /** Extract a [WireType] for [type]. Top-level Object/Enum/Refined definitions accumulate in [definitions]. */
    fun extract(type: Type): WireType = extractInner(type, nullable = false)

    private fun extractInner(type: Type, nullable: Boolean): WireType = when (type) {
        is Class<*>          -> fromClass(type, nullable)
        is ParameterizedType -> fromParameterized(type, nullable)
        is WildcardType      -> extractInner(type.upperBounds.firstOrNull() ?: Any::class.java, nullable)
        is TypeVariable<*>   -> WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
        else                 -> WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
    }

    private fun fromClass(cls: Class<*>, nullable: Boolean): WireType {
        primitiveOf(cls)?.let { return it.copy(nullable = nullable) }
        if (cls == String::class.java) return WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
        if (cls == ByteArray::class.java) return WireType.Primitive(WireType.Primitive.Kind.BYTES, nullable)
        if (cls == UUID::class.java) return WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
        if (Enum::class.java.isAssignableFrom(cls)) {
            cache[cls.name]?.let { return (it as WireType.Ref).copy(nullable = nullable) }
            val ref = WireType.Ref(cls.simpleName, nullable)
            cache[cls.name] = ref.copy(nullable = false)
            @Suppress("UNCHECKED_CAST")
            val values = (cls.enumConstants as Array<Enum<*>>).map { it.name }
            _definitions += WireType.EnumDef(cls.simpleName, values)
            return ref
        }
        if (Collection::class.java.isAssignableFrom(cls)) {
            return WireType.ListOf(WireType.Primitive(WireType.Primitive.Kind.STRING), nullable)
        }
        // Object class — register and recurse into its fields.
        cache[cls.name]?.let { return (it as WireType.Ref).copy(nullable = nullable) }
        val ref = WireType.Ref(cls.simpleName, nullable)
        cache[cls.name] = ref.copy(nullable = false)
        val fields = walkFields(cls)
        _definitions += WireType.Object(cls.simpleName, fields)
        return ref
    }

    private fun fromParameterized(pt: ParameterizedType, nullable: Boolean): WireType {
        val raw = pt.rawType as Class<*>
        if (Collection::class.java.isAssignableFrom(raw)) {
            val arg = pt.actualTypeArguments[0]
            val element = if (arg is WildcardType || arg is TypeVariable<*>)
                WireType.Primitive(WireType.Primitive.Kind.STRING)
            else
                extractInner(arg, nullable = false)
            return WireType.ListOf(element, nullable)
        }
        if (Map::class.java.isAssignableFrom(raw)) {
            val valueArg = pt.actualTypeArguments[1]
            val v = extractInner(valueArg, nullable = false)
            return WireType.MapOf(v, nullable)
        }
        // Generic value class — fall back to its raw type for now.
        return fromClass(raw, nullable)
    }

    /** Override-able by subclasses (Tasks 9-12) to inject Jackson/Validation/Schema processing. */
    protected open fun walkFields(cls: Class<*>): List<WireType.Field> {
        val members = propertyMembers(cls)
        return members.mapNotNull { (name, type) ->
            val element = cls.declaredFieldOrNull(name) ?: cls
            if (JacksonNames.isIgnored(element)) return@mapNotNull null
            val rawType = extractInner(type, nullable = false)
            val refined = ValidationConstraints.refine(element, rawType)
            if (refined is WireType.Refined) _definitions += refined
            WireType.Field(
                name = JacksonNames.effectiveName(element, original = name),
                type = if (refined is WireType.Refined) WireType.Ref(refined.name) else refined,
            )
        }
    }

    private fun Class<*>.declaredFieldOrNull(name: String): java.lang.reflect.Field? =
        try { getDeclaredField(name) } catch (_: NoSuchFieldException) { null }

    /**
     * Discover (name, generic-type) pairs for a class:
     * 1. Java records: record components.
     * 2. Kotlin data classes: declared fields (Kotlin generates fields for properties).
     * 3. JavaBeans: getters paired with backing fields.
     */
    protected fun propertyMembers(cls: Class<*>): List<Pair<String, Type>> {
        if (cls.isRecord) {
            return cls.recordComponents.map { it.name to it.genericType }
        }
        // Kotlin data classes / POJOs: prefer declared non-static, non-synthetic fields.
        val declared = cls.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name to it.genericType }
        if (declared.isNotEmpty()) return declared
        // Pure JavaBean: getXxx / isXxx pairs.
        return cls.methods
            .filter { it.parameterCount == 0 && (it.name.startsWith("get") || it.name.startsWith("is")) && it.declaringClass != Any::class.java }
            .filter { it.name != "getClass" }
            .map { method ->
                val raw = method.name.removePrefix("get").removePrefix("is")
                val name = raw.replaceFirstChar { it.lowercase() }
                name to method.genericReturnType
            }
    }

    private fun primitiveOf(cls: Class<*>): WireType.Primitive? = when (cls) {
        Int::class.javaPrimitiveType, Integer::class.java        -> WireType.Primitive(WireType.Primitive.Kind.INTEGER_32)
        Long::class.javaPrimitiveType, java.lang.Long::class.java -> WireType.Primitive(WireType.Primitive.Kind.INTEGER_64)
        Short::class.javaPrimitiveType, java.lang.Short::class.java -> WireType.Primitive(WireType.Primitive.Kind.INTEGER_32)
        Byte::class.javaPrimitiveType, java.lang.Byte::class.java   -> WireType.Primitive(WireType.Primitive.Kind.INTEGER_32)
        Float::class.javaPrimitiveType, java.lang.Float::class.java -> WireType.Primitive(WireType.Primitive.Kind.NUMBER_32)
        Double::class.javaPrimitiveType, java.lang.Double::class.java -> WireType.Primitive(WireType.Primitive.Kind.NUMBER_64)
        Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java -> WireType.Primitive(WireType.Primitive.Kind.BOOLEAN)
        Char::class.javaPrimitiveType, java.lang.Character::class.java -> WireType.Primitive(WireType.Primitive.Kind.STRING)
        else -> null
    }
}

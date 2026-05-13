package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.WirespecExtractorException
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
    /** Tracks which simple name has been claimed by which FQN to detect cross-package collisions. */
    private val usedNames = mutableMapOf<String, String>()  // simpleName -> identity that claimed it

    /** Stack of TypeVariable -> Type bindings, pushed when entering a parameterized type's body. */
    private val bindings = ArrayDeque<Map<TypeVariable<*>, Type>>()

    val definitions: Set<WireType> get() = _definitions

    /** Extract a [WireType] for [type]. Top-level Object/Enum/Refined definitions accumulate in [definitions]. */
    fun extract(type: Type): WireType = extractInner(type, nullable = false)

    private fun extractInner(type: Type, nullable: Boolean): WireType = when (type) {
        is Class<*>          -> fromClass(type, nullable)
        is ParameterizedType -> fromParameterized(type, nullable)
        is WildcardType      -> extractInner(type.upperBounds.firstOrNull() ?: Any::class.java, nullable)
        is TypeVariable<*>   -> resolveBinding(type)
            ?.let { extractInner(it, nullable) }
            ?: WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
        else                 -> WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
    }

    /**
     * Returns a unique name for [cls], disambiguating with a numeric suffix when two classes
     * from different packages share the same simple name (e.g. com.a.User and com.b.User → "User", "User2").
     */
    private fun nameFor(cls: Class<*>): String {
        val simple = cls.simpleName
        val existing = usedNames[simple]
        return when {
            existing == null -> { usedNames[simple] = cls.name; simple }
            existing == cls.name -> simple
            else -> {
                // collision — find or assign a numeric suffix
                var i = 2
                while (usedNames["$simple$i"] != null && usedNames["$simple$i"] != cls.name) i++
                val newName = "$simple$i"
                if (usedNames[newName] == null) usedNames[newName] = cls.name
                newName
            }
        }
    }

    /**
     * Like [nameFor] but for a composed flat name (e.g., "UserDtoPage" from Page<UserDto>).
     * The collision key is `${rawClass.name}#$composed` so a hand-written class registered
     * under the plain FQN cannot match a flattened-generic identity, and the flattened
     * generic gets a numeric suffix.
     */
    private fun nameFor(composed: String, rawClass: Class<*>): String {
        val identity = "${rawClass.name}#$composed"
        val existing = usedNames[composed]
        return when {
            existing == null -> { usedNames[composed] = identity; composed }
            existing == identity -> composed
            else -> {
                var i = 2
                while (usedNames["$composed$i"] != null && usedNames["$composed$i"] != identity) i++
                val newName = "$composed$i"
                if (usedNames[newName] == null) usedNames[newName] = identity
                newName
            }
        }
    }

    private fun fromClass(cls: Class<*>, nullable: Boolean): WireType {
        primitiveOf(cls)?.let { return it.copy(nullable = nullable) }
        if (cls == String::class.java) return WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
        if (cls == ByteArray::class.java) return WireType.Primitive(WireType.Primitive.Kind.BYTES, nullable)
        if (cls == UUID::class.java) return WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
        if (Enum::class.java.isAssignableFrom(cls)) {
            val fp = cls.name
            cache[fp]?.let { return (it as WireType.Ref).copy(nullable = nullable) }
            val name = nameFor(cls)
            val ref = WireType.Ref(name, nullable)
            cache[fp] = ref.copy(nullable = false)
            @Suppress("UNCHECKED_CAST")
            val values = (cls.enumConstants as Array<Enum<*>>).map { it.name }
            _definitions += WireType.EnumDef(name, values)
            return ref
        }
        if (Collection::class.java.isAssignableFrom(cls)) {
            return WireType.ListOf(WireType.Primitive(WireType.Primitive.Kind.STRING), nullable)
        }
        // JDK / platform value types (java.time.*, java.math.BigDecimal, java.net.URI, …):
        // walking their declared fields would leak internal implementation details
        // (e.g. ZoneOffset's `totalSeconds`) into the Wirespec schema. Jackson serializes
        // these to opaque strings, so represent them as STRING here too.
        if (isJdkOpaqueType(cls)) {
            return WireType.Primitive(WireType.Primitive.Kind.STRING, nullable)
        }
        // Object class — register and recurse into its fields.
        val fp = cls.name
        cache[fp]?.let { return (it as WireType.Ref).copy(nullable = nullable) }
        val name = nameFor(cls)
        val ref = WireType.Ref(name, nullable)
        cache[fp] = ref.copy(nullable = false)
        val fields = walkFields(cls)
        _definitions += WireType.Object(name, fields)
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
        // User-level generic — flatten.
        return flattenGeneric(pt, nullable)
    }

    /**
     * Flatten a `Page<UserDto>`-style parameterized type into a uniquely-named
     * Object definition with type-substituted fields.
     */
    private fun flattenGeneric(pt: ParameterizedType, nullable: Boolean): WireType {
        val raw = pt.rawType as Class<*>
        val fp = fingerprint(pt)

        // Same instantiation reached twice -> same ref.
        cache[fp]?.let { return (it as WireType.Ref).copy(nullable = nullable) }

        // Compose the display name from resolved args, then disambiguate.
        val composedName = flatName(pt)
        val name = nameFor(composedName, raw)

        // Insert placeholder ref BEFORE walking fields so self-referential
        // generics terminate (Tree<UserDto> -> children: List<Tree<UserDto>>).
        val ref = WireType.Ref(name, nullable)
        cache[fp] = ref.copy(nullable = false)

        // Build binding frame: typeParameter -> actualTypeArgument.
        @Suppress("UNCHECKED_CAST")
        val frame = raw.typeParameters
            .zip(pt.actualTypeArguments)
            .toMap() as Map<TypeVariable<*>, Type>
        bindings.addFirst(frame)
        try {
            val fields = walkFields(raw)
            _definitions += WireType.Object(name, fields)
        } finally {
            bindings.removeFirst()
        }
        return ref
    }

    /**
     * Stable fingerprint for cache lookup. Uses FQNs so that display-name
     * disambiguation (`UserDtoPage` vs `UserDtoPage2`) cannot confuse the cache.
     */
    private fun fingerprint(type: Type): String = when (type) {
        is Class<*> -> type.name
        is ParameterizedType -> {
            val raw = (type.rawType as Class<*>).name
            val args = type.actualTypeArguments.joinToString(",") { fingerprint(it) }
            "$raw($args)"
        }
        is WildcardType -> "?"
        is TypeVariable<*> -> resolveBinding(type)?.let { fingerprint(it) } ?: "T:${type.name}"
        else -> type.typeName
    }

    /** Walk the bindings stack outside-in to resolve [variable]. */
    private fun resolveBinding(variable: TypeVariable<*>): Type? {
        for (frame in bindings) {
            frame[variable]?.let { return it }
        }
        return null
    }

    /**
     * Display name for a type as it appears inside a flattened wrapper name.
     * Composed names get funneled through [nameFor] for collision handling.
     */
    private fun flatName(type: Type): String = when (type) {
        is Class<*> -> when {
            primitiveOf(type) != null -> type.simpleName
            type == String::class.java -> "String"
            type == ByteArray::class.java -> "ByteArray"
            type == java.util.UUID::class.java -> "UUID"
            else -> type.simpleName
        }
        is ParameterizedType -> {
            val raw = type.rawType as Class<*>
            val args = type.actualTypeArguments
            when {
                Collection::class.java.isAssignableFrom(raw) -> flatName(args[0]) + "List"
                Map::class.java.isAssignableFrom(raw)        -> flatName(args[1]) + "Map"
                else -> args.joinToString("") { flatName(it) } + raw.simpleName
            }
        }
        is TypeVariable<*> -> resolveBinding(type)
            ?.let { flatName(it) }
            ?: throw WirespecExtractorException.unboundTypeVariable(
                variable = type.name,
                inClassField = "<flatName>",
                controllerMethod = currentContext(),
            )
        is WildcardType -> throw WirespecExtractorException.wildcardArgument(
            atType = type.typeName,
            controllerMethod = currentContext(),
        )
        else -> type.typeName
    }

    /**
     * Best-effort description of the originating controller method for error
     * messages. Until controller context is threaded through this returns
     * a sentinel; the static parts of error messages still hold.
     */
    private fun currentContext(): String = "<unknown>"

    /** Override-able by subclasses (Tasks 9-12) to inject Jackson/Validation/Schema processing. */
    protected open fun walkFields(cls: Class<*>): List<WireType.Field> {
        val members = propertyMembers(cls)
        return members.mapNotNull { (name, type) ->
            val field = cls.declaredFieldOrNull(name)
            val element: java.lang.reflect.AnnotatedElement = field ?: cls
            if (JacksonNames.isIgnored(element)) return@mapNotNull null

            val declaredClass = (type as? Class<*>) ?: ((type as? java.lang.reflect.ParameterizedType)?.rawType as? Class<*>) ?: Any::class.java
            val nullable = NullabilityResolver.isNullable(element, declaredClass)

            val rawType = withNullability(extractInner(type, nullable = false), nullable)
            val refined = ValidationConstraints.refine(element, rawType)
            if (refined is WireType.Refined) _definitions += refined

            WireType.Field(
                name = JacksonNames.effectiveName(element, original = name),
                type = if (refined is WireType.Refined) WireType.Ref(refined.name, nullable) else refined,
                description = NullabilityResolver.schemaDescription(element),
            )
        }
    }

    private fun withNullability(t: WireType, nullable: Boolean): WireType = when (t) {
        is WireType.Primitive -> t.copy(nullable = nullable)
        is WireType.Ref       -> t.copy(nullable = nullable)
        is WireType.ListOf    -> t.copy(nullable = nullable)
        is WireType.MapOf     -> t.copy(nullable = nullable)
        is WireType.Object    -> t.copy(nullable = nullable)
        is WireType.EnumDef   -> t.copy(nullable = nullable)
        is WireType.Refined   -> t.copy(nullable = nullable)
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

    /**
     * Whether [cls] is a JDK / platform value type that should be treated as an
     * opaque STRING in Wirespec instead of being expanded into a nested object.
     *
     * Covers `java.time.*` (LocalDateTime, ZoneOffset, Instant, …), `java.math.*`
     * (BigDecimal, BigInteger), `java.net.URI/URL`, `java.util.Date`/`Calendar`,
     * `java.sql.*` temporal types, and anything else shipped in the `java.*` /
     * `jdk.*` / `sun.*` namespaces that we haven't already mapped above
     * (primitives, String, ByteArray, UUID, Enum, Collection).
     */
    private fun isJdkOpaqueType(cls: Class<*>): Boolean {
        val pkg = cls.`package`?.name ?: return false
        return pkg == "java.lang"
            || pkg.startsWith("java.")
            || pkg.startsWith("javax.")
            || pkg.startsWith("jakarta.")
            || pkg.startsWith("jdk.")
            || pkg.startsWith("sun.")
            || pkg.startsWith("com.sun.")
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

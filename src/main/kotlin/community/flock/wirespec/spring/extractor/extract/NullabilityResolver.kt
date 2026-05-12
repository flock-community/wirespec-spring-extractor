package community.flock.wirespec.spring.extractor.extract

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.util.Optional

object NullabilityResolver {

    /**
     * Returns true if the given member should be modelled as nullable in Wirespec.
     * Priority order:
     *  1. Java primitive type → non-null
     *  2. Kotlin nullability metadata (kotlin.Metadata + member type)
     *  3. Optional<T> → nullable
     *  4. JSR-305 / @Nullable / @NonNull annotations
     *  5. @NotNull / @NotBlank / @Schema(required=true) → non-null
     *  6. Default → nullable
     */
    fun isNullable(element: AnnotatedElement, declaredJavaType: Class<*>): Boolean {
        if (declaredJavaType.isPrimitive) return false
        if (declaredJavaType == Optional::class.java) return true
        kotlinNullable(element)?.let { return it }
        annotationDeclaredNullable(element)?.let { return it }
        if (element.isAnnotationPresent(NotNull::class.java)) return false
        if (element.isAnnotationPresent(NotBlank::class.java)) return false
        if (element.getAnnotation(Schema::class.java)?.required == true) return false
        return true
    }

    fun schemaDescription(element: AnnotatedElement): String? =
        element.getAnnotation(Schema::class.java)?.description?.takeIf { it.isNotBlank() }

    /**
     * Read Kotlin's @Metadata to determine nullability for a property field.
     * Returns null when this isn't a Kotlin class member.
     */
    private fun kotlinNullable(element: AnnotatedElement): Boolean? {
        val field = element as? Field ?: return null
        val owner = field.declaringClass
        if (!owner.isAnnotationPresent(Metadata::class.java)) return null
        val kClass = try { owner.kotlin } catch (_: Throwable) { return null }
        val prop = kClass.members.firstOrNull { it.name == field.name } ?: return null
        return prop.returnType.isMarkedNullable
    }

    private fun annotationDeclaredNullable(element: AnnotatedElement): Boolean? {
        val annotations = element.annotations.map { it.annotationClass.simpleName }
        return when {
            annotations.any { it.equals("Nullable", ignoreCase = true) } -> true
            annotations.any { it.equals("NonNull", ignoreCase = true) || it.equals("NotNull", ignoreCase = true) } -> false
            else -> null
        }
    }
}

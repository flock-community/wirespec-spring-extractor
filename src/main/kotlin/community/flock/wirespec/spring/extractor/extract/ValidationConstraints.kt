package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.model.WireType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.lang.reflect.AnnotatedElement
import java.util.UUID

object ValidationConstraints {

    /**
     * If [element] carries Bean Validation constraints that refine [base],
     * return a [WireType.Refined] capturing them; otherwise return [base].
     * Refined types get a synthetic name unique to this refinement.
     */
    fun refine(element: AnnotatedElement, base: WireType): WireType {
        if (base !is WireType.Primitive) return base

        val pattern = element.getAnnotation(Pattern::class.java)?.regexp
        val size = element.getAnnotation(Size::class.java)
        val min = element.getAnnotation(Min::class.java)?.value?.toString()
        val max = element.getAnnotation(Max::class.java)?.value?.toString()

        val hasAny = pattern != null || size != null || min != null || max != null
        if (!hasAny) return base

        return WireType.Refined(
            name = "Refined" + UUID.randomUUID().toString().take(8).uppercase(),
            base = base.copy(nullable = false),
            regex = pattern,
            min = min ?: size?.min?.toString(),
            max = max ?: size?.max?.toString()?.takeIf { size.max != Int.MAX_VALUE },
            nullable = base.nullable,
        )
    }

    fun isRequired(element: AnnotatedElement): Boolean =
        element.isAnnotationPresent(NotNull::class.java) ||
        element.isAnnotationPresent(NotBlank::class.java)
}

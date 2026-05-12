// src/main/kotlin/community/flock/wirespec/spring/extractor/extract/ParamExtractor.kt
package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.model.Param
import community.flock.wirespec.spring.extractor.model.Param.Source
import community.flock.wirespec.spring.extractor.model.WireType
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import java.lang.reflect.Method
import java.lang.reflect.Parameter

object ParamExtractor {

    /** Extract all non-body parameters of [method]. */
    fun extractParams(method: Method): List<Param> =
        method.parameters.mapNotNull(::toParam)

    /** Find the first @RequestBody parameter, if any. */
    fun extractRequestBodyParameter(method: Method): Parameter? =
        method.parameters.firstOrNull { it.isAnnotationPresent(RequestBody::class.java) }

    private fun toParam(p: Parameter): Param? {
        p.getAnnotation(PathVariable::class.java)?.let { a ->
            return Param(name = a.value.ifEmpty { p.name }, source = Source.PATH, type = stringPlaceholder())
        }
        p.getAnnotation(RequestParam::class.java)?.let { a ->
            return Param(name = a.value.ifEmpty { p.name }, source = Source.QUERY, type = stringPlaceholder())
        }
        p.getAnnotation(RequestHeader::class.java)?.let { a ->
            return Param(name = a.value.ifEmpty { p.name }, source = Source.HEADER, type = stringPlaceholder())
        }
        p.getAnnotation(CookieValue::class.java)?.let { a ->
            return Param(name = a.value.ifEmpty { p.name }, source = Source.COOKIE, type = stringPlaceholder())
        }
        return null  // unannotated parameters and @RequestBody are skipped here
    }

    /**
     * Placeholder type. Task 8 (TypeExtractor) replaces this with real type
     * resolution by passing in a TypeExtractor instance.
     */
    private fun stringPlaceholder() = WireType.Primitive(WireType.Primitive.Kind.STRING)
}

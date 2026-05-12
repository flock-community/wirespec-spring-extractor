// src/main/kotlin/community/flock/wirespec/spring/extractor/extract/ParamExtractor.kt
package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.model.Param
import community.flock.wirespec.spring.extractor.model.Param.Source
import community.flock.wirespec.spring.extractor.model.WireType
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import java.lang.reflect.Method
import java.lang.reflect.Parameter

class ParamExtractor(private val types: TypeExtractor) {

    fun extractParams(method: Method): List<Param> = method.parameters.mapNotNull(::toParam)

    fun extractRequestBody(method: Method): WireType? {
        val p = method.parameters.firstOrNull {
            AnnotatedElementUtils.isAnnotated(it, RequestBody::class.java.name)
        } ?: return null
        return types.extract(p.parameterizedType)
    }

    private fun toParam(p: Parameter): Param? {
        // Only extract the parameter's type once we've confirmed it's actually a Spring
        // binding parameter — otherwise we'd pollute TypeExtractor.definitions with
        // synthetic / framework parameters (notably Kotlin's `Continuation<? super T>`,
        // which would otherwise leak Continuation and CoroutineContext into the schema).
        AnnotatedElementUtils.findMergedAnnotation(p, PathVariable::class.java)?.let { a ->
            return Param(name = a.value.ifEmpty { a.name }.ifEmpty { p.name }, source = Source.PATH, type = types.extract(p.parameterizedType))
        }
        AnnotatedElementUtils.findMergedAnnotation(p, RequestParam::class.java)?.let { a ->
            return Param(name = a.value.ifEmpty { a.name }.ifEmpty { p.name }, source = Source.QUERY, type = types.extract(p.parameterizedType))
        }
        AnnotatedElementUtils.findMergedAnnotation(p, RequestHeader::class.java)?.let { a ->
            return Param(name = a.value.ifEmpty { a.name }.ifEmpty { p.name }, source = Source.HEADER, type = types.extract(p.parameterizedType))
        }
        AnnotatedElementUtils.findMergedAnnotation(p, CookieValue::class.java)?.let { a ->
            return Param(name = a.value.ifEmpty { a.name }.ifEmpty { p.name }, source = Source.COOKIE, type = types.extract(p.parameterizedType))
        }
        return null
    }
}

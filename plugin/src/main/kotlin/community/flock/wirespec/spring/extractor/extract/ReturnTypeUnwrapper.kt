// src/main/kotlin/community/flock/wirespec/spring/extractor/extract/ReturnTypeUnwrapper.kt
package community.flock.wirespec.spring.extractor.extract

import org.springframework.web.bind.annotation.ResponseStatus
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

object ReturnTypeUnwrapper {

    private val WRAPPERS = setOf(
        "org.springframework.http.ResponseEntity",
        "java.util.Optional",
        "reactor.core.publisher.Mono",
        "java.util.concurrent.Callable",
        "org.springframework.web.context.request.async.DeferredResult",
    )
    private const val FLUX = "reactor.core.publisher.Flux"

    /** A flattened view of a method's effective response payload. */
    data class Unwrapped(val type: Type, val isList: Boolean, val isVoid: Boolean)

    fun unwrap(returnType: Type): Unwrapped {
        var current = returnType
        var isList = false

        while (true) {
            val rawName = (current as? ParameterizedType)?.rawType?.typeName
                ?: (current as? Class<*>)?.name

            if (rawName == FLUX && current is ParameterizedType) {
                isList = true
                current = current.actualTypeArguments[0]
                continue
            }
            if (rawName in WRAPPERS && current is ParameterizedType) {
                current = current.actualTypeArguments[0]
                continue
            }
            break
        }

        val isVoid = when (current) {
            is Class<*> -> current == Void.TYPE || current == Void::class.java
            else        -> current.typeName == "java.lang.Void"
        }

        return Unwrapped(current, isList = isList, isVoid = isVoid)
    }

    fun statusCodeFor(method: Method, unwrapped: Unwrapped): Int {
        val rs = method.getAnnotation(ResponseStatus::class.java)
        if (rs != null) {
            val codeAttr = rs.code
            val valueAttr = rs.value
            // Spring treats the two as aliases; pick whichever isn't the default.
            val code = if (codeAttr.value() != 500) codeAttr else valueAttr
            return code.value()
        }
        return if (unwrapped.isVoid) 204 else 200
    }
}

// src/main/kotlin/community/flock/wirespec/spring/extractor/extract/EndpointExtractor.kt
package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.model.Endpoint
import community.flock.wirespec.spring.extractor.model.Endpoint.HttpMethod
import community.flock.wirespec.spring.extractor.model.Endpoint.PathSegment
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import java.lang.reflect.Method

object EndpointExtractor {

    /**
     * Extract all Wirespec endpoints from one Spring controller class.
     * Parameter, body and return-type extraction are stubs in this task —
     * they're filled in by Tasks 6 and 7.
     */
    fun extract(controllerClass: Class<*>): List<Endpoint> {
        val classMapping = AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping::class.java)
        val classPaths = classMapping?.path?.toList()?.takeIf { it.isNotEmpty() } ?: listOf("")

        return controllerClass.methods.flatMap { method ->
            extractFromMethod(controllerClass, classPaths, method)
        }
    }

    private fun extractFromMethod(
        controllerClass: Class<*>,
        classPaths: List<String>,
        method: Method,
    ): List<Endpoint> {
        val mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)
            ?: return emptyList()

        val methodPaths = mapping.path.toList().takeIf { it.isNotEmpty() } ?: listOf("")
        val httpMethods = if (mapping.method.isEmpty()) listOf(RequestMethod.GET) else mapping.method.toList()

        val allParams = ParamExtractor.extractParams(method)
        val bodyParam = ParamExtractor.extractRequestBodyParameter(method)

        return httpMethods.flatMap { rm ->
            classPaths.flatMap { cp ->
                methodPaths.map { mp ->
                    Endpoint(
                        controllerSimpleName = controllerClass.simpleName,
                        name = pascalCase(method.name),
                        method = rm.toHttpMethod(),
                        pathSegments = parsePath(joinPath(cp, mp)),
                        queryParams = allParams.filter { it.source == community.flock.wirespec.spring.extractor.model.Param.Source.QUERY },
                        headerParams = allParams.filter { it.source == community.flock.wirespec.spring.extractor.model.Param.Source.HEADER },
                        cookieParams = allParams.filter { it.source == community.flock.wirespec.spring.extractor.model.Param.Source.COOKIE },
                        requestBody = bodyParam?.let { _ ->
                            // Real type resolution comes in Task 8.
                            community.flock.wirespec.spring.extractor.model.WireType.Ref("Unknown")
                        },
                        responseBody = null,          // Task 7
                        statusCode = 200,             // Task 7
                    )
                }
            }
        }
    }

    private fun joinPath(a: String, b: String): String {
        val left = a.trim('/').takeIf { it.isNotBlank() }
        val right = b.trim('/').takeIf { it.isNotBlank() }
        return listOfNotNull(left, right).joinToString("/")
    }

    internal fun parsePath(path: String): List<PathSegment> =
        path.split('/').filter { it.isNotBlank() }.map { seg ->
            val match = Regex("""^\{([^:}]+)(?::[^}]+)?}$""").matchEntire(seg)
            if (match != null) {
                PathSegment.Variable(
                    name = match.groupValues[1],
                    type = community.flock.wirespec.spring.extractor.model.WireType.Primitive(
                        community.flock.wirespec.spring.extractor.model.WireType.Primitive.Kind.STRING,
                    ),
                )
            } else {
                PathSegment.Literal(seg)
            }
        }

    internal fun pascalCase(name: String): String =
        if (name.isEmpty()) name else name[0].uppercaseChar() + name.substring(1)

    private fun RequestMethod.toHttpMethod(): HttpMethod = when (this) {
        RequestMethod.GET -> HttpMethod.GET
        RequestMethod.POST -> HttpMethod.POST
        RequestMethod.PUT -> HttpMethod.PUT
        RequestMethod.PATCH -> HttpMethod.PATCH
        RequestMethod.DELETE -> HttpMethod.DELETE
        RequestMethod.OPTIONS -> HttpMethod.OPTIONS
        RequestMethod.HEAD -> HttpMethod.HEAD
        RequestMethod.TRACE -> HttpMethod.TRACE
    }
}

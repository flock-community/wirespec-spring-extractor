// src/main/kotlin/community/flock/wirespec/spring/extractor/model/Endpoint.kt
package community.flock.wirespec.spring.extractor.model

data class Endpoint(
    val controllerSimpleName: String,
    val name: String,                     // Wirespec definition name (PascalCase)
    val method: HttpMethod,
    val pathSegments: List<PathSegment>,
    val queryParams: List<Param>,
    val headerParams: List<Param>,
    val cookieParams: List<Param>,
    val requestBody: WireType? = null,
    val responseBody: WireType? = null,
    val statusCode: Int = 200,
) {
    enum class HttpMethod { GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD, TRACE }

    sealed interface PathSegment {
        data class Literal(val value: String) : PathSegment
        data class Variable(val name: String, val type: WireType) : PathSegment
    }
}

// src/test/kotlin/community/flock/wirespec/spring/extractor/extract/EndpointExtractorTest.kt
package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.HelloController
import community.flock.wirespec.spring.extractor.fixtures.InheritingController
import community.flock.wirespec.spring.extractor.fixtures.MultiMappingController
import community.flock.wirespec.spring.extractor.fixtures.ParamsController
import community.flock.wirespec.spring.extractor.model.Endpoint.HttpMethod
import community.flock.wirespec.spring.extractor.model.Endpoint.PathSegment
import community.flock.wirespec.spring.extractor.model.WireType
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EndpointExtractorTest {

    @Test
    fun `combines class-level and method-level paths`() {
        val endpoints = EndpointExtractor(TypeExtractor()).extract(HelloController::class.java)

        endpoints shouldHaveSize 1
        val ep = endpoints.single()
        ep.method shouldBe HttpMethod.GET
        ep.pathSegments shouldBe listOf(PathSegment.Literal("hello"))
        ep.controllerSimpleName shouldBe "HelloController"
    }

    @Test
    fun `multi-method mapping produces one endpoint per method`() {
        val methods = EndpointExtractor(TypeExtractor()).extract(MultiMappingController::class.java).map { it.method }

        methods shouldContain HttpMethod.GET
        methods shouldContain HttpMethod.HEAD
        methods shouldHaveSize 2
    }

    @Test
    fun `honors inherited @RequestMapping from a superclass`() {
        val endpoints = EndpointExtractor(TypeExtractor()).extract(InheritingController::class.java)

        endpoints shouldHaveSize 1
        endpoints.single().pathSegments shouldBe listOf(
            PathSegment.Literal("parent"),
            PathSegment.Literal("child"),
        )
    }

    @Test
    fun `endpoint name is PascalCase of method name`() {
        val ep = EndpointExtractor(TypeExtractor()).extract(HelloController::class.java).single()
        ep.name shouldBe "Hello"
    }

    @Test
    fun `params and body propagate from ParamExtractor`() {
        val ep = EndpointExtractor(TypeExtractor())
            .extract(ParamsController::class.java)
            .single { it.name == "PostItem" }
        ep.requestBody shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }
}

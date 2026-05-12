// src/test/kotlin/community/flock/wirespec/spring/extractor/extract/ParamExtractorTest.kt
package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.ParamsController
import community.flock.wirespec.spring.extractor.model.Param.Source
import community.flock.wirespec.spring.extractor.model.WireType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ParamExtractorTest {

    private val pe = ParamExtractor(TypeExtractor())

    private val getItem = ParamsController::class.java.getDeclaredMethod(
        "getItem", String::class.java, String::class.java, Integer::class.java,
        String::class.java, String::class.java,
    )

    private val postItem = ParamsController::class.java.getDeclaredMethod("postItem", String::class.java)

    @Test
    fun `path variables are PATH params named after the variable`() {
        val params = pe.extractParams(getItem)
        val pathParams = params.filter { it.source == Source.PATH }
        pathParams shouldHaveSize 1
        pathParams.single().name shouldBe "id"
    }

    @Test
    fun `request params are QUERY params`() {
        val params = pe.extractParams(getItem)
        params.filter { it.source == Source.QUERY }.map { it.name } shouldBe listOf("q", "page")
    }

    @Test
    fun `request headers are HEADER params named by their value`() {
        val params = pe.extractParams(getItem)
        val headers = params.filter { it.source == Source.HEADER }
        headers shouldHaveSize 1
        headers.single().name shouldBe "X-Trace"
    }

    @Test
    fun `cookie values are COOKIE params named by their value`() {
        val params = pe.extractParams(getItem)
        val cookies = params.filter { it.source == Source.COOKIE }
        cookies shouldHaveSize 1
        cookies.single().name shouldBe "session"
    }

    @Test
    fun `request body is extracted as a WireType`() {
        pe.extractRequestBody(postItem) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
        pe.extractParams(postItem) shouldBe emptyList()
    }

    @Test
    fun `getItem has no @RequestBody parameter`() {
        pe.extractRequestBody(getItem) shouldBe null
    }

    @Test
    fun `@RequestParam name attribute is honored as the param name`() {
        val named = ParamsController::class.java.getDeclaredMethod("named", String::class.java)
        val params = pe.extractParams(named)
        params.single().name shouldBe "explicitName"
    }
}

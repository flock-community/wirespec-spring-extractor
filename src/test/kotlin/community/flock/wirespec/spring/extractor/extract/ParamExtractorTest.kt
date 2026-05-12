// src/test/kotlin/community/flock/wirespec/spring/extractor/extract/ParamExtractorTest.kt
package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.ParamsController
import community.flock.wirespec.spring.extractor.model.Param.Source
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ParamExtractorTest {

    private val getItem = ParamsController::class.java.getDeclaredMethod(
        "getItem", String::class.java, String::class.java, Integer::class.java,
        String::class.java, String::class.java,
    )

    private val postItem = ParamsController::class.java.getDeclaredMethod("postItem", String::class.java)

    @Test
    fun `path variables are PATH params named after the variable`() {
        val params = ParamExtractor.extractParams(getItem)
        val pathParams = params.filter { it.source == Source.PATH }
        pathParams shouldHaveSize 1
        pathParams.single().name shouldBe "id"
    }

    @Test
    fun `request params are QUERY params`() {
        val params = ParamExtractor.extractParams(getItem)
        params.filter { it.source == Source.QUERY }.map { it.name } shouldBe listOf("q", "page")
    }

    @Test
    fun `request headers are HEADER params named by their value`() {
        val params = ParamExtractor.extractParams(getItem)
        val headers = params.filter { it.source == Source.HEADER }
        headers shouldHaveSize 1
        headers.single().name shouldBe "X-Trace"
    }

    @Test
    fun `cookie values are COOKIE params named by their value`() {
        val params = ParamExtractor.extractParams(getItem)
        val cookies = params.filter { it.source == Source.COOKIE }
        cookies shouldHaveSize 1
        cookies.single().name shouldBe "session"
    }

    @Test
    fun `request body parameter is detected and is not a Param`() {
        ParamExtractor.extractRequestBodyParameter(postItem) shouldBe postItem.parameters.first()
        ParamExtractor.extractParams(postItem) shouldBe emptyList()
    }

    @Test
    fun `getItem has no @RequestBody parameter`() {
        ParamExtractor.extractRequestBodyParameter(getItem) shouldBe null
    }
}

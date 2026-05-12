// src/test/kotlin/community/flock/wirespec/spring/extractor/extract/ParamExtractorTest.kt
package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.ParamsController
import community.flock.wirespec.spring.extractor.fixtures.SuspendController
import community.flock.wirespec.spring.extractor.model.Param.Source
import community.flock.wirespec.spring.extractor.model.WireType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
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

    @Test
    fun `suspend functions do not leak Continuation or CoroutineContext into definitions`() {
        val types = TypeExtractor()
        val pe = ParamExtractor(types)
        // A suspend function's compiled signature has a trailing Continuation<? super Item>
        // parameter. ParamExtractor walks every parameter, but it must only walk the TYPE
        // of parameters that will actually become a Spring Param — otherwise it pollutes
        // TypeExtractor's definition set with Kotlin coroutine internals.
        SuspendController::class.java.declaredMethods.forEach { pe.extractParams(it) }
        val defNames = types.definitions.map { definitionName(it) }
        defNames shouldNotContain "Continuation"
        defNames shouldNotContain "CoroutineContext"
    }

    @Test
    fun `unannotated parameter types are not registered as definitions`() {
        // Same root-cause check, narrower: any parameter without a Spring binding
        // annotation must not have its type walked.
        val types = TypeExtractor()
        val pe = ParamExtractor(types)
        val m = SuspendController::class.java.declaredMethods.first { it.name == "createUser" }
        pe.extractParams(m)  // walks all params; only @RequestBody is annotated, but extractParams
                             // is for query/path/header/cookie — none of which match here.
        // The Item @RequestBody type should NOT be registered by extractParams
        // (only extractRequestBody touches it).
        val defNames = types.definitions.map { definitionName(it) }
        defNames shouldNotContain "Item"
        defNames shouldNotContain "Continuation"
    }

    private fun definitionName(w: WireType): String? = when (w) {
        is WireType.Object  -> w.name
        is WireType.EnumDef -> w.name
        is WireType.Refined -> w.name
        else                -> null
    }
}

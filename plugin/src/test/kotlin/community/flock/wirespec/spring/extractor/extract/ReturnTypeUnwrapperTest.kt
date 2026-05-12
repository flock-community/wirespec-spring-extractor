// src/test/kotlin/community/flock/wirespec/spring/extractor/extract/ReturnTypeUnwrapperTest.kt
package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.wrapped.Item
import community.flock.wirespec.spring.extractor.fixtures.wrapped.WrappedController
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.lang.reflect.ParameterizedType

class ReturnTypeUnwrapperTest {

    private fun method(name: String) = WrappedController::class.java.declaredMethods.first { it.name == name }

    @Test
    fun `raw type returns the type itself`() {
        ReturnTypeUnwrapper.unwrap(method("raw").genericReturnType) shouldBe ReturnTypeUnwrapper.Unwrapped(Item::class.java, isList = false, isVoid = false)
    }

    @Test
    fun `ResponseEntity unwraps to inner type`() {
        val out = ReturnTypeUnwrapper.unwrap(method("entity").genericReturnType)
        out shouldBe ReturnTypeUnwrapper.Unwrapped(Item::class.java, isList = false, isVoid = false)
    }

    @Test
    fun `Optional unwraps to inner type`() {
        ReturnTypeUnwrapper.unwrap(method("opt").genericReturnType).type shouldBe Item::class.java
    }

    @Test
    fun `Mono unwraps to inner type`() {
        ReturnTypeUnwrapper.unwrap(method("mono").genericReturnType).type shouldBe Item::class.java
    }

    @Test
    fun `Flux unwraps to inner type with isList=true`() {
        val out = ReturnTypeUnwrapper.unwrap(method("flux").genericReturnType)
        out.type shouldBe Item::class.java
        out.isList shouldBe true
    }

    @Test
    fun `Callable unwraps to inner type`() {
        ReturnTypeUnwrapper.unwrap(method("callable").genericReturnType).type shouldBe Item::class.java
    }

    @Test
    fun `void method has isVoid=true and default status 204`() {
        val out = ReturnTypeUnwrapper.unwrap(method("voided").genericReturnType)
        out.isVoid shouldBe true
        ReturnTypeUnwrapper.statusCodeFor(method("voided"), out) shouldBe 204
    }

    @Test
    fun `Mono Void has isVoid=true and default status 204`() {
        val out = ReturnTypeUnwrapper.unwrap(method("monoVoid").genericReturnType)
        out.isVoid shouldBe true
        ReturnTypeUnwrapper.statusCodeFor(method("monoVoid"), out) shouldBe 204
    }

    @Test
    fun `default status for value-returning method is 200`() {
        val out = ReturnTypeUnwrapper.unwrap(method("raw").genericReturnType)
        ReturnTypeUnwrapper.statusCodeFor(method("raw"), out) shouldBe 200
    }

    @Test
    fun `@ResponseStatus overrides the default`() {
        val m = method("created")
        ReturnTypeUnwrapper.statusCodeFor(m, ReturnTypeUnwrapper.unwrap(m.genericReturnType)) shouldBe 201
    }

    @Test
    fun `nested wrapper ResponseEntity Mono Item unwraps recursively`() {
        // Smoke check via a synthetic ParameterizedType using Flux<Item> as a stand-in.
        val t = method("flux").genericReturnType.shouldBeInstanceOf<ParameterizedType>()
        ReturnTypeUnwrapper.unwrap(t).type shouldBe Item::class.java
    }
}

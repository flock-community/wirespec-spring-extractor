package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.dto.ValidatedDto
import community.flock.wirespec.spring.extractor.model.WireType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class ValidationConstraintsTest {

    @Test
    fun `@Pattern produces a refined String type`() {
        val f = ValidatedDto::class.java.getDeclaredField("code")
        val refined = ValidationConstraints.refine(f, base = WireType.Primitive(WireType.Primitive.Kind.STRING))
        refined.shouldBeInstanceOf<WireType.Refined>().regex shouldBe "^[A-Z]{3}$"
    }

    @Test
    fun `@Size produces refined String with min and max`() {
        val f = ValidatedDto::class.java.getDeclaredField("name")
        val refined = ValidationConstraints.refine(f, base = WireType.Primitive(WireType.Primitive.Kind.STRING))
        refined.shouldBeInstanceOf<WireType.Refined>()
        refined.min shouldBe "1"
        refined.max shouldBe "10"
    }

    @Test
    fun `@Min and @Max on Integer produce refined Integer with bounds`() {
        val f = ValidatedDto::class.java.getDeclaredField("age")
        val refined = ValidationConstraints.refine(f, base = WireType.Primitive(WireType.Primitive.Kind.INTEGER_32))
        refined.shouldBeInstanceOf<WireType.Refined>()
        refined.min shouldBe "0"
        refined.max shouldBe "120"
    }

    @Test
    fun `Unconstrained field returns the base type unchanged`() {
        val f = community.flock.wirespec.spring.extractor.fixtures.dto.UserDto::class.java.getDeclaredField("id")
        val out = ValidationConstraints.refine(f, base = WireType.Primitive(WireType.Primitive.Kind.STRING))
        out shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }
}

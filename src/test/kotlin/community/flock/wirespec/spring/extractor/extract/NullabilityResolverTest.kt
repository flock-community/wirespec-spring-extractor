package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.dto.SchemaDto
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NullabilityResolverTest {

    @Test
    fun `Java primitive int is non-null`() {
        val f = SchemaDto::class.java.getDeclaredField("notNullablePrimitive")
        NullabilityResolver.isNullable(f, declaredJavaType = Int::class.javaPrimitiveType!!) shouldBe false
    }

    @Test
    fun `Optional field is nullable`() {
        val f = SchemaDto::class.java.getDeclaredField("maybe")
        NullabilityResolver.isNullable(f, declaredJavaType = f.type) shouldBe true
    }

    @Test
    fun `Kotlin nullable property is nullable`() {
        val f = SchemaDto::class.java.getDeclaredField("nullable")
        NullabilityResolver.isNullable(f, declaredJavaType = f.type) shouldBe true
    }

    @Test
    fun `@NotNull or @Schema(required=true) flips to non-null`() {
        val f = SchemaDto::class.java.getDeclaredField("name")
        NullabilityResolver.isNullable(f, declaredJavaType = f.type) shouldBe false
    }

    @Test
    fun `Schema description is exposed`() {
        val f = SchemaDto::class.java.getDeclaredField("name")
        NullabilityResolver.schemaDescription(f) shouldBe "The user's display name"
    }
}

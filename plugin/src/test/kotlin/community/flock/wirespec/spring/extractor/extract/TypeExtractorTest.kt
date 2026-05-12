package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.dto.Container
import community.flock.wirespec.spring.extractor.fixtures.dto.Role
import community.flock.wirespec.spring.extractor.fixtures.dto.UserDto
import community.flock.wirespec.spring.extractor.model.WireType
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class TypeExtractorTest {

    private val extractor = TypeExtractor()

    @Test
    fun `String maps to STRING primitive`() {
        extractor.extract(String::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `Int maps to INTEGER_32 primitive`() {
        extractor.extract(Int::class.javaPrimitiveType!!) shouldBe WireType.Primitive(WireType.Primitive.Kind.INTEGER_32)
    }

    @Test
    fun `Long maps to INTEGER_64 primitive`() {
        extractor.extract(Long::class.javaPrimitiveType!!) shouldBe WireType.Primitive(WireType.Primitive.Kind.INTEGER_64)
    }

    @Test
    fun `Boolean maps to BOOLEAN primitive`() {
        extractor.extract(Boolean::class.javaPrimitiveType!!) shouldBe WireType.Primitive(WireType.Primitive.Kind.BOOLEAN)
    }

    @Test
    fun `enum becomes Ref to its simple name and is registered as EnumDef`() {
        val ref = extractor.extract(Role::class.java)
        ref.shouldBeInstanceOf<WireType.Ref>().name shouldBe "Role"

        val def = extractor.definitions.single { (it as? WireType.EnumDef)?.name == "Role" } as WireType.EnumDef
        def.values shouldBe listOf("ADMIN", "MEMBER")
    }

    @Test
    fun `class becomes Ref to its simple name and is registered as Object`() {
        val ref = extractor.extract(UserDto::class.java)
        ref.shouldBeInstanceOf<WireType.Ref>().name shouldBe "UserDto"

        val obj = extractor.definitions.single { (it as? WireType.Object)?.name == "UserDto" } as WireType.Object
        obj.fields.map { it.name } shouldBe listOf("id", "age", "active", "role", "tags")
        obj.fields.first { it.name == "tags" }.type.shouldBeInstanceOf<WireType.ListOf>()
    }

    @Test
    fun `parameterized List of String becomes ListOf STRING primitive`() {
        val type = UserDto::class.java.getDeclaredField("tags").genericType
        val out = extractor.extract(type)
        out.shouldBeInstanceOf<WireType.ListOf>().element shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `unbound generic ParameterizedType resolves to ListOf STRING with warning`() {
        val type = Container::class.java.getDeclaredField("items").genericType
        val out = extractor.extract(type)
        out.shouldBeInstanceOf<WireType.ListOf>().element shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `recursive structure does not stack overflow (cycle cache)`() {
        // Self-referential class via reflection stub.
        val ref = extractor.extract(SelfRef::class.java)
        ref.shouldBeInstanceOf<WireType.Ref>().name shouldBe "SelfRef"
    }

    data class SelfRef(val next: SelfRef?)

    @Test
    fun `walkFields honors @JsonProperty and @JsonIgnore`() {
        extractor.extract(community.flock.wirespec.spring.extractor.fixtures.dto.JacksonDto::class.java)
        val obj = extractor.definitions.single { (it as? WireType.Object)?.name == "JacksonDto" } as WireType.Object
        obj.fields.map { it.name } shouldBe listOf("user_id", "visible")
    }

    @Test
    fun `two classes with same simple name across packages get disambiguated names`() {
        val freshExtractor = TypeExtractor()
        val r1 = freshExtractor.extract(community.flock.wirespec.spring.extractor.fixtures.dto.clashA.Conflict::class.java)
        val r2 = freshExtractor.extract(community.flock.wirespec.spring.extractor.fixtures.dto.clashB.Conflict::class.java)
        (r1 as WireType.Ref).name shouldBe "Conflict"
        (r2 as WireType.Ref).name shouldBe "Conflict2"
        val names = freshExtractor.definitions.filterIsInstance<WireType.Object>().map { it.name }
        names shouldContainAll listOf("Conflict", "Conflict2")
    }

    @Test
    fun `SchemaDto fields combine nullability, validation, and description`() {
        extractor.extract(community.flock.wirespec.spring.extractor.fixtures.dto.SchemaDto::class.java)
        val obj = extractor.definitions.single { (it as? WireType.Object)?.name == "SchemaDto" } as WireType.Object
        val byName = obj.fields.associateBy { it.name }
        byName["name"]!!.description shouldBe "The user's display name"
        byName["name"]!!.type.nullable shouldBe false
        byName["nullable"]!!.type.nullable shouldBe true
        byName["notNullablePrimitive"]!!.type.nullable shouldBe false
    }
}

package community.flock.wirespec.spring.extractor.extract

import community.flock.wirespec.spring.extractor.fixtures.dto.Container
import community.flock.wirespec.spring.extractor.fixtures.dto.Role
import community.flock.wirespec.spring.extractor.fixtures.dto.TemporalDto
import community.flock.wirespec.spring.extractor.fixtures.dto.UserDto
import community.flock.wirespec.spring.extractor.model.WireType
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date

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
    fun `LocalDateTime maps to STRING primitive and is not registered as a definition`() {
        extractor.extract(LocalDateTime::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
        extractor.definitions.map { definitionName(it) } shouldNotContain "LocalDateTime"
    }

    @Test
    fun `LocalDate maps to STRING primitive`() {
        extractor.extract(LocalDate::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `LocalTime maps to STRING primitive`() {
        extractor.extract(LocalTime::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `Instant maps to STRING primitive`() {
        extractor.extract(Instant::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `ZoneOffset maps to STRING primitive and is not registered as a definition`() {
        extractor.extract(ZoneOffset::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
        extractor.definitions.map { definitionName(it) } shouldNotContain "ZoneOffset"
    }

    @Test
    fun `ZoneId maps to STRING primitive`() {
        extractor.extract(ZoneId::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `ZonedDateTime maps to STRING primitive`() {
        extractor.extract(ZonedDateTime::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `OffsetDateTime maps to STRING primitive`() {
        extractor.extract(OffsetDateTime::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `Duration and Period map to STRING primitive`() {
        extractor.extract(Duration::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
        extractor.extract(Period::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `BigDecimal and BigInteger map to STRING primitive`() {
        extractor.extract(BigDecimal::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
        extractor.extract(BigInteger::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `URI and util Date map to STRING primitive`() {
        extractor.extract(URI::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
        extractor.extract(Date::class.java) shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
    }

    @Test
    fun `DTO with JDK temporal fields produces STRING fields and no nested JDK definitions`() {
        val ref = extractor.extract(TemporalDto::class.java)
        ref.shouldBeInstanceOf<WireType.Ref>().name shouldBe "TemporalDto"

        val obj = extractor.definitions.single { (it as? WireType.Object)?.name == "TemporalDto" } as WireType.Object
        val byName = obj.fields.associateBy { it.name }
        byName["createdAt"]!!.type.shouldBeInstanceOf<WireType.Primitive>().kind shouldBe WireType.Primitive.Kind.STRING
        byName["birthDate"]!!.type.shouldBeInstanceOf<WireType.Primitive>().kind shouldBe WireType.Primitive.Kind.STRING
        byName["occurredAt"]!!.type.shouldBeInstanceOf<WireType.Primitive>().kind shouldBe WireType.Primitive.Kind.STRING
        byName["timezone"]!!.type.shouldBeInstanceOf<WireType.Primitive>().kind shouldBe WireType.Primitive.Kind.STRING
        byName["zoned"]!!.type.shouldBeInstanceOf<WireType.Primitive>().kind shouldBe WireType.Primitive.Kind.STRING
        byName["price"]!!.type.shouldBeInstanceOf<WireType.Primitive>().kind shouldBe WireType.Primitive.Kind.STRING

        val defNames = extractor.definitions.map { definitionName(it) }.toSet()
        defNames shouldNotContain "LocalDateTime"
        defNames shouldNotContain "LocalDate"
        defNames shouldNotContain "Instant"
        defNames shouldNotContain "ZoneOffset"
        defNames shouldNotContain "ZonedDateTime"
        defNames shouldNotContain "BigDecimal"
    }

    @Test
    fun `List of LocalDateTime becomes ListOf STRING with no JDK definitions`() {
        val type = TemporalDtoListHolder::class.java.getDeclaredField("timestamps").genericType
        val out = extractor.extract(type)
        val list = out.shouldBeInstanceOf<WireType.ListOf>()
        list.element shouldBe WireType.Primitive(WireType.Primitive.Kind.STRING)
        extractor.definitions.map { definitionName(it) } shouldNotContain "LocalDateTime"
    }

    @Suppress("unused")
    data class TemporalDtoListHolder(val timestamps: List<LocalDateTime>)

    private fun definitionName(w: WireType): String? = when (w) {
        is WireType.Object  -> w.name
        is WireType.EnumDef -> w.name
        is WireType.Refined -> w.name
        else                -> null
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

    @Test
    fun `Page of UserDto flattens to UserDtoPage with substituted fields`() {
        val type = community.flock.wirespec.spring.extractor.fixtures.generic.Holders::class.java
            .getDeclaredField("userDtoPage").genericType

        val ref = extractor.extract(type)
        ref.shouldBeInstanceOf<WireType.Ref>().name shouldBe "UserDtoPage"

        val obj = extractor.definitions.single { (it as? WireType.Object)?.name == "UserDtoPage" } as WireType.Object
        val byName = obj.fields.associateBy { it.name }

        byName["content"]!!.type.shouldBeInstanceOf<WireType.Ref>().name shouldBe "UserDto"
        byName["totalElements"]!!.type shouldBe WireType.Primitive(WireType.Primitive.Kind.INTEGER_64)
        byName["number"]!!.type shouldBe WireType.Primitive(WireType.Primitive.Kind.INTEGER_32)

        // The raw generic class is NOT registered.
        val defNames = extractor.definitions.map { definitionName(it) }.toSet()
        defNames shouldNotContain "Page"
    }
}

package community.flock.wirespec.spring.extractor.ownership

import community.flock.wirespec.compiler.core.parse.ast.DefinitionIdentifier
import community.flock.wirespec.compiler.core.parse.ast.Endpoint as WsEndpoint
import community.flock.wirespec.compiler.core.parse.ast.Enum as WsEnum
import community.flock.wirespec.compiler.core.parse.ast.Field as WsField
import community.flock.wirespec.compiler.core.parse.ast.FieldIdentifier
import community.flock.wirespec.compiler.core.parse.ast.Reference
import community.flock.wirespec.compiler.core.parse.ast.Refined as WsRefined
import community.flock.wirespec.compiler.core.parse.ast.Type as WsType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TypeOwnershipTest {

    @Test
    fun `empty input yields empty partition`() {
        val result = TypeOwnership.partition(
            endpointsByController = emptyMap(),
            allTypes = emptyList(),
        )
        result.perController shouldBe emptyMap()
        result.shared shouldBe emptyList()
    }

    @Test
    fun `single controller owning one type — type moves into controller`() {
        val ep = endpoint("GetUser", Reference.Custom("UserDto", false))
        val userDto = typeDef("UserDto")

        val result = TypeOwnership.partition(
            endpointsByController = mapOf("UserController" to listOf(ep)),
            allTypes = listOf(userDto),
        )

        result.perController["UserController"] shouldBe listOf(ep, userDto)
        result.shared shouldBe emptyList()
    }
}

private fun endpoint(name: String, responseRef: Reference?): WsEndpoint = WsEndpoint(
    comment = null,
    annotations = emptyList(),
    identifier = DefinitionIdentifier(name),
    method = WsEndpoint.Method.GET,
    path = listOf(WsEndpoint.Segment.Literal("p")),
    queries = emptyList(),
    headers = emptyList(),
    requests = listOf(WsEndpoint.Request(content = null)),
    responses = listOf(WsEndpoint.Response(
        status = "200",
        headers = emptyList(),
        content = responseRef?.let { WsEndpoint.Content("application/json", it) },
        annotations = emptyList(),
    )),
)

private fun typeDef(name: String, vararg fieldNameToRefName: Pair<String, String>): WsType = WsType(
    comment = null,
    annotations = emptyList(),
    identifier = DefinitionIdentifier(name),
    shape = WsType.Shape(
        value = fieldNameToRefName.map { (fn, refName) ->
            WsField(
                annotations = emptyList(),
                identifier = FieldIdentifier(fn),
                reference = Reference.Custom(refName, isNullable = false),
            )
        }
    ),
    extends = emptyList(),
)

private fun enumDef(name: String): WsEnum = WsEnum(
    comment = null,
    annotations = emptyList(),
    identifier = DefinitionIdentifier(name),
    entries = setOf("A", "B"),
)

@Suppress("unused")  // referenced by the refined-type test added later
private fun refinedDef(name: String): WsRefined = WsRefined(
    comment = null,
    annotations = emptyList(),
    identifier = DefinitionIdentifier(name),
    reference = Reference.Primitive(
        type = Reference.Primitive.Type.String(constraint = null),
        isNullable = false,
    ),
)

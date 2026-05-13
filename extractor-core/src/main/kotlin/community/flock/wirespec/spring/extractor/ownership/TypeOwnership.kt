package community.flock.wirespec.spring.extractor.ownership

import community.flock.wirespec.compiler.core.parse.ast.Definition
import community.flock.wirespec.compiler.core.parse.ast.Endpoint as WsEndpoint
import community.flock.wirespec.compiler.core.parse.ast.Reference
import community.flock.wirespec.compiler.core.parse.ast.Type as WsType

/**
 * Partitions extracted Wirespec definitions into "lives with one controller"
 * vs. "shared by 2+ controllers".
 *
 * A type "belongs" to a controller if it is reachable from any of that
 * controller's endpoint references (path params, queries, headers, request
 * content, response content), transitively through Object/Type field
 * references. Enums and Refined definitions are leaves in the reference graph.
 *
 * Definitions reachable from exactly one controller are appended to that
 * controller's existing endpoint list. Definitions reachable from two or more
 * controllers (or — defensively — none) end up in `shared`.
 */
internal object TypeOwnership {

    data class Partition(
        /** controllerName -> endpoint definitions (input) + owned type definitions (appended). */
        val perController: Map<String, List<Definition>>,
        /** Type definitions referenced by 2+ controllers (or zero). */
        val shared: List<Definition>,
    )

    fun partition(
        endpointsByController: Map<String, List<Definition>>,
        allTypes: List<Definition>,
        onWarn: (String) -> Unit = {},
    ): Partition {
        // Stub — Task 2 fills this in.
        return Partition(perController = endpointsByController, shared = allTypes)
    }

    /** Names of every `Reference.Custom` reachable from [reference]. */
    internal fun customNamesIn(reference: Reference): Sequence<String> = when (reference) {
        is Reference.Custom    -> sequenceOf(reference.value)
        is Reference.Iterable  -> customNamesIn(reference.reference)
        is Reference.Dict      -> customNamesIn(reference.reference)
        is Reference.Primitive -> emptySequence()
        is Reference.Any       -> emptySequence()
        is Reference.Unit      -> emptySequence()
    }

    /** Names of every `Reference.Custom` reachable from an endpoint's signature. */
    internal fun customNamesIn(endpoint: WsEndpoint): Set<String> {
        val out = linkedSetOf<String>()
        endpoint.path.forEach { seg ->
            if (seg is WsEndpoint.Segment.Param) out += customNamesIn(seg.reference).toList()
        }
        endpoint.queries.forEach  { f -> out += customNamesIn(f.reference).toList() }
        endpoint.headers.forEach  { f -> out += customNamesIn(f.reference).toList() }
        endpoint.requests.forEach { r -> r.content?.reference?.let { out += customNamesIn(it).toList() } }
        endpoint.responses.forEach { r ->
            r.content?.reference?.let { out += customNamesIn(it).toList() }
            r.headers.forEach { h -> out += customNamesIn(h.reference).toList() }
        }
        return out
    }

    /** Names of every `Reference.Custom` reachable from a Type's shape fields and extends list. */
    internal fun customNamesIn(type: WsType): Set<String> {
        val out = linkedSetOf<String>()
        type.shape.value.forEach { f -> out += customNamesIn(f.reference).toList() }
        type.extends.forEach { ref -> out += customNamesIn(ref).toList() }
        return out
    }
}

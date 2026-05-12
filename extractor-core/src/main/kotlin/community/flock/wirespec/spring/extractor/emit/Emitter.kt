// src/main/kotlin/community/flock/wirespec/spring/extractor/emit/Emitter.kt
package community.flock.wirespec.spring.extractor.emit

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import community.flock.wirespec.compiler.core.FileUri
import community.flock.wirespec.compiler.core.parse.ast.Definition
import community.flock.wirespec.compiler.core.parse.ast.FieldIdentifier
import community.flock.wirespec.compiler.core.parse.ast.Identifier
import community.flock.wirespec.compiler.core.parse.ast.Module
import community.flock.wirespec.compiler.core.parse.ast.Root
import community.flock.wirespec.compiler.utils.Logger
import community.flock.wirespec.compiler.utils.noLogger
import community.flock.wirespec.emitters.wirespec.WirespecEmitter
import java.io.File

class Emitter {

    /**
     * Custom emitter that also backticks field names starting with `_`.
     * The upstream [WirespecEmitter] already backticks names that are reserved
     * keywords or start with an uppercase letter, but Wirespec syntax also
     * requires underscore-leading field names to be quoted.
     */
    private val emitter = object : WirespecEmitter() {
        override fun emit(identifier: Identifier): String {
            if (identifier is FieldIdentifier && identifier.value.startsWith("_")) {
                return "`${identifier.value}`"
            }
            return super.emit(identifier)
        }
    }
    private val logger: Logger = noLogger

    /**
     * Render and write all `.ws` files into [outputDir].
     *
     * - Deletes existing `*.ws` files in [outputDir] recursively.
     * - Writes one `<ControllerName>.ws` per entry of [controllerEndpoints].
     * - Writes one `types.ws` containing every definition in [sharedTypes].
     * - Never touches non-`.ws` files; never writes outside [outputDir].
     */
    fun write(
        outputDir: File,
        controllerEndpoints: Map<String, List<Definition>>,
        sharedTypes: List<Definition>,
    ) {
        outputDir.mkdirs()
        clearExistingWs(outputDir)

        controllerEndpoints.forEach { (controller, defs) ->
            defs.toNonEmptyListOrNull()?.let { nel ->
                val path = File(outputDir, "$controller.ws")
                path.writeText(render(nel, "$controller.ws"))
            }
        }

        sharedTypes.toNonEmptyListOrNull()?.let { nel ->
            val path = File(outputDir, "types.ws")
            path.writeText(render(nel, "types.ws"))
        }
    }

    private fun render(defs: NonEmptyList<Definition>, fileName: String): String {
        val ast = Root(
            modules = NonEmptyList(
                head = Module(fileUri = FileUri(fileName), statements = defs),
                tail = emptyList(),
            )
        )
        return emitter.emit(ast, logger).head.result
    }

    private fun clearExistingWs(dir: File) {
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "ws" }
            .forEach { it.delete() }
    }
}

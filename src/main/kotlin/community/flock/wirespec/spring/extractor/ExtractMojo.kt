package community.flock.wirespec.spring.extractor

import community.flock.wirespec.spring.extractor.ast.WirespecAstBuilder
import community.flock.wirespec.spring.extractor.classpath.ClasspathBuilder
import community.flock.wirespec.spring.extractor.emit.Emitter
import community.flock.wirespec.spring.extractor.extract.EndpointExtractor
import community.flock.wirespec.spring.extractor.extract.TypeExtractor
import community.flock.wirespec.spring.extractor.scan.ControllerScanner
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(
    name = "extract",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.RUNTIME,
    threadSafe = true,
)
class ExtractMojo : AbstractMojo() {

    @Parameter(required = true, property = "wirespec.output")
    lateinit var output: File

    @Parameter(property = "wirespec.basePackage")
    var basePackage: String? = null

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    lateinit var project: MavenProject

    override fun execute() {
        val classesDir = File(project.build.outputDirectory)
        if (!classesDir.exists() || classesDir.listFiles().isNullOrEmpty()) {
            throw MojoExecutionException(
                "No compiled classes in ${classesDir.absolutePath}. Did `compile` run before `wirespec:extract`?"
            )
        }
        if (output.exists() && !output.canWrite()) {
            throw MojoExecutionException("Output dir not writable: ${output.absolutePath}")
        }

        val urls = ClasspathBuilder.collectUrls(
            runtimeClasspathElements = project.runtimeClasspathElements,
            outputDirectory = classesDir,
        )
        val loader = ClasspathBuilder.fromUrls(urls, parent = javaClass.classLoader)

        val scanPackages = listOfNotNull(basePackage)
        val controllers = ControllerScanner.scan(loader, scanPackages, basePackage)
        log.info("Found ${controllers.size} controller(s)")

        val types = TypeExtractor()
        val endpoints = EndpointExtractor(types)
        val builder = WirespecAstBuilder()

        val collisions = detectControllerCollisions(controllers)
        if (collisions.isNotEmpty()) {
            val msg = collisions.entries.joinToString("; ") { (name, classes) ->
                "$name in [${classes.joinToString(", ")}]"
            }
            throw MojoExecutionException("Controller simple-name collisions: $msg")
        }

        val byController = controllers.associate { c ->
            val eps = try {
                endpoints.extract(c).map(builder::toEndpoint)
            } catch (t: Throwable) {
                log.warn("Skipping ${c.name}: ${t.message}")
                emptyList()
            }
            c.simpleName to eps.map { it as community.flock.wirespec.compiler.core.parse.ast.Definition }
        }.filterValues { it.isNotEmpty() }

        val sharedTypes = types.definitions.mapNotNull { def ->
            try { builder.toDefinition(def) } catch (t: Throwable) {
                log.warn("Skipping type ${def}: ${t.message}"); null
            }
        }

        Emitter().write(
            outputDir = output,
            controllerEndpoints = byController,
            sharedTypes = sharedTypes,
        )
        log.info("Wrote ${byController.size + (if (sharedTypes.isEmpty()) 0 else 1)} .ws file(s) to ${output.absolutePath}")
    }
}

/**
 * Returns a map of simple name -> list of FQNs for controllers that share the same simple name.
 * An empty map means no collisions.
 */
internal fun detectControllerCollisions(controllers: List<Class<*>>): Map<String, List<String>> =
    controllers.groupBy { it.simpleName }.filterValues { it.size > 1 }.mapValues { (_, classes) -> classes.map { it.name } }

package community.flock.wirespec.spring.extractor

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
        val runtimeClasspath: List<File> = try {
            project.runtimeClasspathElements.orEmpty().map(::File)
        } catch (_: Exception) {
            emptyList()
        }

        try {
            WirespecExtractor.extract(
                ExtractConfig(
                    classesDirectory = File(project.build.outputDirectory),
                    runtimeClasspath = runtimeClasspath,
                    outputDirectory = output,
                    basePackage = basePackage,
                    log = MavenExtractLog(log),
                )
            )
        } catch (e: WirespecExtractorException) {
            throw MojoExecutionException(e.message, e.cause)
        }
    }
}

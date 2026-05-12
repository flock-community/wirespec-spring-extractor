package community.flock.wirespec.spring.extractor

import org.apache.maven.plugin.AbstractMojo
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
        log.info("wirespec-spring-extractor: output=$output basePackage=$basePackage")
        // Pipeline wired in Task 14.
    }
}

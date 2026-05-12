package community.flock.wirespec.spring.extractor

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class WirespecExtractorTest {

    /** Locate the test-classes directory of this module on the runtime classpath. */
    private fun thisModuleClassesDir(): File {
        // Any fixture class works as a probe; HelloController is small and stable.
        val probe = community.flock.wirespec.spring.extractor.fixtures.HelloController::class.java
        val classFileUrl = probe.protectionDomain.codeSource.location
        return File(classFileUrl.toURI())
    }

    @Test
    fun `extract writes ws files for known controllers and returns counts`(@TempDir tmp: Path) {
        val out = File(tmp.toFile(), "ws").apply { mkdirs() }
        val result = WirespecExtractor.extract(
            ExtractConfig(
                classesDirectory = thisModuleClassesDir(),
                runtimeClasspath = emptyList(),
                outputDirectory = out,
                basePackage = "community.flock.wirespec.spring.extractor.fixtures",
            )
        )

        result.controllerCount shouldBe (out.listFiles()!!.filter { it.name != "types.ws" }.size)
        result.filesWritten.map { it.name } shouldContainAll listOf("HelloController.ws")
        result.filesWritten.all { it.exists() } shouldBe true
    }

    @Test
    fun `extract throws WirespecExtractorException when classes directory is missing`(@TempDir tmp: Path) {
        val missing = File(tmp.toFile(), "does-not-exist")
        val out = File(tmp.toFile(), "ws").apply { mkdirs() }

        val ex = assertThrows<WirespecExtractorException> {
            WirespecExtractor.extract(
                ExtractConfig(
                    classesDirectory = missing,
                    runtimeClasspath = emptyList(),
                    outputDirectory = out,
                )
            )
        }
        ex.message!! shouldContain "No compiled classes in"
        ex.message!! shouldContain "Did `compile` run before `wirespec:extract`?"
    }

    @Test
    fun `extract routes log messages through the provided ExtractLog`(@TempDir tmp: Path) {
        val out = File(tmp.toFile(), "ws").apply { mkdirs() }
        val infos = mutableListOf<String>()
        val warns = mutableListOf<String>()
        val log = object : ExtractLog {
            override fun info(msg: String) { infos += msg }
            override fun warn(msg: String) { warns += msg }
        }

        WirespecExtractor.extract(
            ExtractConfig(
                classesDirectory = thisModuleClassesDir(),
                runtimeClasspath = emptyList(),
                outputDirectory = out,
                basePackage = "community.flock.wirespec.spring.extractor.fixtures",
                log = log,
            )
        )

        infos.any { it.startsWith("Found ") && it.endsWith(" controller(s)") } shouldBe true
        infos.any { it.startsWith("Wrote ") && it.contains(".ws file(s) to") } shouldBe true
    }

    @Test
    fun `ExtractLog NoOp is the default and is safe`(@TempDir tmp: Path) {
        val out = File(tmp.toFile(), "ws").apply { mkdirs() }
        // Just verify the default is wired without throwing.
        WirespecExtractor.extract(
            ExtractConfig(
                classesDirectory = thisModuleClassesDir(),
                runtimeClasspath = emptyList(),
                outputDirectory = out,
                basePackage = "community.flock.wirespec.spring.extractor.fixtures",
            )
        )
    }
}

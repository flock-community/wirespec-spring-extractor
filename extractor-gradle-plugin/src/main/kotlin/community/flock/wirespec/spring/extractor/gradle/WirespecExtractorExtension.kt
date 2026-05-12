package community.flock.wirespec.spring.extractor.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Build-script DSL for the Gradle plugin:
 *
 * ```kotlin
 * wirespec {
 *     outputDir.set(layout.buildDirectory.dir("wirespec"))   // default
 *     basePackage.set("com.acme.api")
 * }
 * ```
 */
abstract class WirespecExtractorExtension {
    abstract val outputDir: DirectoryProperty
    abstract val basePackage: Property<String>
}

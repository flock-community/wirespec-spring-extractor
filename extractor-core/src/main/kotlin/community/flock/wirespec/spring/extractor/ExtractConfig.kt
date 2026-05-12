package community.flock.wirespec.spring.extractor

import java.io.File

/**
 * Input to [WirespecExtractor.extract].
 *
 * @property classesDirectory  Directory of compiled `.class` files to scan
 *   (typically `target/classes` in a Maven project).
 * @property runtimeClasspath  Additional jars and directories needed so the
 *   class loader can resolve types referenced by scanned classes.
 * @property outputDirectory   Where `.ws` files will be written.
 * @property basePackage       Optional package prefix to restrict scanning;
 *   `null` or blank means scan every package.
 * @property log               Logger sink. Defaults to [ExtractLog.NoOp].
 */
data class ExtractConfig(
    val classesDirectory: File,
    val runtimeClasspath: List<File>,
    val outputDirectory: File,
    val basePackage: String? = null,
    val log: ExtractLog = ExtractLog.NoOp,
)

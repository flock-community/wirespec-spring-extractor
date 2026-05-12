package community.flock.wirespec.spring.extractor.classpath

import java.io.File
import java.net.URL
import java.net.URLClassLoader

object ClasspathBuilder {

    /** Build a child URLClassLoader over the given URLs. */
    fun fromUrls(urls: List<URL>, parent: ClassLoader): URLClassLoader =
        URLClassLoader(urls.toTypedArray(), parent)

    /**
     * Combine `outputDirectory` with the runtime classpath into the URL list
     * to feed a URLClassLoader. The output dir comes first so the project's
     * own classes win over duplicates pulled in transitively.
     */
    fun collectUrls(runtimeClasspathElements: List<String>, outputDirectory: File): List<URL> {
        val output = outputDirectory.toURI().toURL()
        val deps = runtimeClasspathElements.map { File(it).toURI().toURL() }
        return (listOf(output) + deps).distinct()
    }
}

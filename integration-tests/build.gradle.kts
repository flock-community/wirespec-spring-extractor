plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "End-to-end tests that drive the published plugin against fixture Spring/Kotlin projects via `mvn`."

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
}

// Path used by the test runner to locate the IT-local Maven repo where the
// plugin module has just been published. Shared by all tests and tasks here.
val itRepoDir = rootProject.layout.buildDirectory.dir("it-repo")

tasks.test {
    useJUnitPlatform()

    // The fixture builds need our just-built plugin to be discoverable in a
    // local Maven repo. Publish it into a project-local repo (NOT ~/.m2) so
    // we don't pollute the user's environment and tests stay reproducible.
    dependsOn(":extractor-maven-plugin:publishMavenPublicationToItLocalRepository")

    systemProperty("it.pluginVersion", project.version.toString())
    systemProperty("it.repo", itRepoDir.get().asFile.absolutePath)
    systemProperty("it.fixturesRoot", layout.projectDirectory.dir("src/it").asFile.absolutePath)
    systemProperty("it.workRoot", layout.buildDirectory.dir("it-work").get().asFile.absolutePath)

    // `mvn` is invoked as an external process; show its stdout/stderr only on
    // failure to keep passing-test output quiet.
    testLogging {
        showStandardStreams = false
        events("failed")
    }
}

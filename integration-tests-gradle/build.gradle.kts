plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "End-to-end tests that drive the Gradle plugin against fixture Gradle/Spring/Kotlin projects via TestKit."

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(gradleTestKit())
}

// Path used by the test runner to locate the IT-local Maven repo where the
// Gradle plugin + extractor-core have just been published. Shared with the
// :integration-tests (Maven) module — both write into the same `it-repo`.
val itRepoDir = rootProject.layout.buildDirectory.dir("it-repo")

tasks.test {
    useJUnitPlatform()

    // Publish both the plugin jar AND the Gradle plugin marker artifact, plus
    // extractor-core (transitive dependency), into the project-local repo
    // before the IT runs. Marker is what makes `plugins { id("...") }` resolve.
    dependsOn(":extractor-gradle-plugin:publishAllPublicationsToItLocalRepository")
    dependsOn(":extractor-core:publishMavenPublicationToItLocalRepository")

    systemProperty("it.pluginVersion", project.version.toString())
    systemProperty("it.repo", itRepoDir.get().asFile.absolutePath)
    systemProperty("it.fixturesRoot", layout.projectDirectory.dir("src/it").asFile.absolutePath)
    systemProperty("it.workRoot", layout.buildDirectory.dir("it-work").get().asFile.absolutePath)
    systemProperty("it.testKitDir", layout.buildDirectory.dir("it-testkit").get().asFile.absolutePath)

    // `gradle` is invoked via TestKit (in-process). Show its output only on failure.
    testLogging {
        showStandardStreams = false
        events("failed")
    }
}

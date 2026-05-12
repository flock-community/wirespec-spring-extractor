plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.gradle.plugin.publish)
    `maven-publish`
}

description = "Gradle plugin: extracts Spring Boot endpoints into Wirespec .ws files."
base.archivesName.set("wirespec-spring-extractor-gradle-plugin")

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    website.set("https://wirespec.io")
    vcsUrl.set("https://github.com/flock-community/wirespec-spring-extractor")
    plugins {
        register("wirespecExtractor") {
            id = "community.flock.wirespec.spring.extractor"
            displayName = "Wirespec Spring Extractor"
            description = project.description
            implementationClass = "community.flock.wirespec.spring.extractor.gradle.WirespecExtractorPlugin"
            tags.set(listOf("wirespec", "spring", "openapi", "schema"))
        }
    }
}

dependencies {
    // Core extraction logic. Consumers resolve it transitively from the published POM.
    implementation(project(":extractor-core"))
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}

// Configure the auto-generated `pluginMaven` publication (jar) + `<id>PluginMarkerMaven`
// (marker) created by java-gradle-plugin. We override the jar's artifactId so the
// Maven coordinate matches the descriptive `wirespec-spring-extractor-gradle-plugin`
// rather than the module name.
publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "wirespec-spring-extractor-gradle-plugin"
            pom {
                name.set("Wirespec Spring Extractor Gradle Plugin")
                description.set(project.description)
            }
        }
    }
    repositories {
        // Build-local repo used by :integration-tests-gradle. Publishing here
        // keeps the fixture Gradle builds isolated from the user's ~/.m2.
        maven {
            name = "itLocal"
            url = uri(rootProject.layout.buildDirectory.dir("it-repo"))
        }
    }
}

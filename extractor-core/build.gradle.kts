plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

description = "Spring → Wirespec extraction logic. Maven-agnostic; drives the Maven plugin."
base.archivesName.set("wirespec-spring-extractor-core")

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Spring's @PathVariable/@RequestParam (and our extractor) need parameter names.
        freeCompilerArgs.add("-java-parameters")
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.classgraph)
    // `api` because WirespecAstBuilder.toEndpoint / toDefinition expose
    // Wirespec compiler AST types in their public signatures — consumers
    // of extractor-core (e.g. ExtractMojo) need those types on the
    // compile classpath.
    api(libs.wirespec.core)
    implementation(libs.wirespec.emitter)
    implementation(libs.spring.web)
    implementation(libs.spring.context)
    implementation(libs.jackson.annotations)
    implementation(libs.jakarta.validation)
    implementation(libs.swagger.annotations)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.reactor.core)
}

tasks.test {
    useJUnitPlatform()
}

// Expose test classes (notably the `fixtures/` subtree) to other modules'
// test classpaths via a secondary configuration. Used by
// `:extractor-maven-plugin`'s ExtractMojoTest, which references controller
// fixtures defined here.
val testJar = tasks.register<Jar>("testJar") {
    archiveClassifier.set("tests")
    from(sourceSets.test.get().output)
}
val testArtifacts: Configuration by configurations.creating
artifacts.add(testArtifacts.name, testJar)

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "wirespec-spring-extractor-core"
            pom {
                name.set("Wirespec Spring Extractor Core")
                description.set(project.description)
            }
        }
    }
    repositories {
        // Same build-local repo as the Maven plugin: integration tests can
        // resolve both artifacts from one place.
        maven {
            name = "itLocal"
            url = uri(rootProject.layout.buildDirectory.dir("it-repo"))
        }
    }
}

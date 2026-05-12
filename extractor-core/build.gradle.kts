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
    implementation(libs.wirespec.core)
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

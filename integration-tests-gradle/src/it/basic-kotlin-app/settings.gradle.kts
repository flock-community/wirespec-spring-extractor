pluginManagement {
    repositories {
        // Substituted by the test runner with the it-repo URL where the
        // freshly-built Gradle plugin (jar + marker) lives.
        maven { url = uri("@itRepo@") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Same it-repo for extractor-core (transitive of the plugin),
        // then Maven Central for everything else (Spring, Kotlin stdlib, ...).
        maven { url = uri("@itRepo@") }
        mavenCentral()
    }
}

rootProject.name = "basic-kotlin-app"

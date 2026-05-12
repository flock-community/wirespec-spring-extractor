rootProject.name = "wirespec-spring-extractor"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(":extractor-core")
include(":extractor-maven-plugin")
include(":extractor-gradle-plugin")
include(":integration-tests")
include(":integration-tests-gradle")

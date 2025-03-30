plugins {
    // To automatically resolve toolchains
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// Centralized repository declarations
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // Fail if projects also declare repositories
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

// Project structure
rootProject.name = "nav"
include("app")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

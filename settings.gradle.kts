pluginManagement {
    // Composite build hosting the compiler plugin + Gradle plugin.
    // Makes id("com.wassimbeltaief.loupe") resolvable in module build scripts.
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Also included at top level (in addition to pluginManagement) so that the
// compiler plugin coordinates from LoupeGradlePlugin.getPluginArtifact()
// resolve to the local project instead of a remote repository.
includeBuild("build-logic") {
    dependencySubstitution {
        substitute(module("com.wassimbeltaief:loupe-plugin")).using(project(":loupe-plugin"))
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "loupe"

include(":loupe-runtime")
include(":loupe-testing")
include(":loupe-sample-android")

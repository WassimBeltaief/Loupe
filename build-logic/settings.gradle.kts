pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    // Reuse the root version catalog so dependency versions never drift
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "loupe-build-logic"

include(":loupe-plugin")
include(":loupe-plugin-gradle")

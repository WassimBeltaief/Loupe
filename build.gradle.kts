plugins {
    kotlin("jvm") version libs.versions.kotlin.get() apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

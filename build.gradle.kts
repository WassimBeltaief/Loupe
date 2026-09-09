plugins {
    kotlin("jvm") version libs.versions.kotlin.get() apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.lib) apply false
}


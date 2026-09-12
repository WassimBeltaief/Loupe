plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

dependencies {
    // Stable KGP API surface only — never kotlin-gradle-plugin internals
    implementation(libs.kotlin.gradle.plugin.api)
    // The compiler plugin, wired into the same composite build.
    // For publishing (#26) this must become a fat JAR or a published coordinate.
    implementation(project(":loupe-plugin"))
    // compileOnly: present on the consumer's classpath whenever an Android plugin is applied
    compileOnly(libs.android.tools.gradle)
}

gradlePlugin {
    plugins {
        create("loupe") {
            id = "com.wassimbeltaief.loupe"
            implementationClass = "com.wassimbeltaief.loupe.gradle.LoupeGradlePlugin"
            displayName = "Loupe"
            description = "Zero-instrumentation recomposition debugger for Jetpack Compose — debug builds only"
        }
    }
}

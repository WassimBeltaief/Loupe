plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Coordinates must stay in sync with LoupeGradlePlugin's getPluginArtifact() —
// composite-build substitution resolves the compiler plugin JAR by group + name.
group = "com.wassimbeltaief"
version = "1.0.0-alpha01"

dependencies {
    // The Kotlin compiler itself — available at build time, not bundled into our jar
    compileOnly(libs.kotlin.compiler)

    // Tests need to actually run the compiler, so it's a full dependency there
    testImplementation(libs.kotlin.compiler)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kct.core)
}

tasks.test {
    useJUnitPlatform()
}

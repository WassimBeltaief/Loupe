plugins {
    alias(libs.plugins.kotlin.jvm)
}

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

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

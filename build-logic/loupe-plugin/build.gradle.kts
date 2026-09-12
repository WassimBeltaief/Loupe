plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
}

// Project coordinates must stay in sync with LoupeGradlePlugin's
// getPluginArtifact() — composite-build substitution resolves by group + name.
group = "com.wassimbeltaief"
version = "1.0.0-alpha01"

mavenPublishing {
    coordinates("com.wassimbeltaief", "loupe-plugin", "1.0.0-alpha01")
    pom {
        name.set("Loupe Compiler Plugin")
        description.set("Kotlin compiler plugin that instruments @Composable functions for Loupe")
        inceptionYear.set("2026")
        url.set("https://github.com/WassimBeltaief/loupe")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("WassimBeltaief")
                name.set("Wassim Beltaief")
            }
        }
        scm {
            url.set("https://github.com/WassimBeltaief/loupe")
            connection.set("scm:git:git://github.com/WassimBeltaief/loupe.git")
            developerConnection.set("scm:git:ssh://git@github.com/WassimBeltaief/loupe.git")
        }
    }
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    // Sign only when release keys are configured (gradle.properties signing.keyId
    // or SIGNING_KEY env) — publishToMavenLocal works without them
    if (providers.gradleProperty("signing.keyId").isPresent ||
        providers.environmentVariable("SIGNING_KEY").isPresent
    ) {
        signAllPublications()
    }
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

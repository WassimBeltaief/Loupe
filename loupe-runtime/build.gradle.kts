plugins {
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.maven.publish)
}

mavenPublishing {
    coordinates("com.wassimbeltaief", "loupe-runtime", "1.0.1")
    pom {
        name.set("Loupe Runtime")
        description.set("Zero-instrumentation recomposition debugger for Jetpack Compose")
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

android {
    namespace = "com.wassimbeltaief.loupe.runtime"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    // Keep JVM unit tests fast — no Robolectric needed for pure logic tests
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    // #13 heatmap: reads the composition slot table (Layout-Inspector-grade bounds).
    // Debug-only in practice — consumers add loupe-runtime via debugImplementation.
    implementation(libs.compose.ui.tooling.data)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.savedstate)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.compose.ui)
    testImplementation(libs.compose.ui.tooling.data)
}

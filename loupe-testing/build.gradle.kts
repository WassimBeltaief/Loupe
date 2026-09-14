plugins {
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.maven.publish)
}

mavenPublishing {
    coordinates("com.wassimbeltaief", "loupe-testing", "1.1.0")
    pom {
        name.set("Loupe Testing")
        description.set("Compose recomposition assertion DSL for Jetpack Compose tests")
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
    if (providers.gradleProperty("signing.keyId").isPresent ||
        providers.environmentVariable("SIGNING_KEY").isPresent
    ) {
        signAllPublications()
    }
}

android {
    namespace = "com.wassimbeltaief.loupe.testing"
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Exposed transitively so consumers get LoupeReport, CompositionHistory, etc.
    api(project(":loupe-runtime"))

    // api: SemanticsNodeInteraction and ComposeContentTestRule appear in public signatures
    api(platform(libs.compose.bom))
    api(libs.compose.ui.test.junit4)

    // Registers the host ComponentActivity that createComposeRule needs — consumers get
    // this automatically rather than having to add it to their own app module
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.androidx.test.junit)

    testImplementation(libs.kotlin.test)
}

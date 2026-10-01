plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.junit5)
    `maven-publish`
}

group = "com.bitperfect.player"
version = "6.0.0"

android {
    namespace = "com.bitperfect.player.core.plugin.page"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.serialization.json)
    testImplementation("org.jetbrains.kotlin:kotlin-test:${libs.versions.kotlin.get()}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${libs.versions.kotlin.get()}")
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.bitperfect.player"
                artifactId = "plugin-pages"
                version = "6.0.0"
            }
        }
    }
}

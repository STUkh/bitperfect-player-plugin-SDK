plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.bitperfect.player.sdk.plugin.aidl"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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

    buildFeatures {
        aidl = true
    }
}

dependencies {
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
}

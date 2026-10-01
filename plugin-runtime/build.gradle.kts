plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.bitperfect.player.sdk.plugin.runtime"
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
}

dependencies {
    implementation(project(":plugin-aidl"))
    api(project(":plugin-pages"))
    testImplementation(libs.junit5)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
}

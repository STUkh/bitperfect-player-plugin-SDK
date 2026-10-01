pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "bitperfect-player-plugin-sdk"

include(":plugin-aidl")
include(":plugin-pages")
include(":plugin-runtime")
include(":sample-plugin")
include(":sample-network-source")
include(":sample-dsp-filter")

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
    }
}

rootProject.name = "bitperfect-player-plugin-sdk"

include(":plugin-aidl")
include(":plugin-pages")
include(":plugin-runtime")
include(":sample-plugin")

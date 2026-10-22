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
        // NewPipe Extractor (used for YouTube search / stream resolution) is published on JitPack.
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "Off"
include(":app")

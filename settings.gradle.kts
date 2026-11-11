pluginManagement {
    repositories {
        google()
        // Google's mirror of Maven Central first (avoids rate limits), then Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google's mirror of Maven Central first (avoids rate limits), then Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        // NewPipeExtractor (YouTube search and audio download) and its nanojson fork are only published on JitPack.
        maven("https://jitpack.io") { content { includeGroup("com.github.TeamNewPipe") } }
    }
}
rootProject.name = "SuperSax"
include(":app")

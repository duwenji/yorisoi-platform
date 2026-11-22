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
        // Astronomy Engine is only on JitPack; nothing else is taken from there (REQ-KOYOMI D-1).
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("io.github.cosinekitty") }
        }
    }
}
rootProject.name = "yorisoi-platform"
// The platform (ECOSYSTEM E-2, E-28): core libraries (plain JVM) and Android kits. Each module's name
// is its artifactId, so an app's composite build swaps the published versions for these sources
//.
include(":core-format")
include(":core-text")
include(":core-web")
include(":core-agent")
include(":core-prompt")
include(":kit-ui")
include(":kit-voice")
include(":kit-llm")
include(":kit-geo")
include(":kit-mood")
include(":kit-companion")
include(":kit-channel")
include(":kit-local-materials")
include(":kit-songs")
include(":kit-calendar")

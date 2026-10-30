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
rootProject.name = "yorisoi-platform"
// The platform (ADR-003 E-2, E-28): core libraries (plain JVM) and Android kits. Each module's name
// is its artifactId, so an app's composite build swaps the published versions for these sources
//.
include(":core-format")
include(":core-prompt")
include(":kit-ui")
include(":kit-voice")
include(":kit-llm")
include(":kit-geo")
include(":kit-mood")
include(":kit-companion")
include(":kit-channel")

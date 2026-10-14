import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Every module is published to GitHub Packages under one version: the core
// libraries as JARs, the kits as AARs (kit-companion is plain JVM). The group and the module
// names match the coordinates, so an app's composite build can stand in for them.
subprojects {
    group = "io.github.duwenji.yorisoi"
    version = providers.gradleProperty("yorisoiVersion").get()

    apply(plugin = "maven-publish")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> { withSourcesJar() }
        extensions.configure<PublishingExtension> {
            publications.create<MavenPublication>("maven") { from(components["java"]) }
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            publishing {
                singleVariant("release") { withSourcesJar() }
            }
        }
        // The release component exists only once the Android plugin has evaluated the project.
        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications.create<MavenPublication>("maven") { from(components["release"]) }
            }
        }
    }

    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set("yorisoi ${project.name}")
                description.set(provider { project.description })
                url.set("https://github.com/duwenji/yorisoi-platform")
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/duwenji/yorisoi-platform")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

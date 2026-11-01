import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The channel and collection package formats (API-CHANNEL-PACKAGE, API-MATERIALS) and the channel list (API-CHANNEL-LIST):
// reading and checking packages, their signatures and the list. Plain JVM so the channel
// management system's review tools run exactly the checks the apps run (ECOSYSTEM E-3).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Channel and collection package formats, their checks and the channel list (API-CHANNEL-LIST/003/004). Requires org.json on the classpath."

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Android ships org.json; bundling the jar into the app would clash with it
    // (lint: DuplicatePlatformClasses). Other users add it themselves.
    compileOnly(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}

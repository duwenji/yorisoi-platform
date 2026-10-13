import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The channel and collection package formats (API-003, API-004) and the channel list (API-002):
// reading and checking packages, their signatures and the list. Plain JVM so the channel
// management system's review tools run exactly the checks the apps run (ADR-003 E-3, RFC-002 R-2).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Channel and collection package formats, their checks and the channel list (API-002/003/004). Requires org.json on the classpath."

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

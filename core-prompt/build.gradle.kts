import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The prompt template engine (a small subset of Mustache). Plain JVM, no dependencies, so the
// apps and the channel management system render prompts the same way (ADR-003 E-3).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Prompt templates: the subset of Mustache the apps' prompt files are written in."

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
    testImplementation(libs.junit)
}

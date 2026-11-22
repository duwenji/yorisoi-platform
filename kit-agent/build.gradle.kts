// Running the preparer on the app's side (platform ADR-AGENT A-32ÅEA-33ÅEA-34ÅEA-49): when it runs, its limits per walk
// and per day, the threads it made kept on the device, and the developer's record of each preparation. It
// leans on core-agent and kit-llm only; the tools come in from kit-agent-tools.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "Running the preparer on the app's side: when it runs, its limits, the kept threads and the developer's record."

android {
    namespace = "io.github.duwenji.yorisoi.kit.agent"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core-agent"))
    api(project(":kit-llm"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // The real org.json; Android's is a stub in local unit tests.
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.core)
}

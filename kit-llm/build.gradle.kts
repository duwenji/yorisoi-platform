// AI services (Claude, OpenAI-compatible APIs) and the Keystore cipher for their keys.
// Published with the other modules.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "AI services (Claude, OpenAI-compatible APIs, the operator's relay), the Keystore cipher for their keys, the daily count of calls and the check of replies."

android {
    namespace = "io.github.duwenji.yorisoi.kit.llm"
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.integrity)
    implementation(libs.okhttp)
    implementation(libs.anthropic.java)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
}

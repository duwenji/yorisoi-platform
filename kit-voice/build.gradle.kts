// Speech (TTS with audio focus) and synthesized background sounds (RFC-002). Published with
// the other modules (RFC-004 R-4).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "Speech (TTS with audio focus) and synthesized background sounds."

android {
    namespace = "io.github.duwenji.yorisoi.kit.voice"
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

    testImplementation(libs.junit)
}

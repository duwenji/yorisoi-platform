import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Channels in an app: a channel ready to use with the user's changes, the list of channels and the
// one in use, the built-in channels read from resources, what a third party's channel can show,
// third-party channels from the lists the user added (remote), the outside material they fetch
// ahead and the producers that make material for a cue (material), and reports on a channel (report).
// Plain JVM: the app's prompt pack reads its built-in channels through here, and the channel
// management system's review tools use the pack. Published with the other modules.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Channels in an app: a channel with the user's changes, the list and the one in use, built-in channels from resources, third-party channels from lists. Requires org.json on the classpath (and OkHttp, Tink for OkHttpProviderHttp, TinkEd25519)."

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
    api(project(":core-format"))
    implementation(libs.kotlinx.coroutines.core)
    // As in core-format: Android ships org.json.
    compileOnly(libs.json)
    // Only OkHttpProviderHttp and TinkEd25519 use these; an app that uses them adds them itself, so
    // the channel management system's review tools don't carry them.
    compileOnly(libs.okhttp)
    compileOnly(libs.tink.android)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.tink.android)
}

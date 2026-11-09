// The season's songs on the app's side (platform API-SONGS): the catalog and the sounds fetched from the
// operator's delivery and kept on the device, choosing a song for the season, and playing it.
// Published with the other modules.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "The season's songs on the app's side: the catalog and sounds kept on the device, choosing a song, playing it."

android {
    namespace = "io.github.duwenji.yorisoi.kit.songs"
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
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    // The real org.json; Android's is a stub in local unit tests.
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
}

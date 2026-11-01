// Local materials on the app's side (platform API-LOCAL-MATERIALS): the studios and notes around the walk or
// ride, as the built-in producers builtin:studio and builtin:note that channels' cues show, and notes placed by
// voice. Published with the other modules.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "Local materials on the app's side: the studios and notes around the walk or ride as built-in producers for channels' cues, and notes placed by voice."

android {
    namespace = "io.github.duwenji.yorisoi.kit.local"
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
    // The relay's session (API-LOCAL-MATERIALS ST-2).
    api(project(":kit-llm"))
    // builtin:studio and builtin:note are producers channels' cues ask (API-MATERIALS).
    api(project(":kit-channel"))
    // Cells (Geohash).
    implementation(project(":kit-geo"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    // The real org.json; Android's is a stub in local unit tests.
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
}

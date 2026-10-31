// The studio on the app's side (platform API-STUDIO): notes and placements near the walk or ride,
// read out between the narration, and notes placed by voice. Published with the other modules.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "The studio on the app's side: notes and placements near the walk or ride, read out between the narration, and notes placed by voice."

android {
    namespace = "io.github.duwenji.yorisoi.kit.studio"
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
    // The relay's session (API-STUDIO ST-2).
    api(project(":kit-llm"))
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

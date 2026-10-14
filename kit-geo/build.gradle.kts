// Places, routes, photos, weather and the map. Published with the other modules.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

description = "Places, routes, photos, weather and the map."

android {
    namespace = "io.github.duwenji.yorisoi.kit.geo"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The spot kinds a search asks for (SpotKind).
    api(project(":core-format"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.osmdroid.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    // The real org.json; Android's is a stub in local unit tests.
    testImplementation(libs.json)
}

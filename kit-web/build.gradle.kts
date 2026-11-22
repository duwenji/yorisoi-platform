// The web on the app's side (platform ADR-AGENT A-19・A-20・A-26・A-34): reading pages and feeds from the device
// with the app's manners (robots.txt, its own User-Agent, no cookies, two at a time and one per site), the page
// and hint caches, and the operator's delivered files (the sources' ledger, the exclusion list, the season's
// observations) with their versions. What may be read and what is taken from a page is core-web's.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "The web on the app's side: reading pages and feeds with robots.txt and limits, the page and hint caches, and the operator's delivered files."

android {
    namespace = "io.github.duwenji.yorisoi.kit.web"
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
    api(project(":core-web"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    // The real org.json; Android's is a stub in local unit tests.
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)
}

// The preparer's standard tools (platform ADR-AGENT A-6・A-7・A-12・A-69): what the app already has for the talk
// (the calendar, the weather, the season's observations, the local events, the spots around, the materials) as
// numbered facts, and the pages of the sources read with kit-web and turned into hints. The channel's policy is
// applied inside the tools, so both apps keep it the same way.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "The preparer's standard tools: the app's facts as numbered results, read_page with hints, nearby_spots and materials, under the channel's policy."

android {
    namespace = "io.github.duwenji.yorisoi.kit.agent.tools"
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
    api(project(":kit-web"))
    api(project(":kit-calendar"))
    api(project(":kit-geo"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // The real org.json; Android's is a stub in local unit tests.
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)
}

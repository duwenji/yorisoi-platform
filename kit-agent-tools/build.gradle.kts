import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The preparer's standard tools (platform AGENT A-6・A-7・A-12・A-69): the facts the app or the validator already
// has (the calendar, the weather, the season's observations, the local events, the spots around, the materials) as
// numbered facts, and the pages of the sources read with kit-web and turned into hints. The channel's policy is
// applied inside the tools, so both apps and the validator keep it the same way. Plain JVM (A-54).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "The preparer's standard tools: the facts as numbered results, read_page with hints, nearby_spots and materials, under the channel's policy."

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
    api(project(":core-agent"))
    api(project(":kit-web"))
    api(project(":kit-calendar"))
    // The channel as the apps read it, for the policy's narrowing (ResearchPolicy).
    api(project(":kit-channel"))
    implementation(libs.kotlinx.coroutines.core)
    // Android ships org.json.
    compileOnly(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
}

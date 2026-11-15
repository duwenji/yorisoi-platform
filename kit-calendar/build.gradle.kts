import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The calendar's facts for a day (REQ-KOYOMI): the 24 solar terms and the 72 pentads, the old
// lunisolar calendar (rokuyou, the harvest moon), the seasonal days, the national holidays, the
// moon's quarters, and the tables of festivals and designated days. Plain JVM; no network.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "The calendar's facts for a day: solar terms, pentads, the lunisolar calendar, seasonal days, holidays, moon phases."

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
    implementation(libs.astronomy)
    testImplementation(libs.junit)
    // Only to check the channel format spells the kinds the same way (REQ-KOYOMI F-21).
    testImplementation(project(":core-format"))
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The machine checks on text that every path shares (ADR-AGENT A-52): slots and materials in review
// (core-format), the AI's replies before they are read out (kit-llm), and, next, pages and link titles.
// Plain JVM with no dependencies, so the apps and the review tools run the same checks.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Machine checks on text: contact details and URLs, headings, and the marks taken out before text is read out."

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
    testImplementation(libs.junit)
    testImplementation(libs.json)
}

// The shared samples (docs/contracts/api/text-safety-vectors.json) that every implementation must meet.
tasks.withType<Test>().configureEach {
    systemProperty("textSafetyVectors", rootProject.file("docs/contracts/api/text-safety-vectors.json").absolutePath)
}

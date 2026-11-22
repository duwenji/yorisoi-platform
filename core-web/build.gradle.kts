import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What may be read from the web for the talk, and what is taken from it (ADR-AGENT 4・5): the sources'
// ledger and the exclusion list, which URLs are in reach, robots.txt, and the text and links of a page or
// a feed. Plain JVM with no network or storage, so the apps and the review tools decide the same way.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Web reading rules: the sources' ledger, the exclusion list, URLs in reach, robots.txt, and the text and links of pages and feeds. Requires org.json on the classpath."

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
    api(project(":core-text"))
    implementation(libs.jsoup)
    // Android ships org.json; other users add it themselves (same as core-format).
    compileOnly(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}

// The shared samples (docs/contracts/api/web-url-vectors.json) every implementation must meet.
tasks.withType<Test>().configureEach {
    systemProperty("webUrlVectors", rootProject.file("docs/contracts/api/web-url-vectors.json").absolutePath)
}

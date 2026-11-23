import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The web as the preparer reads it (platform AGENT A-19・A-20・A-26・A-34): reading pages and feeds with the
// apps' manners (robots.txt, its own User-Agent, no cookies, two at a time and one per site), the page and hint
// caches, and the operator's delivered files (the sources' ledger, the exclusion list, the season's observations)
// with their versions. What may be read and what is taken from a page is core-web's. Plain JVM, so the apps and the
// console's validator read pages the same way (A-54).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "The web as the preparer reads it: pages and feeds with robots.txt and limits, the page and hint caches, and the operator's delivered files."

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
    api(project(":core-web"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    // Android ships org.json.
    compileOnly(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
}

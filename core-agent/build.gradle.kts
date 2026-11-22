import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The preparer's rules (ADR-AGENT): the tools' definitions and the check of their arguments, the numbered
// results, the turns and their limits, the context's budget and how it is cut, the check of the submitted
// threads, and the hint maker's prompt and the reading of its answer. No network: the AI and the tools
// come in through interfaces, so the apps and the review tools run the same preparation.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "The preparer's rules: tool definitions and argument checks, numbered results, turns and limits, the context budget, the check of submitted threads, and the hint maker. Requires org.json on the classpath."

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
    // Android ships org.json; other users add it themselves (same as core-format).
    compileOnly(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}

// The tools' definitions are shared with the relay (docs/contracts/api, ADR-AGENT A-45): one file, packed as is.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("docs/contracts/api")) {
        include("agent-tools.v*.json")
        into("io/github/duwenji/yorisoi/core/agent")
    }
}

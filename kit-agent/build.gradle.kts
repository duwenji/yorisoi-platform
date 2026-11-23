import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Running the preparer (platform AGENT A-32・A-33・A-34・A-49): when it runs, its limits per walk and per day,
// the threads it made kept, and the developer's record of each preparation. It leans on core-agent only: the model
// comes in from kit-llm (the apps) or the validator, the tools from kit-agent-tools. Plain JVM (A-54).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Running the preparer: when it runs, its limits, the kept threads and the developer's record."

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
    implementation(libs.kotlinx.coroutines.core)
    // Android ships org.json.
    compileOnly(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}

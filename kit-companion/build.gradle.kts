import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Deciding what to say now: rules in order, each behind gates the app opens and closes
//. Plain JVM: the engine holds no values, the app's rules and situation do.
// Published with the other modules.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "Deciding what to say now (rules in order, each behind gates the app opens and closes), and the developer's record of a session. Requires org.json on the classpath for the record."

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
    // The session record writes JSON lines with org.json, which Android ships; others add it.
    compileOnly(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}

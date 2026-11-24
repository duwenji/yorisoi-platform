// たっぷりプラン on Google Play's billing library (platform API-AI-RELAY R-15〜R-21). It adds the BILLING
// permission to the app, so only an app that sells the plan uses it. Published with the other modules.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "たっぷりプラン: the subscription's offers, the purchase flow and the purchase sent to the relay."

android {
    namespace = "io.github.duwenji.yorisoi.kit.billing"
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
    // The purchase goes to the relay (RelayClient.sendPlan) and back as PlanPurchases.
    api(project(":kit-llm"))
    api(libs.billing.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

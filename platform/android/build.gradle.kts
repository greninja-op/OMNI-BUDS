import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Version-catalog reads are hoisted to file scope: inside the `android { }`
// receiver the `libs` accessor does not resolve to the version catalog.
val omniBudsCompileSdk = libs.versions.androidCompileSdk.get().toInt()
val omniBudsMinSdk = libs.versions.androidMinSdk.get().toInt()

// The Android platform boundary. Phase 1 deliberately contains no Android source
// here: no Bluetooth, no permissions, no UI, no notifications, no Quick Settings.
// The module exists so that the boundary is real and compilable before Phase 2
// puts platform code behind it (execution prompt sections 7, 33, 51, 52).
android {
    namespace = "com.omnibuds.android"
    compileSdk = omniBudsCompileSdk

    defaultConfig {
        minSdk = omniBudsMinSdk
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // The platform module may depend on core abstractions. Core must never depend
    // back on this module.
    api(project(":core"))
}

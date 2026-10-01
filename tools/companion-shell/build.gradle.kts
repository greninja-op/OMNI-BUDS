import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Debug-only harness target for the device bridge (ADR-P2-010). This module is NOT the OmniBuds
// product: it has no dependency on :core or :platform:android, no business logic, and no release
// variant. Its whole purpose is to give the bridge something installable, foregroundable and
// inspectable, so that deploy, screen capture, UI-hierarchy extraction and coordinate input are
// verified against a real device instead of being declared working (Phase 2 prompt section 52 keeps
// product UI out until Phase 49).
//
// Deliberately no UI framework dependency: plain framework Views and programmatic layout, so this
// module adds nothing to the project's dependency surface (ADR-P1-011).
// Catalog reads happen at file scope: inside the `android { }` receiver `libs` does not resolve to
// the version catalog (learned the hard way in Phase 1).
val shellCompileSdk = libs.versions.androidCompileSdk.get().toInt()
val shellMinSdk = libs.versions.androidMinSdk.get().toInt()
val shellTargetSdk = libs.versions.androidTargetSdk.get().toInt()

android {
    namespace = "com.omnibuds.tools.shell"
    compileSdk = shellCompileSdk

    defaultConfig {
        applicationId = "com.omnibuds.tools.shell"
        minSdk = shellMinSdk
        targetSdk = shellTargetSdk
        versionCode = 1
        versionName = "phase-2-harness"
    }

    buildTypes {
        debug {
            isDebuggable = true
            // No obfuscation, no minification, no signing config beyond the debug default: nothing
            // here is meant to ship, and a release variant would imply a distribution that does not
            // exist.
            isMinifyEnabled = false
        }
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

// No release variant is configured, and none is built: `assembleDebug` is the only artifact the
// bridge consumes, and a release build here would imply a distribution that does not exist.
// (AGP's beforeVariants() API was tried to hard-disable it; it does not resolve in this Kotlin DSL
// configuration, so the guarantee is expressed by the bridge only ever asking for debug.)

dependencies {
    // None. The harness must not import product code: if it did, a harness screenshot could be
    // mistaken for evidence about OmniBuds behaviour.
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Version-catalog reads are hoisted to file scope: inside the `android { }`
// receiver the `libs` accessor does not resolve to the version catalog.
val omniBudsCompileSdk = libs.versions.androidCompileSdk.get().toInt()
val omniBudsMinSdk = libs.versions.androidMinSdk.get().toInt()

// The Android platform boundary. Phase 1 created it source-free so the boundary was
// real and compilable before any platform code existed; Phase 2 puts the Bluetooth
// mechanism behind it (ADR-P2-001). What is still deliberately absent is everything
// Phase 2 does not authorise: no GATT or RFCOMM traffic, no discovery, no UI, no
// notifications, no Quick Settings, and no manifest permission (ADR-P2-011). The
// absence is machine-checked by DependencyDirectionTest rather than by this comment.
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

    // Plain JVM unit tests, no device and no instrumentation. Only the seam-bearing
    // classes are tested this way: the ones that hold framework calls (the receiver
    // adapter, the system probes) are exercised on physical hardware instead, and
    // docs/phases/phase-2/validation.md records which is which.
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

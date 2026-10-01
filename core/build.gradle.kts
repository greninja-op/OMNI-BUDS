import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Platform-independent domain. This module must never depend on Android, a
// database implementation, a UI toolkit or any vendor protocol code. The rule is
// machine-checked by com.omnibuds.core.architecture.DependencyDirectionTest.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Quality baseline (execution prompt section 35): warnings are errors, so
        // no unchecked deprecation or nullable-discipline slip can be merged.
        allWarningsAsErrors.set(true)
        javaParameters.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // ADR-P1-021 removed this while nothing used it. Phase 2 restores it on the stated
    // trigger: adapter-state observation is a Flow surface (ADR-P2-003), so the library
    // now has a reason. Suspending contracts alone would still need no dependency.
    api(libs.kotlinx.coroutines.core)

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

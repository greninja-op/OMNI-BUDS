pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OmniBuds"

// Core is platform-independent Kotlin/JVM. It must never gain an Android
// dependency; docs/phases/phase-0/architecture-governance.md and the
// architecture tests in :core enforce this.
include(":core")

// Android platform boundary. Library module only — no application shell and no
// UI exists in Phase 1 (Phase 1 execution prompt sections 7 and 52).
include(":platform:android")

// Harness target, not the product. Phase 2 prompt section 52 and ADR-P1-001 keep UI out of the
// product until Phase 49; the device-bridge harness (ADR-P2-010) needs an installable artifact to
// verify deploy, capture, hierarchy extraction and input against, so that artifact lives here as a
// debug-only debuggable shell with no product code and no release variant.
include(":tools:companion-shell")

// Desktop platform application (Phase 48). Desktop application shell, presentation layer,
// desktop storage, and UI toolkit boundaries.
include(":platform:desktop")


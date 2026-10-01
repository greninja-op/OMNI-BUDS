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

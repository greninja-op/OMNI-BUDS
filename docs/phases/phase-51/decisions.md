# Phase 51 — Architecture & Engineering Decisions

## DEC-P51-001: Centralized Domain-Level Application Versioning
- **Context**: The project spans multiple modules (`:core`, `:platform:android`, `:platform:desktop`, `:tools:companion-shell`). Hardcoding version literals in build scripts or presentation screens leads to version divergence.
- **Decision**: Define `com.omnibuds.core.release.ApplicationVersion` at Layer 0 of `:core`.
- **Consequences**: Provides a single authoritative reference for `versionName` ("1.0.0") and monotonic `versionCode` (1000000). The domain separates the application version from protocol schema versions, community SDK versions, and preference schema versions.

---

## DEC-P51-002: Dual-Track Build Infrastructure (Gradle Wrapper & Toolchain Runner)
- **Context**: The sandbox environment restricts Gradle daemon socket communications (`Could not receive a message from the daemon`) and lacks public internet access for dynamic dependency downloads, while local toolchains (`kotlinc` 2.0.21, OpenJDK 17, AAPT2, D8, ZipAlign, APKSigner) and pinned repository JARs are completely functional.
- **Decision**: Provide both standard Gradle project configuration (`build.gradle.kts`, `settings.gradle.kts`, version catalogs) for standard CI/developer machines, and a deterministic offline toolchain release build script (`scripts/release_build.sh`) that directly executes the compilers and packaging utilities.
- **Consequences**: Guarantees that release candidates can be built and verified offline, repeatably, and deterministically inside the container while preserving standard Gradle conventions for CI runners.

---

## DEC-P51-003: Safe Android Signing with Graceful Secret Absence
- **Context**: Committing signing keys, passwords, or fabricating self-signed production certificates presents severe security risks and risks losing a genuine production identity.
- **Decision**: Android signing in `scripts/release_build.sh` is entirely environment-driven (`RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_PASSWORD`). If any secret is missing or the keystore file does not exist, the build gracefully outputs an unsigned, aligned APK and logs `NO_SIGNING_KEYS`. The release manifest records the signed artifact as `BLOCKED_NO_KEYS`.
- **Consequences**: Prevents accidental leakage of secrets, prevents build crashes in untrusted environments, and clearly labels unsigned artifacts so they can never be mistaken for production-signed packages.

---

## DEC-P51-004: Standalone Desktop Packaging for Linux x64
- **Context**: Desktop targets run on diverse platforms. The current verified build environment is Linux x86_64 with OpenJDK 17.
- **Decision**: Package both a universal runnable desktop JAR (`omnibuds-desktop-1.0.0.jar`) with `Main-Class` manifest attributes, and a self-contained distribution tarball (`omnibuds-desktop-1.0.0-linux-x64.tar.gz`) containing bundled runtime libraries and a dedicated startup launcher script (`bin/omnibuds-desktop`).
- **Consequences**: Provides immediate, ready-to-run desktop distribution artifacts on Linux without relying on unavailable native packaging tools (e.g. WiX for Windows or pkgbuild for macOS), while honestly documenting unbuilt platform installers as pending platform-specific runners.

---

## DEC-P51-005: Deferral of Physical Hardware Verification to Phase 52
- **Context**: OmniBuds' core mission is controlling physical earbuds and headphones without simulating hardware capabilities.
- **Decision**: The release manifest and release gates explicitly record `physicalHardwareVerification: DEFERRED_PHASE_52`. Phase 51 delivers packaging, reproducibility, checksums, and CI workflows; Phase 52 retains exclusive authority over final hardware testing, QA clearance, and store publication decisions.
- **Consequences**: Eliminates false claims of production readiness based solely on clean builds.

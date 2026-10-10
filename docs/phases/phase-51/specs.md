# Phase 51 — Technical Specifications & Schemas

## 1. Version Metadata Schema

Defined in `com.omnibuds.core.release.ApplicationVersion`:

```kotlin
data class ApplicationVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
    val buildNumber: Int? = null,
) {
    val versionName: String
    val versionCode: Int
}
```

### Constraints:
- `major`, `minor`, `patch` must be non-negative integers.
- `versionCode` formula: `(major * 1_000_000) + (minor * 10_000) + (patch * 100) + (buildNumber ?: 0)`.
- Valid range: `1 <= versionCode <= 2,100,000,000` (within standard Android 32-bit signed integer boundary).

---

## 2. Release Manifest Schema (JSON)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "OmniBudsReleaseManifest",
  "type": "object",
  "required": ["schemaVersion", "productName", "applicationVersion", "versionCode", "gitRevision", "buildTimestamp", "artifacts", "releaseGates"],
  "properties": {
    "schemaVersion": { "type": "integer", "enum": [1] },
    "productName": { "type": "string" },
    "applicationVersion": { "type": "string" },
    "versionCode": { "type": "integer" },
    "gitRevision": { "type": "string" },
    "buildTimestamp": { "type": "string", "format": "date-time" },
    "artifacts": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["productName", "applicationVersion", "platform", "architecture", "filename", "format", "sizeBytes", "sha256Checksum", "gitRevision", "buildTask", "buildTimestamp", "signingStatus", "validationStatus"],
        "properties": {
          "productName": { "type": "string" },
          "applicationVersion": { "type": "string" },
          "platform": { "type": "string" },
          "architecture": { "type": "string" },
          "filename": { "type": "string" },
          "format": { "type": "string" },
          "sizeBytes": { "type": "integer", "minimum": 1 },
          "sha256Checksum": { "type": "string", "pattern": "^[a-fA-F0-9]{64}$" },
          "gitRevision": { "type": "string" },
          "buildTask": { "type": "string" },
          "buildTimestamp": { "type": "string" },
          "signingStatus": { "type": "string", "enum": ["UNSIGNED", "DEBUG_SIGNED", "RELEASE_SIGNED", "BLOCKED_NO_KEYS"] },
          "validationStatus": { "type": "string" }
        }
      }
    },
    "releaseGates": {
      "type": "object",
      "required": ["allTestsPassed", "architectureValid", "hardwareTruthPreserved", "physicalHardwareVerification", "productionSigning"]
    }
  }
}
```

---

## 3. Environment Variable Contracts

| Variable | Description | Security Classification | Required For |
|---|---|---|---|
| `JAVA_BIN` | Path to `java` binary | Public / Toolchain | Release Build Script |
| `KOTLINC` | Path to `kotlinc` compiler | Public / Toolchain | Release Build Script |
| `ANDROID_JAR` | Path to Android API 35 SDK | Public / Toolchain | Android Packaging |
| `AAPT2` | Path to `aapt2` build-tools | Public / Toolchain | Resource Compilation |
| `D8` | Path to `d8` DEX compiler | Public / Toolchain | APK Compilation |
| `ZIPALIGN` | Path to `zipalign` utility | Public / Toolchain | APK Optimization |
| `APKSIGNER` | Path to `apksigner` tool | Public / Toolchain | APK Signing |
| `RELEASE_KEYSTORE_PATH` | File path to release keystore | Sensitive / Secret | Signed Release APK |
| `RELEASE_KEYSTORE_PASSWORD` | Release keystore password | Secret Credential | Signed Release APK |
| `RELEASE_KEY_PASSWORD` | Release key password | Secret Credential | Signed Release APK |

---

## 4. Release Task Specifications

- **Build Task**: `bash scripts/release_build.sh`
- **Output Directory**: `build/release-dist/`
- **Artifact Naming Conventions**:
  - `omnibuds-core-${VERSION_NAME}.jar`
  - `omnibuds-desktop-${VERSION_NAME}.jar`
  - `omnibuds-desktop-${VERSION_NAME}-linux-x64.tar.gz`
  - `omnibuds-android-${VERSION_NAME}.aar`
  - `omnibuds-companion-shell-${VERSION_NAME}-unsigned.apk`
  - `omnibuds-companion-shell-${VERSION_NAME}-signed.apk` (if keystore provided)
  - `CHECKSUMS.sha256`
  - `release-manifest.json`

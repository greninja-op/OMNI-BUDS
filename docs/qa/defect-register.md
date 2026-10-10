# OmniBuds — Defect Register (Phase 52)

## 1. Defect Classification & Severity Policy

- **Critical**: Remote code execution, arbitrary memory corruption, secret leakage, hardware damage risk, or silent fake state generation. (Blocks release).
- **High**: Major functional failure, crash during normal operation, incorrect security policy enforcement, or malformed artifact packaging. (Blocks general distribution).
- **Medium**: Platform API limitations, edge-case protocol incompatibility, missing non-critical diagnostic field. (Documented under Known Limitations or patched).
- **Low**: Minor styling, documentation typo, non-functional permission scope boundary. (Documented; does not block software candidate).

---

## 2. Defect Register

### DEF-52-001: GitHub App Installation Lacks `workflow` Scope for Workflow Push
- **Defect ID**: `DEF-52-001`
- **Severity**: Low (Infrastructure / Permission Boundary)
- **Affected Platform**: Infrastructure / CI/CD
- **Affected Module**: `.github/workflows/release-verification.yml`
- **Reproduction Steps**:
  1. Commit `.github/workflows/release-verification.yml`.
  2. Attempt to push commit to remote repository using GitHub App token.
- **Expected Behavior**: Commit pushed successfully to `origin/main`.
- **Actual Behavior**: HTTP 403 Forbidden returned by GitHub API: `refusing to allow an OAuth App to create or update workflow without 'workflow' scope`.
- **Evidence**: Phase 51 git push log; untracked `.github/` folder in working tree.
- **Root Cause**: GitHub App installation credentials intentionally restricted to code repository read/write without workflow administrative mutation rights.
- **Proposed Remediation**: Document permission boundary. Workflow file remains in working tree; push will be executed via separate administrative PAT or authorized GitHub App installation. Do not perform insecure workarounds like relocating workflow files to documentation folders.
- **Resolution Status**: **ACCEPTED_LIMITATION**
- **Verification Result**: Audited; does not compromise code or artifact integrity.

---

### DEF-52-002: Missing Physical Test Environment for Hardware-in-the-Loop Validation
- **Defect ID**: `DEF-52-002`
- **Severity**: High (Release Gate Blocker for Physical Device Claims)
- **Affected Platform**: Hardware / HIL
- **Affected Module**: All vendor adapters (`:core` / physical hardware)
- **Reproduction Steps**:
  1. Inspect Linux host environment for Bluetooth controllers (`/sys/class/bluetooth`).
  2. Attempt discovery of physical Apple AirPods, Sony, Bose, or Pixel Buds peripherals.
- **Expected Behavior**: Physical Bluetooth controller discovers paired test devices and executes HIL 10-step protocol.
- **Actual Behavior**: Host environment is a headless cloud container without physical Bluetooth radios or peripherals.
- **Evidence**: Host system audit: 0 HCI devices, 0 physical earbuds attached.
- **Root Cause**: Physical test environment not provisioned in headless cloud build sandbox.
- **Proposed Remediation**: Enforce Fundamental Principle of Hardware Truth: evaluate hardware gates as `NOT VERIFIED (BLOCKED_NO_ENVIRONMENT)`. Software candidate is approved as RC1 (Conditional Go); physical verification deferred to dedicated hardware lab.
- **Resolution Status**: **ACCEPTED_LIMITATION** (Release Condition)
- **Verification Result**: Honestly reported; 0 fake test results fabricated.

---

### DEF-52-003: Public Android APIs Prohibit Direct Codec Selection and Bluetooth Battery Reading
- **Defect ID**: `DEF-52-003`
- **Severity**: Medium (Platform API Constraint)
- **Affected Platform**: Android
- **Affected Module**: `platform/android/src/main/kotlin/com/omnibuds/android/bluetooth/audio`
- **Reproduction Steps**:
  1. Attempt to switch A2DP codec (e.g. SBC to LDAC) via public Android `BluetoothAdapter` / `BluetoothDevice` APIs on unrooted Android device.
  2. Query battery percentage of connected classic Bluetooth device via standard public SDK APIs.
- **Expected Behavior**: Codec switch and battery percentage directly controlled and observed.
- **Actual Behavior**: Android SDK marks codec configuration methods as `@SystemApi` / hidden (`BluetoothA2dp.setCodecConfigPreference()`), requiring signature permissions (`MODIFY_PHONE_STATE`).
- **Evidence**: `AndroidCodecControlAdapter.kt` and `AndroidBatteryObservationSource.kt` implementation.
- **Root Cause**: Android OS platform security architecture restricts Bluetooth audio routing and codec negotiation to system settings / system apps.
- **Proposed Remediation**: OmniBuds adapters honestly report `CodecObservability.NOT_OBSERVABLE`, `CodecControlCapability.NOT_SELECTABLE`, and battery level `Unknown` rather than fabricating values or crashing.
- **Resolution Status**: **RESOLVED** (Designed & Tested Architectural Behavior)
- **Verification Result**: Verified by `AndroidCodecControlAdapterTest.kt` and `AndroidBatteryObservationSourceTest.kt`.

---

### DEF-52-004: Absence of Private Production Release Keystore in Sandbox
- **Defect ID**: `DEF-52-004`
- **Severity**: Medium (Packaging Condition)
- **Affected Platform**: Android / Release Engineering
- **Affected Module**: `scripts/release_build.sh`
- **Reproduction Steps**:
  1. Execute `scripts/release_build.sh` without setting `RELEASE_KEYSTORE_PATH`.
- **Expected Behavior**: Signed release APK generated with production certificate.
- **Actual Behavior**: Build script safely blocks signed APK generation, logs `NO_SIGNING_KEYS`, and outputs unsigned companion shell APK.
- **Evidence**: `release-manifest.json` specifies `"productionSigning": "BLOCKED_NO_KEYS"`.
- **Root Cause**: Production signing keystores and passwords are appropriately withheld from developer sandbox environments to prevent key leakage.
- **Proposed Remediation**: Unsigned artifacts marked `UNSIGNED`; release gate evaluated as `BLOCKED_NO_KEYS`. Production signing performed in hardened release pipeline.
- **Resolution Status**: **RESOLVED** (Secure Standard Procedure)
- **Verification Result**: Verified in `release-manifest.json` and build log.

# Device access policy

Adopted 2026-10-01 (Phase 2). Binding on every phase that uses a physical device, and on every agent working on this project.

## The device is personal, not a lab instrument

The user develops on their own Android phone, connected over USB/ADB with developer options enabled. It contains photos, messages, accounts and other applications that have nothing to do with OmniBuds. Device access is therefore scoped to **this application and nothing else**.

## Permitted without further confirmation

| Action | Scope limit |
|---|---|
| `adb install` / `install -r` | only OmniBuds APKs built from this repository |
| `adb uninstall` | only OmniBuds packages, and confirmed before running |
| Instrumented tests | only our own test package |
| `adb logcat` | filtered by our package or PID; other apps' logs are not collected |
| `run-as`, `pm clear` | only our own package |
| Reading app data | only our own `/data/data/<our package>` |
| `adb shell getprop`, `dumpsys` for build/API/Bluetooth adapter facts | read-only, and no personal data is captured |

## Requires asking the user first, every time

Anything outside the table above, including and especially:

- `adb pull` or `adb push` of any path that is not our app's own directory
- browsing, listing or copying external storage (`/sdcard/…`), photos, downloads, DCIM
- `run-as`, `pm clear`, backup or data extraction involving **another** package
- reading another app's logs, databases, shared preferences or files
- `adb backup`, `adb extract`, filesystem traversal of the device
- changing device settings: developer options, animation scales, location mode, Wi-Fi, airplane mode, battery optimisation, granted permissions we did not request through our own UI
- screen recording, scrcpy, `screencap` beyond our own app's window when it would capture other apps' content
- reboot, safe mode, bootloader/Fastboot, factory reset, partition writes
- installing or updating anything on the device that is not our APK

## Consequences for how tests are written

Because the device is personal and app-scoped:

1. **Instrumented tests must be self-contained.** They create the state they need and never read pre-existing user data, and they must not require granting a permission by shell command when the app's own flow cannot request it.
2. **No test, log or diagnostic artifact may be captured from another application.** Packet logs and Bluetooth captures in particular can contain identifiers of devices the user owns outside this project.
3. **Diagnostics exports stay app-scoped** and are reviewed by the user before leaving the device (`docs/phases/phase-0/security-governance.md` SEC-LOG, SEC-PRIV).
4. **A capability that would need out-of-scope access is not built.** If a future phase needs something on the device that this policy forbids, the phase reports the conflict instead of working around it — the same discipline that stops OmniBuds from faking a hardware capability.

## Device work is user-directed, not orchestrator-initiated

**Standing rule added 2026-10-01.** A device is attached to this workstation (Xiaomi 2311DRK48I,
Android 14, API 34, arm64-v8a, authorised over USB). The user has instructed that no device
operation is to be attempted until they supply their own connection instructions, and that only
those instructions are to be followed.

Concretely, until the user's instructions arrive: no installs, no `adb reverse`, no
`dumpsys bluetooth_manager`, no instrumented-test harness or `androidx.test` dependencies, no
instrumented runs, and no writes of any kind to the device.

What has actually been done on the device, and nothing more: two targeted `getprop` reads
returning non-personal build facts (SDK level, release, model, manufacturer, ABI). Those facts are
recorded here because they are what the API-level permission bands will be tested against.

## Status of device validation in Phase 2

No device was attached when Phase 2 was implemented and validated (`adb devices` returned no entries, and no AVD exists). Phase 2 therefore validates adapter, permission, lifecycle and error behavior as JVM unit tests against fake framework seams, and the Android glue in `:platform:android` is compiled and lint-checked only.

Device validation is a scheduled, separate step: when the user connects their phone, install and run the instrumented suite and record what the real adapter and permission state were — and treat any device-level observation not produced by that run as `UNKNOWN` rather than inferred. Until that happens, no Phase 2 statement about real Android behavior is verified.

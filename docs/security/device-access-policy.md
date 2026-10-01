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

**Those instructions did arrive on 2026-10-01**, as two documents the user supplied: the local-first
companion-bridge specification and the ADB deployment audit. They authorise a bounded set of
actions - build, install, verify, launch, and later capture/input - and the audit added the
restrictions that now bound them: no `adb uninstall` as troubleshooting, no `install -g`, no
automatic permission grants, no developer-option changes, no deleting application data, and no
claim of success a check did not produce.

What has actually been done on the device, and nothing more:

- targeted `getprop` reads returning non-personal build facts (SDK level, release, model,
  manufacturer, ABI, HyperOS build, USB mode), recorded because they are what the API-level
  permission bands will be tested against;
- `adb install` of our own debug APK, including the controlled attempts that reproduced the
  refusal, and one successful install plus one successful `-r` reinstall of the same package;
- `pm list packages` / `pm path` **for our own package only**, `cmd package resolve-activity` and
  `am start` **for our own activity**, and `pidof` for our own package;
- `dumpsys window` to read which activity holds focus. This is the one command whose output names
  other packages - the previous focus was a system media app - and it is read for that single
  field, which is the foreground gate capture and input depend on;
- `adb devices -l` and `adb version`, host-side.

Not done, at any point: `adb uninstall`, `pm clear`, any `pm grant` or revocation, any change to
developer options or security settings, any screen capture, any injected input, any read of
another app's files or databases, `adb reverse`, `dumpsys bluetooth_manager`, or an instrumented
test run. Capture and input remain unexercised because the foreground gate is the only permission
this policy grants for them, and the deployment work had no reason to cross it.

## Status of device validation in Phase 2

**No device was attached at Phase 2's close-out** (`adb devices -l` returned no entries), and no AVD exists. That is separate from the deployment work earlier the same day, which did run against the attached phone and is recorded in `docs/development/adb-deployment/validation.md`. What matters for this phase is that neither event overlaps: **no Phase 2 Bluetooth class has ever executed on a device.** Phase 2 validates adapter, permission, lifecycle and error behavior as JVM unit tests against fake framework seams, and the Android glue in `:platform:android` is compiled and lint-checked only. The installable module that did reach the phone is the debug-only companion shell, which depends on neither `:core` nor `:platform:android`, so a green deployment run is not evidence about the Bluetooth layer (ADR-P2-010).

Device validation is a scheduled, separate step: when the user connects their phone, install and run the instrumented suite and record what the real adapter and permission state were — and treat any device-level observation not produced by that run as `UNKNOWN` rather than inferred. Until that happens, no Phase 2 statement about real Android behavior is verified.

## Phase 3: instrumented verification, authorised and bounded

On 2026-10-01 the user chose **instrumented tests on the phone** as Phase 3's verification route (ADR-P3-007), which supersedes the "no instrumented test run" line above for this phase only. The authorisation is narrow and is written here before the first run, not after it.

**What may be installed.** Only `:platform:android`'s own `androidTest` APK — our package, addressed as `com.omnibuds.android` with instrumentation in its `.test` package. No other APK, no third-party test harness, nothing that carries a `tools:` namespace into the product build.

**What it reads, and why that is personal.** The phone's own adapter state and permission standings, and as Phase 3 grows, the bonded-device list and per-profile connected-device reports. That is the user's Bluetooth history: which earbuds they own, which they pair with, and identifiers that are stable across reconnects. The suite is therefore built to assert on **counts, states, categories and shapes** — never names, never addresses — and `BluetoothInstrumentedSmokeTest.assertNoAddressLeak` makes the identifier rule enforceable rather than aspirational, because a rule that only exists in prose is the one that breaks first.

**Diagnostics are treated as data.** Instrumented output leaves the phone through logcat and the generated XML results, which are files in this repository's build directories. Nothing may print a device name or address into either, and a test that needs to name a device to debug it has to fail on shape instead.

**Permission posture.** The suite never calls any request API and grants nothing (ADR-P3-007's authoring rule, ADR-P3-009's standing-before-looking ordering, and Phase 3 prompt section 13). If the instrumentation package does not hold `BLUETOOTH_CONNECT`, the refusal is recorded as the finding it is — the platform then answers "empty", which is exactly the confusion ADR-P3-009 is designed to expose rather than fall into. Granting that permission to our own test package would be a device write and needs the user's explicit say-so at the time, like everything else in the "requires asking" list above.

**One deliberate exposure, stated.** Phase 3's connection receivers register with `RECEIVER_EXPORTED` from API 33, because Bluetooth announcements arrive from the stack's UID and an un-exported filter may never receive them (ADR-P3-011, settled by ADR-P3-013). The sender is a named system component and the alternative is a silent failure, which this policy treats as the worse of the two. Phase 2's opposite choice for adapter-state broadcasts **was** rewritten once the documentation was read (ADR-P3-013): the boundary module now registers its receivers exported, and what remains for the deferred device session is confirming that announcements actually arrive under the documented rule - a falsifiable check, not an open choice.

**What a green run does and does not mean.** It is evidence about one Xiaomi/POCO handset on one HyperOS build. Prompt section 19's own rule applies: it does not establish universal behavior, and any claim that survives it must say which phone it was seen on.

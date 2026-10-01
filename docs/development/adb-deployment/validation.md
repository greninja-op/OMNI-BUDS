# ADB deployment — validation

Recorded 2026-10-01 on the development machine. Two kinds of result are kept strictly apart:
**host-side**, produced against a scripted fake ADB with no device present, and **physical**,
produced by running commands against the connected phone. Nothing here is inferred from a Gradle
success.

## Device used

| Property | Value | Source |
|---|---|---|
| Serial | `8TCABAIFWOZTDICI` | `adb devices -l` |
| Model / product | `2311DRK48I` / `duchamp_in` | `getprop` |
| Manufacturer | Xiaomi | `getprop` |
| Android / API | 14 / 34 | `getprop` |
| MIUI/HyperOS | `V816`, incremental `V816.0.8.0.UNLINXM` | `getprop` |
| ABI | `arm64-v8a` | `getprop ro.product.cpu.abi` |
| USB mode | `mtp,adb`, `adbd` running | `getprop` |
| Uptime at test | 2 days 12 h | `uptime` |
| ADB | `1.0.41`, platform-tools `37.0.1-15733141`, single server on 127.0.0.1:5037 | `adb version`, port owner |

## Physical device results

| Stage | Result | Evidence actually observed |
|---|---|---|
| BUILD | **verified** | `./gradlew :tools:companion-shell:assembleDebug` → BUILD SUCCESSFUL; APK present, 817,289 bytes |
| APK_VALIDATED | **verified** | package id read back from the artifact; `minSdkVersion` 26 against device API 34 |
| DEVICE_RESOLVED | **verified** | exactly one ready device, state `device`, explicit serial used |
| INSTALL (first time, no `-r`) | **verified success** | `Performing Streamed Install` → `Success` |
| INSTALL (`-r`, absent package) | **verified refusal** | `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`, twice (attempts 1 and 2 in `troubleshooting.md`) |
| INSTALL (`-r`, package present) | **verified success** | `Success`; the reinstall path that preserves data |
| PACKAGE_VERIFIED | **verified** | `pm list packages` lists `com.omnibuds.tools.shell`; `pm path` returns a `/data/app/~~…/base.apk` path |
| LAUNCH | **verified** | activity resolved by `cmd package resolve-activity` → `com.omnibuds.tools.shell/.ShellActivity`; `am start` started it |
| PROCESS_ALIVE | **verified** | `pidof` returned `8703`; `dumpsys window` `mCurrentFocus` moved from `com.google.android.youtube` to `com.omnibuds.tools.shell/…ShellActivity` |
| Data preservation | **not verified** | the app had no persisted state to preserve; no `pm clear` or `uninstall` was issued at any point, which is the only part that can be asserted |
| Screen capture | **not exercised** | capture was deliberately not run; the foreground happened to be ours after launch, but no capture was attempted in this session |
| Hierarchy / input | **not exercised** | `uiautomator dump` and `input tap` were not run; `USB debugging (Security settings)` state was not checked, so a tap may be silently dropped on this OEM build |
| WebSocket gateway | **not exercised against a device** | the harness app has no in-app client yet; localhost serving is covered only by host-side tests |

## Host-side results

`python -m unittest tools.device-bridge.tests.test_deploy` — **30 tests, 0 failures, 0 errors** (re-measured 2026-10-01 after the pipeline was reconciled with the shared transport). They cover: the
absent-package install issuing no `-r` and no `-g`; the present-package reinstall using `-r`; a
rejection never being followed by `uninstall` or `pm clear`; unscripted commands raising rather than
being absorbed; ambiguous and unauthorised device selection stopping the pipeline before contact;
missing, empty, mismatched, unparseable and SDK-incompatible APKs; activity resolution failing
rather than assuming a name; `am start` error text not counting as a launch; a dead process not
counting as success; a build failure producing no device command at all; and error classification
leaving unseen codes unclassified.

The whole bridge suite — `python -m unittest discover -s tools/device-bridge/tests` — measures
**416 tests, 0 failures, 0 errors, 1 skipped** on the same date. That suite belongs to the
companion-bridge workstream, which is still being authored in the same working tree; the one skip is
`test_readme_is_ascii_too`, which waits for a README the other workstream has not written yet. A
green run there is evidence about the harness, not about the pipeline's physical behaviour, which is
what the table above is for.

The pipeline gained a ninth stage since the first version of this record: after a successful launch
it reads the foreground package and refuses to proceed if it is not ours. Capture and input stay
gated behind that check, so a run that lands on somebody else's screen stops with
`NOT_FOREGROUND` instead of tapping it. That refusal is asserted by
`test_a_launch_that_does_not_reach_the_foreground_is_not_verified` in `test_deploy.py`.

## Security constraints confirmed

No device setting was changed and no developer option toggled. No app was uninstalled and no data
cleared. No permission was granted to any package. No screen capture was taken or saved. The only
device writes in this session were installing our own debug APK and launching our own activity — the
actions the deployment task authorises. The running IDE's ADB server was neither killed nor
restarted.

## Overall

The audit objective is met: the harness's install strategy is corrected, the failing flag
identified, the pipeline implemented with per-stage verification, and the build-install-launch path
proven on the physical device. The capture, input and streaming legs of the bridge remain unverified
on hardware, and are listed as such rather than assumed working.

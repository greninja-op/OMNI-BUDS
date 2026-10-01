# ADB deployment — test plan

Two categories, kept apart deliberately: **host** tests run against a scripted fake ADB with no
device attached and are part of normal verification; **physical** checks need the phone and are
recorded in `validation.md` with exactly what was and was not exercised. A passing host test proves
the harness handles a response. It never proves the device produces it.

The fake transport is strict in both directions: an unexpected command raises, and a script with no
remaining answer raises. A silent extra call — an uninstall, a permission grant, a blind retry —
therefore fails a test instead of passing quietly.

## Host tests — `tools/device-bridge/tests/test_deploy.py` (23 cases)

### Install flag selection (the audited defect)

| ID | Case | Expected |
|---|---|---|
| DEP-01 | package absent | `install` is issued with **no** `-r` and **no** `-g` |
| DEP-02 | package present | `install -r`, no `-g`, `install_flags == ("-r",)` |
| DEP-03 | install rejected | no `uninstall`, no `pm clear`, note records that no destructive recovery was attempted |
| DEP-04 | manager attempts an unscripted command | test raises; a missing `pidof` answer must not be invented |

### Device resolution

| ID | Case | Expected |
|---|---|---|
| DEP-10 | no devices | `NO_DEVICES`, pipeline stops |
| DEP-11 | two ready devices, no serial | `AMBIGUOUS_DEVICE`, stops before any install |
| DEP-12 | explicit serial unauthorised | `UNAUTHORIZED`, not substituted by another device |
| DEP-13 | explicit serial present and ready | that device is chosen although another is listed |
| DEP-14 | `adb version` fails | `ADB_INVALID`; the non-ADB executable is not silently replaced |

### APK validation

| ID | Case | Expected |
|---|---|---|
| DEP-20 | file missing | `APK_MISSING` |
| DEP-21 | zero-byte file | `APK_EMPTY` |
| DEP-22 | package id differs | `PACKAGE_MISMATCH`, and no install command is issued |
| DEP-23 | `minSdkVersion` above device API | `SDK_INCOMPATIBLE` |
| DEP-24 | metadata unreadable (no aapt) | `APK_INVALID`, not assumed compatible |

### Launch and stage reporting

| ID | Case | Expected |
|---|---|---|
| DEP-30 | clean run | all seven stages recorded, in order, all true; pid and resolved activity present |
| DEP-31 | no launcher activity resolves | `NO_LAUNCH_ACTIVITY`; no `am start` issued (name is never guessed) |
| DEP-32 | `am start` prints an error | `LAUNCH_FAILED`, `succeeded` false |
| DEP-33 | `pidof` empty | `PROCESS_NOT_RUNNING`, `succeeded` false |
| DEP-34 | build fails | `BUILD_FAILED`, and no device command at all |

### Error classification

| ID | Case | Expected |
|---|---|---|
| DEP-40 | `INSTALL_FAILED_USER_RESTRICTED` | named exactly, and gated as non-destructive |
| DEP-41 | `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `INSTALL_SIGNATURE_MISMATCH`, in `DESTRUCTIVE_RECOVERY_REQUIRED` |
| DEP-42 | unknown failure code | `INSTALL_FAILED` — unclassified, not forced into a known category |
| DEP-43 | `cmd: Failure ... Broken pipe` | `INSTALL_TRANSPORT_FAILED`, distinct from a policy refusal |

## Physical checks requiring the device

| ID | What it proves | Status |
|---|---|---|
| PHY-01 | build → APK present on disk | **verified** this session |
| PHY-02 | install of an absent package without `-r` | **verified** — `Success` |
| PHY-03 | `-r` on an absent package is refused | **verified** — reproduced twice |
| PHY-04 | `-r` on a present package preserves the install | **verified** — `Success` |
| PHY-05 | package and path listed after install | **verified** |
| PHY-06 | activity resolved and started; process alive; focus moved to us | **verified** |
| PHY-07 | data preservation across `-r` | **not verified** — the harness app holds no persisted state to compare |
| PHY-08 | foreground-gated screencap into the ring buffer | **not exercised** |
| PHY-09 | capture refused when another app is foreground | **not exercised** |
| PHY-10 | `uiautomator dump` parse against the real hierarchy | **not exercised** |
| PHY-11 | `input tap` landing (HyperOS may drop it) | **not exercised** |
| PHY-12 | disconnect mid-install classification | **not exercised** |
| PHY-13 | localhost WebSocket handshake with an in-app client | **not exercised** — the shell has no client yet |
| PHY-14 | wireless debugging path | **not exercised** |

PHY-08 through PHY-11 are the reason the bridge cannot be called verified end to end; they are
listed rather than assumed, and the deployment task is complete only for the stages it actually ran.

## How to run

```bash
python tools/device-bridge/tests/test_deploy.py            # host only, no device
python -m unittest discover -s tools/device-bridge/tests   # whole bridge suite
```

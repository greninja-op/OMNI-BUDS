# ADB deployment — setup

Machine-specific paths are supplied by the environment and are never committed. Nothing in this
file should be pasted into a tracked source file.

## Prerequisites on this project

| Requirement | Where it comes from | Verified on the development machine |
|---|---|---|
| JDK 17 | user-level install, selected per invocation via `JAVA_HOME` | yes — 17.0.20, not on `PATH` |
| Android SDK with platform 35, build-tools 35.0.0 | `local.properties` (`sdk.dir`), git-ignored | yes |
| `aapt` or `aapt2` for APK metadata | `$SDK/build-tools/<version>/` | yes (`aapt.exe`, `aapt2.exe`, `apksigner.bat`) |
| ADB | `$SDK/platform-tools/adb.exe` | yes — `1.0.41`, platform-tools `37.0.1-15733141` |
| Python 3 (standard library only) | host | yes; no pip installs required |

## Selecting the ADB executable

`DeploymentManager` takes an explicit `adb_path`. Resolution order for anything that has to discover
it:

1. an explicitly configured path (argument or environment override);
2. `ANDROID_HOME` / `ANDROID_SDK_ROOT` → `platform-tools/adb`;
3. `PATH`.

The chosen executable is then **validated by running `adb version`** and requiring the expected
banner; a path that is not ADB is reported (`ADB_INVALID`) rather than silently replaced with a
different installation. Two competing ADB servers on port 5037 cause confusing "offline" devices,
so the harness reuses whatever server already owns the port instead of restarting one, and never
kills another tool's server.

## Device target

```bash
adb devices -l          # read-only inventory
```

With more than one entry, pass the serial explicitly. The harness will not pick a device for you.

## Build and deploy

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew :tools:companion-shell:assembleDebug
python -m bridge.deploy          # or drive DeploymentManager.deploy() from a script
```

Artifacts land in `tools/companion-shell/build/outputs/apk/debug/`, which is git-ignored. The APK
path is resolved from the build, not assumed from a convention.

## What must never be done here

- Do not add `install -g`, or any code path that grants all runtime permissions.
- Do not run `adb uninstall` or `pm clear` to make an installation succeed.
- Do not write phone settings, toggle developer options, or attempt to dismiss an on-device
  confirmation programmatically.
- Do not store screen captures in the repository; the ring buffer is in memory and the capture
  paths are git-ignored.
- Do not add a third-party ADB or WebSocket dependency; the bridge is standard library only.

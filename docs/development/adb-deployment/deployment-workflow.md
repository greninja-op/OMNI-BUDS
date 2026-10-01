# ADB deployment — workflow

The order below is what `DeploymentManager.deploy()` enforces. Each stage is verified before it is
reported, and the pipeline stops at the first unverified stage rather than continuing on assumptions.

## 1. Resolve the toolchain

Pick the ADB executable explicitly and validate it with `adb version`. Reuse any ADB server already
owning port 5037; never start a competing one and never terminate another tool's server.

## 2. Build

Run the repository's actual Gradle task for the target module, then resolve the produced artifact
path from the build output rather than assuming a conventional location. A build failure produces no
device command whatsoever.

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew :tools:companion-shell:assembleDebug
```

## 3. Resolve the device

`adb devices -l`, then: zero ready devices, an unauthorised or offline target, or several ready
devices with no explicit serial, all end the run with a named failure. Selection is never guessed.

## 4. Read device facts

Targeted `getprop` reads only — API level, ABI, model, manufacturer. No settings are written and no
developer option is touched.

## 5. Validate the APK

Read the package id and `minSdkVersion` with `aapt`/`aapt2`. Refuse on: missing file, empty file,
unparseable archive, package id that differs from the expected one, or a `minSdkVersion` above the
device's API level. Unreadable metadata is a failure, not a pass.

## 6. Install, using the flags the device state justifies

```text
package absent   ->  adb -s SERIAL install APK
package present  ->  adb -s SERIAL install -r APK
```

Never `-g`. Never `uninstall`, never `pm clear`, never a blind retry after a rejection. A rejection
is classified; a rejection that could only be cleared by removing the install is returned for a human
decision with the reason.

## 7. Verify the package

`pm list packages` must list the package, and `pm path` must return an install path. An install that
reported success but is not present is a failure, not a success.

## 8. Resolve the launch activity, then start it

`cmd package resolve-activity -c android.intent.category.LAUNCHER --brief PACKAGE` — the name is
looked up, never assumed from convention. Then `am start -n <resolved>`. Any `Error` text in the
output is a launch failure.

## 9. Confirm the process

`pidof PACKAGE` must return a live pid. Optionally confirm `mCurrentFocus` moved to the package,
which is also the precondition for any later capture.

## 10. Report

```text
BUILD_SUCCESS
DEVICE_RESOLVED_SUCCESS
APK_VALIDATION_SUCCESS
INSTALL_SUCCESS
PACKAGE_VERIFIED_SUCCESS
LAUNCH_SUCCESS
PROCESS_ALIVE_SUCCESS
OVERALL=SUCCESS
```

Any earlier stage failing yields the matching `..._FAILED` line, the reason, and
`OVERALL=NOT_VERIFIED`. A stage that was not reached is never reported as passed.

## Capture and input, when they run

Screen capture and UI-hierarchy extraction are a separate step from deployment and are governed by
`docs/security/device-access-policy.md`: our package must be in the foreground, images stay in an
in-memory ring buffer and are never committed, and input actions resolve an element's real bounds
before tapping rather than using remembered coordinates.

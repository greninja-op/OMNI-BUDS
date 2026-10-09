# Phase 32 — Repository Audit

**Date:** 2026-10-09

## Confirmed configuration

| Item | Value |
|---|---|
| Module | `platform/android` (library-style module, no app module) |
| minSdk | 26 |
| compileSdk / targetSdk | 35 |
| Kotlin | 2.0.21 (kotlinc CLI; no Gradle in this environment) |
| JDK | 17 |
| Build | kotlinc CLI against android-35 android.jar; aapt2 for resources |

## Manifest (actual)

- Permissions declared: `BLUETOOTH_CONNECT` only.
- No `POST_NOTIFICATIONS`, no `BLUETOOTH_SCAN`, no location permissions.
- Components: TileService (QS_TILE, BIND_QUICK_SETTINGS_TILE),
  notification receiver (exported=false), widget provider (exported=false).
- No foreground services. No boot receivers. No WorkManager.

## Existing platform code

- `bluetooth/`: adapter observation behind permission checks.
- `notification/`: `NotificationPermissionChecker` (POST_NOTIFICATIONS
  runtime on API 33+, install-time below).
- `tile/`: Quick Settings service shell.
- `widget/`: AppWidgetProvider shell.
- `lifecycle/`: `OmniBudsLifecycleMonitor` (Activity callbacks).

## Test infrastructure

- Phase 30: `core/testkit/` (TestCase, fixtures, ScriptedTransport,
  FailureInjector, evidence).
- Phase 31: `core/testkit/crossdevice/` (profiles, planner, evaluator,
  regression).
- 1648 tests, all JVM (JUnit 5 + kotlin.test). No Robolectric, no
  emulator, no instrumentation in this environment.

## Gaps identified

1. No pure API-level decision module — platform branches are inline.
2. No permission-state model distinguishing denied/unavailable/
   restricted/unknown.
3. No Android-version compatibility matrix document.
4. No permission matrix document.
5. OEM behavior undocumented.

## Files likely to change

- New: `platform/android/src/main/kotlin/com/omnibuds/android/compat/`
- New tests in `platform/android/src/test/`
- Docs: `docs/phases/phase-32/`

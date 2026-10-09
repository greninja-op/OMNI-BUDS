# Phase 32 — Android Version Matrix

## Supported range

minSdk 26 → targetSdk 35. Only API 26–35 are in scope.

## Boundaries

| API | Change | OmniBuds handling | Test |
|---|---|---|---|
| 26 | Notification channels required | Channels created (Phase 26) | Review |
| 26 | Background execution limits | No background services; Phase 28 | Review |
| 29 | Foreground-service types introduced | No foreground service used | ApiLevelPolicyTest |
| 29 | Background activity-start restriction | No background activity starts | ApiLevelPolicyTest |
| 31 | BLUETOOTH_SCAN/CONNECT runtime | Permission-gated (manifest declares CONNECT) | ApiLevelPolicyTest, PermissionPolicyTest |
| 31 | Exact-alarm restrictions | No exact alarms used | ApiLevelPolicyTest |
| 31 | Widget preview | Optional; not required | ApiLevelPolicyTest |
| 33 | POST_NOTIFICATIONS runtime | Checked via NotificationPermissionChecker; not declared (no notification posting in library scope) | ApiLevelPolicyTest |
| 34 | Foreground-service types enforced | N/A — no service | ApiLevelPolicyTest |

## Not tested

- Emulator/SDK images: unavailable in this environment — marked NOT_RUN.
- OEM behavior: UNVERIFIED (see oem-compatibility-notes.md).
- Physical hardware: reserved for the hardware-testing phase.

## Claim discipline

A passing JVM test proves the decision logic, not that the Android
Bluetooth stack behaves identically on every device.

# ADB deployment — architecture

The deployment harness lives in `tools/device-bridge/` and is deliberately outside `:core` and
`:platform:android`. It is development tooling: nothing it can do is evidence about OmniBuds
functionality, and nothing in the product depends on it.

## Components

```text
tools/device-bridge/
├── bridge/
│   ├── adb.py            ADB command construction, device listing, transports
│   ├── deploy.py         the staged deployment pipeline (this document's subject)
│   ├── apk_info.py       package/min-SDK metadata via aapt/aapt2 when available
│   ├── capture.py        foreground-gated screen capture, bounded in-memory ring buffer
│   ├── hierarchy.py      uiautomator XML to typed elements + centre resolution
│   ├── input_actions.py  tap/text/keyevent, gated on the same foreground rule
│   ├── envelope.py       AgentEnvelope validation, sequence tracking, replay buffer
│   ├── ws_server.py      RFC 6455 server, localhost bind, token auth, 512 KiB cap
│   ├── gateway.py        ingestion, subscription, reconnect policy
│   └── errors.py         typed failures; no failure degrades into a default value
└── tests/                stdlib unittest, scripted fake ADB, no device attached
```

## Pipeline stages

`deploy.DeploymentManager.deploy()` records exactly these stages, in this order, and reports a
stage as succeeded only when a command confirmed it:

```text
BUILD → DEVICE_RESOLVED → APK_VALIDATED → INSTALL → PACKAGE_VERIFIED → LAUNCH → PROCESS_ALIVE
```

`DeployReport.succeeded` requires all seven. A run that stops at stage three reports
`FAILURE=...` plus the stages it reached; it never reports partial success as success, which is the
whole point of the stage list.

## Design commitments

- **Observed state chooses the install flags.** `-r` is passed only when `pm list packages` shows
  the package already installed. This is not cosmetics: on the development phone, `-r` for an absent
  package is the exact condition the installer rejects (see `troubleshooting.md`).
- **Explicit device selection.** No serial plus multiple ready devices is `AMBIGUOUS_DEVICE`, not a
  choice. A given serial that is absent, unauthorised or offline is reported as such rather than
  substituted with another phone.
- **Validation before installation, with tools that exist.** Package id and `minSdkVersion` are read
  from the APK with `aapt`/`aapt2` discovered through `ANDROID_HOME`/`ANDROID_SDK_ROOT`. When the
  metadata cannot be read the stage fails `APK_INVALID` — an unreadable APK is not assumed
  compatible.
- **No destructive recovery, ever, on a hunch.** The pipeline never runs `adb uninstall` or
  `pm clear`. Failures that would be "fixed" by removing the install are classified and returned for
  a human decision (`DESTRUCTIVE_RECOVERY_REQUIRED`).
- **No blanket permission grant.** `-g` is not used and there is no code path that adds it; granting
  is a separate, named, per-permission call.
- **Injected runner.** Every external effect goes through a `runner(argv, timeout)` callable, so the
  whole pipeline is exercised in tests with a scripted fake and no device present.

## Relationship to the product

The bridge talks to `:tools:companion-shell` (ADR-P2-010), which depends on no product module. A
successful deploy, capture or tap therefore proves the harness works and proves nothing about
Bluetooth, capability discovery or control. `docs/security/device-access-policy.md` governs what the
harness may observe on the device, and capture remains foreground-gated and unpersisted.

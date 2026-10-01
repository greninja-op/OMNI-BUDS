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

## What `deploy.py` shares with the bridge transport, and what it does not

Both live in `tools/device-bridge/bridge/`, and both can reach the phone, so the boundary between
them is a safety property rather than a tidiness one.

Shared, and therefore impossible for the deployment pipeline to get wrong independently:

- `build_install_argv` - the only construction of an install command in the package, and it cannot
  emit `-g` (its predecessor, an inline list, could).
- `AdbDeviceRecord`, `AdbDeviceState` and `parse_device_listing` - so `unauthorized` and `offline`
  mean the same thing to the gateway and to the deployment manager, including an unrecognised token
  staying `UNKNOWN` with its raw text instead of defaulting to a state with a remedy.
- `parse_sdk_value`, `parse_focus_package`, `CommandRunner`, `CommandResult` and
  `subprocess_command_runner` - one timeout contract, one "no shell", one decode rule.

Not shared, on purpose:

- **`AdbTransport` itself.** It is a per-device object (`AdbTransport(serial=...)`) that answers
  policy questions by raising typed errors. The deployment manager's contract is the opposite: no
  stage throws, every refusal becomes a named `Failure` and a note a human can act on, and a
  missing fact stays `None` so the pipeline can say "API level unknown" instead of aborting.
  Wrapping a raising transport in a classification pipeline would mean catching everything it raises
  and re-deriving the classification `classify_failure` already owns.
- **`AdbTransport.preflight`.** It reads five build properties and refuses a device whose ABI it
  cannot parse. Deploy needs one property and must keep going when it is absent, because an APK
  compatibility judgement against an unknown API level is reported as `SDK_INCOMPATIBLE`-adjacent
  uncertainty, not as a device error. Reusing it would change how many commands reach the phone per
  run, which is exactly the kind of thing this project keeps explicit.
- **The one-line `_device_argv` wrapper.** Its shape (`adb -s <serial> ...`) is duplicated; the
  closed-transport guard and the raising accessors around it are not wanted here. If a later change
  adds a third consumer of that shape, the right move is an unaddressed free function in `adb.py`
  that both call - not a transport constructed with a placeholder serial just to list devices.

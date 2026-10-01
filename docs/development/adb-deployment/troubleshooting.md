# ADB deployment — troubleshooting

## The reported failure

```text
adb install -r tools/companion-shell/build/outputs/apk/debug/companion-shell-debug.apk
  Performing Streamed Install
  adb.exe: failed to install ...: Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]
```

## What the investigation found

**Root cause, established by reproduction on the actual device:** the harness passed `-r` for a
package that was not installed. On this HyperOS build the reinstall path for an absent package is
rejected with `INSTALL_FAILED_USER_RESTRICTED`, while a plain first-time install of the same APK is
accepted, and `-r` afterwards succeeds normally. The flag was the defect; the phone was not.

Sequence of controlled observations (same APK, same device, same ADB):

| # | Command | Package state before | Result |
|---|---|---|---|
| 1 | `install -r` (streamed) | absent | `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` |
| 2 | `install --no-stream -r` | absent | server-side `PackageManagerShellCommand` stack trace; install did not happen |
| 3 | `install` (no flags) | absent | `Success` |
| 4 | `install -r` | present | `Success` (non-destructive reinstall) |

Between attempts 1 and 3 the foreground window was sampled before and after: it did not change, so
**no on-device confirmation dialog was ever displayed**. That detail matters because the failure text
says "Install canceled by user", which reads like a declined prompt. It is not one here. An early
hypothesis in this project — "enable Install via USB" — was wrong and is corrected by this evidence.

## Ruled out, with the evidence used

| Hypothesis | Evidence against it |
|---|---|
| Phone security configuration is wrong | The device is a working Android development target: same serial, `device` state, and a plain install succeeded in under a second. |
| Different ADB binary or platform-tools version than the other IDE | Only one `adb.exe` exists in the searched install roots, at `.../Android/Sdk/platform-tools`, version `1.0.41` / platform-tools `37.0.1-15733141`. |
| Competing ADB servers | Port 5037 is held by exactly one process (the same `adb.exe`). No server was restarted or killed. |
| Signature conflict with an existing app | The package was not installed at all (`pm list packages` had no entry), so there was no prior signing identity to conflict with. |
| `unauthorized` / offline device | `adb devices -l` reports `device`; `adb get-state` succeeded. |
| An unreadable or malformed APK | The same file installed successfully with a plain `install`, and `aapt`/`aapt2` report it as a valid package with `minSdkVersion` 26 on a device reporting API 34. |

## Fix applied in the harness

`bridge/deploy.py` derives install flags from observed state: absent package → `install`;
present package → `install -r`. It never passes `-g`, never uninstalls, never clears data, and does
not retry blindly after a rejection. The rejection is classified as
`Failure.INSTALL_USER_RESTRICTED` with a note that no destructive recovery was attempted.

## If installation fails again

Read the stage first; `DeployReport` says which one failed.

| Stage and code | Meaning | Safe next action |
|---|---|---|
| `INSTALL` + `INSTALL_USER_RESTRICTED` while the package is **absent** | the reinstall-flag condition above | install without `-r`; do not toggle anything on the phone |
| `INSTALL` + `INSTALL_USER_RESTRICTED` while the package **is present** | genuine device policy or a pending on-device confirmation | check the phone screen manually; do not automate or dismiss it |
| `INSTALL` + `INSTALL_SIGNATURE_MISMATCH` | the installed app was signed differently | a human decision: replacing an install removes its data; the harness will not do it |
| `INSTALL` + `SDK_INCOMPATIBLE` | APK `minSdkVersion` above the device API | build for the intended floor; never `--force` |
| `INSTALL_TRANSPORT_FAILED` | server-side/pipe failure rather than a policy refusal | re-run once; capture the stack trace; do not switch flags blindly |
| `DEVICE_RESOLVED` + `AMBIGUOUS_DEVICE` | more than one ready target | pass an explicit serial |
| `PROCESS_NOT_RUNNING` | launched but no process | inspect `am start` output and logcat for the crash |

## Still unresolved, stated as unresolved

- **Why the reinstall path for an absent package is refused has not been established from a primary
  source.** The behaviour is reproduced and the safe command is known; the mechanism is inferred
  from a single device and would need either HyperOS documentation or a second device to confirm.
- **Whether the IDE's installs differ only by flags was not directly observed.** No other ADB
  binary or log was inspected, and the IDE's own session state was deliberately left alone. The
  conclusion rests on the flag differential above, not on reading its configuration.
- **This is one device, one cable, one HyperOS build (`V816.0.8.0.UNLINXM`).** No claim is made about
  other Xiaomi/POCO versions, other OEMs, or wireless debugging.

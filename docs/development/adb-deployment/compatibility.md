# ADB deployment — compatibility notes

Every statement here names the device it was observed on. Nothing is extrapolated to other
manufacturers, Android versions or connection modes, because the one lesson from this audit is that
a plausible generalisation about installer behaviour is exactly what cost the time.

## Tested configuration

| Item | Value |
|---|---|
| Device | Xiaomi/POCO `2311DRK48I` (`duchamp_in`), HyperOS `V816.0.8.0.UNLINXM` |
| Android / API | 14 / 34, `arm64-v8a` |
| Transport | USB, `sys.usb.state = mtp,adb`, `adbd` running |
| ADB | 1.0.41, platform-tools `37.0.1-15733141` |
| Host | Windows 10 (10.0.26200), Git Bash, JDK 17.0.20 at a user-level path |
| App under test | `com.omnibuds.tools.shell`, `minSdkVersion` 26, debug build, unsigned by any release key |

## Observed per-configuration behaviour

- **`install` on an absent package:** accepted.
- **`install -r` on an absent package:** rejected with `INSTALL_FAILED_USER_RESTRICTED`, no
  on-device prompt shown.
- **`install -r` on a present package:** accepted, and the normal way to update without losing data.
- **`install --no-stream -r`:** server-side stack trace from `PackageManagerShellCommand`; treated as
  a transport failure, distinct from a policy refusal.
- **`pm path`, `pm list packages`, `cmd package resolve-activity`, `am start`, `pidof`,
  `dumpsys window`:** all behave as documented on stock Android; no OEM divergence seen for these.
- **Wireless debugging:** not tested. Do not assume the USB findings transfer.

## Known OEM-specific risks not yet characterised

1. **Simulated input may be blocked independently of install policy.** HyperOS builds commonly gate
   `input tap`/`input text` behind a separate USB-debugging security setting. Untested here, so the
   bridge must report a dropped input as unverified rather than assuming it landed.
2. **`dumpsys window` focus text format varies.** `mCurrentFocus` was parseable here; the foreground
   gate should treat an unparseable line as "unknown", refusing capture rather than guessing.
3. **Background-restricted apps can be killed at will by MIUI power management**, which affects any
   long-lived capture or streaming session. Not exercised.
4. **`INSTALL_FAILED_USER_RESTRICTED` is overloaded across HyperOS versions.** The same string can
   mean a genuine policy switch is off. The classifier therefore reports the code and the observed
   package state together, and does not claim to know which.

## Portability requirements for the harness

- No hard-coded personal paths: ADB, SDK and JDK come from configuration or environment.
- No `shell=True` command construction; arguments are passed as lists.
- ADB discovery validates the chosen executable and fails clearly instead of falling through to a
  different installation.
- The pipeline is injectable at the transport boundary, so CI can run the full stage matrix with a
  fake ADB and no device.
- Standard library only: no pip-installed dependency, no vendored third-party client.

## Explicitly not claimed

No claim is made that this works on Samsung, Pixel, other Xiaomi builds, other Android versions, other
cables or hubs, wireless debugging, or any device with a different installer policy. Only one phone
was used.

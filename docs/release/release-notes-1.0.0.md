# OmniBuds v1.0.0-RC1 Release Notes

## Overview

OmniBuds is an open-source, universal Bluetooth earbuds and headphones hardware-control platform designed for Android and Desktop environments. Built with a shared pure Kotlin architecture (`:core`), OmniBuds provides vendor-agnostic capability discovery, protocol encoding/decoding, battery observation, and acoustic profile reporting without requiring proprietary OEM companion bloatware.

---

## What's Included in v1.0.0-RC1

### 1. Shared Kotlin Core (`:core`)
- Universal capability discovery engine with fine-grained evidence levels (`INFERRED`, `IMPLEMENTED`, `LAB_TESTED`, `HARDWARE_VERIFIED`, `PERSISTENCE_VERIFIED`).
- Protocol packet decoders and command builders for Apple AirPods, Sony WH/WF series, Bose QuietComfort, and Google Pixel Buds.
- Dynamic dependency validation engine preventing invalid feature states or concurrent conflicting commands.
- Battery conflict resolution engine handling disparate left/right earbud and charging case states.
- Localized diagnostic snapshot capture with automated MAC address and sensitive identifier redaction (`LogRedactor`).
- Default-deny security policy ensuring unrecognized devices remain strictly read-only.

### 2. Android Platform Integration (`:platform:android`)
- High-performance Android Archive Library (`omnibuds-android-1.0.0.aar`) targeting Android API 35 (minimum API 26).
- Android Bluetooth LE and Classic transport handles with automatic lifecycle recovery across process death.
- Integrated Quick Settings Tile (`OmniBudsTileService`) and Home Screen Widgets (`OmniBudsWidgetProvider`).
- Foreground notification coordinator with strict PendingIntent security.

### 3. Desktop Platform Integration (`:platform:desktop`)
- Native Linux desktop distribution tarball (`omnibuds-desktop-1.0.0-linux-x64.tar.gz`) with executable launcher.
- Pure JVM standalone JAR (`omnibuds-desktop-1.0.0.jar`) with headless and Swing presentation adapters.
- Multi-device management supporting isolated concurrent device sessions.

---

## Distribution Packages

| Package | Platform | Format | Size | SHA-256 Checksum |
|---|---|---|---|---|
| `omnibuds-core-1.0.0.jar` | Core JVM | JAR | 2.8 MB | `beaa941dce9b1e41d534a47eb01ec9e3114602d0b673611ec669e6164ac626e4` |
| `omnibuds-desktop-1.0.0.jar` | Desktop JVM | JAR | 3.0 MB | `ec6dc2791946b43c26e8a73ead853ba143b809cb24eaf7b661e9395816986b81` |
| `omnibuds-desktop-1.0.0-linux-x64.tar.gz` | Linux x64 | Tarball | 5.6 MB | `5f443b5764ed0ba76ac5e322600ef0c89257d2e04aa90d349d871c69b9238a10` |
| `omnibuds-android-1.0.0.aar` | Android Library | AAR | 693 KB | `23cddb98323e0dc11ad06efc0994aab7b685ba81c0027368700b5dc323609731` |
| `omnibuds-companion-shell-1.0.0-unsigned.apk` | Companion Shell | APK | 5.1 KB | `8e5b9bd31309284753d4c7e9c2e5b8bbdfefa5d314f14860af09d812dff407d0` |

---

## Verification & Quality Status

- **Automated Regression Suite**: 2,056 tests executed across `:core`, `:platform:android`, and `:platform:desktop` — **100% Passed**.
- **Software Release Gates**: 13 / 13 passed.
- **Signing Status**: Safely blocked in sandbox without private keys (`BLOCKED_NO_KEYS`).
- **Hardware Verification Status**: Documented as `NOT VERIFIED (BLOCKED_NO_ENVIRONMENT)` pending physical hardware deployment.

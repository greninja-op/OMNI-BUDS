# OmniBuds

> **OmniBuds controls actual device capabilities and does not simulate unsupported hardware functionality.**

A universal hardware-control platform for Bluetooth earbuds and headphones: it discovers, verifies and controls the capabilities a connected device actually implements, regardless of manufacturer, so one app can replace a drawer full of brand-specific ones.

Primary platform Android. Language Kotlin. Architecture Kotlin-Multiplatform-ready.

## Current state — read this before judging anything else

**Phase 2 is complete. Nothing user-facing exists yet.**

| | |
|---|---|
| What is built | Module and build foundation; the platform-independent domain contracts (device identity, fingerprint, session, capability states, codec state, audio transport state, structured errors, protocol and transport contracts, persistence contracts); and Phase 2's Bluetooth foundation: the phone's adapter can be inspected and its state observed, permission requirements are resolved per `targetSdkVersion` with the standing read from the platform, the phone's own capability set is reported with OS availability kept apart from hardware evidence, transport boundaries exist as contracts, and the Android mechanism behind the boundary is real code in `platform/android/`. |
| What is **not** built | Any contact with a headset: device detection, paired-device lists, GATT, RFCOMM, LE Audio, codec control, ANC, transparency, EQ, gestures, battery reading, vendor protocols, storage, notifications, Quick Settings, widgets, and all product UI. Also absent on purpose: the app declares **no Bluetooth permission at all** and requests none, because Phase 2 needs none to inspect the phone's own adapter. |
| Devices supported | **None.** No earbud or headphone has been tested, and no Phase 2 class has run on a phone either — `platform/android` is a library with nothing installed to host it. Every statement in this repository about hardware behaviour is a rule, not a verified capability. |
| Test status | 353 JVM unit tests in `:core` and 45 in `:platform:android`, 0 failures. All run against scripted seams; none touches a radio, so none can justify a `HARDWARE_VERIFIED` claim. |
| Next phase | Phase 3 — device discovery. It has not started and will not begin without an explicit instruction, and it additionally needs a decision about which module hosts the Bluetooth layer on a device. |

Development is deliberately **backend-first**: the capability and state model is finished before any screen exists, so the UI can only ever show what discovery actually established.

## The rule that shapes everything

A capability is never reported as available unless the device proves it. The state model makes that mechanical rather than aspirational:

```text
UNKNOWN          not discovered, or not determinable from here
UNSUPPORTED      positively established that the device does not implement it
READ_ONLY        the device reports it but does not accept changes
SUPPORTED_VOLATILE   settable, but the setting does not survive reconnect
SUPPORTED_PERSISTENT settable and read back as applied
PERSISTENCE_VERIFIED proven to survive a real disconnect/reconnect
```

`UNKNOWN` is never written as `UNSUPPORTED`, a successful write never by itself proves persistence, and an unmeasured value stays unknown rather than becoming `0`, `false` or a plausible-looking number. Codec state follows the same discipline: a headset that supports LDAC while AAC is running is reported as exactly that.

## Audio path isolation

OmniBuds is not an audio player and stays outside the media path. Android owns media transport; OmniBuds attaches a control and configuration channel alongside it.

```text
Music app → Android audio stack → Bluetooth transport → device
                                         ↑
                              OmniBuds: control/config only
```

Capturing, processing, re-encoding and re-transmitting audio would degrade quality, latency, codec negotiation, battery and stability — and would be a software imitation of a hardware feature.

## Vendor protocol architecture

"Universal" does not mean the lowest common denominator.

- No transport is assumed. GATT, RFCOMM/SPP, classic, LE Audio and vendor-specific channels all have to be expressible.
- The protocol contract is narrow (`identify`, `discoverCapabilities`, `readState`); everything else is an optional capability interface a vendor may or may not implement, so a device without gesture support does not fake one.
- Common features get a common, brand-neutral identity (`noise-control.anc`); unique features live under `vendor.<vendor>.<feature>` and stay addressable instead of being discarded.
- Protocol commands are structured definitions with recorded evidence, never scattered hex literals or magic UUIDs.

## Layout

```text
core/                platform-independent Kotlin/JVM domain — no Android; kotlinx-coroutines is its one production dependency (ADR-P2-003)
platform/android/    Android boundary — the Bluetooth mechanism: adapter handle and state receiver,
                     permission and capability readers, the composed platform and a manual
                     composition root. Declares no permission and no component (ADR-P2-011).
tools/companion-shell/  debug-only, dependency-free harness target the device bridge inspects (ADR-P2-010)
tools/device-bridge/    local-first ADB deployment and bridge harness — Python standard library only
docs/                master contract, governance rulebook, per-phase records, templates
```

`core` is enforced, not trusted: an architecture test suite fails the build if Android or JVM-only packages are imported into it, if dependencies point upward, if a stub or a test double appears in production source, or if magic protocol literals show up in code. The same suite now bounds the Android side too: it refuses capabilities Phase 2 was not given (GATT or RFCOMM traffic, discovery, UI, Quick Settings, widgets, the media-audio path), confines broadcast reception to the adapter boundary, keeps platform sources inside the authorised packages, and reads the manifest to check it declares nothing unjustified.

## Building

Requires JDK 17 and the Android SDK (platform 35). Gradle comes from the wrapper.

```bash
export JAVA_HOME=/path/to/jdk-17     # not written into any committed file
./gradlew build                      # compiles every module and runs all tests
./gradlew :core:test                 # domain and architecture tests only
./gradlew :platform:android:test     # the Android mechanism, as JVM tests against scripted seams
./gradlew :platform:android:lintDebug  # static analysis of the boundary module
```

Create a git-ignored `local.properties` containing `sdk.dir=<path to your Android SDK>` (see `local.properties.example`). Nothing machine-specific is committed, and no user-level Gradle or JDK configuration is modified by this project.

For an automated one-step clean rebuild and self-check, run `rebuild.bat` (Windows) or `rebuild.sh` (macOS/Linux). For full instructions, see [REBUILD.md](REBUILD.md).

## Documentation

| Path | Contents |
|---|---|
| `docs/MASTER-CONTEXT.md` | Master source of truth: product principles, architecture, 52-phase roadmap |
| `docs/README.md` | Documentation map and authority order |
| `docs/phases/phase-0/` | Engineering contract, governance rulebooks, ADRs, risk register |
| `docs/phases/phase-1/` | Project foundation: requirements, design, specs, tasks, tests, ADRs, validation |
| `docs/phases/phase-2/` | Android Bluetooth foundation: the same record set, plus the API research it was built from |
| `docs/development/adb-deployment/` | The local ADB deployment harness: pipeline, compatibility findings, troubleshooting and its own validation record |
| `docs/templates/` | The document set every future phase copies from |

Work proceeds phase by phase, each started only by an explicit instruction, each stopping at its boundary and reporting what it did and did not prove.

# OmniBuds

<img width="1254" height="1254" alt="image" src="https://github.com/user-attachments/assets/a1a3b59f-ad00-43a8-ab1d-73066f74a4d1" />

> **OmniBuds controls actual device capabilities and does not simulate unsupported hardware functionality.**

A universal hardware-control platform for Bluetooth earbuds and headphones: it discovers, verifies and controls the capabilities a connected device actually implements, regardless of manufacturer, so one app can replace a drawer full of brand-specific ones.

Primary platform Android. Language Kotlin. Architecture Kotlin-Multiplatform-ready.

## Current state — read this before judging anything else

**Phases 0–11 are complete. Nothing user-facing exists yet.**

| | |
|---|---|
| What is built | Module and build foundation; the platform-independent domain contracts (device identity, fingerprint, session, capability states, codec state, audio transport state, structured errors, protocol and transport contracts, persistence contracts); the Android Bluetooth foundation (adapter inspection, version-aware permissions, platform capability reporting, transport boundaries); connected-device observation (link/bond/availability vocabulary, profile-union reconciliation, paired census); device sessions; device fingerprinting and identification; the Bluetooth transport layer (GATT/RFCOMM operation surfaces behind a framework-free seam); the protocol abstraction engine; the capability discovery engine; the hardware feature engine (six-state control machine, 10-step validator, standard feature catalogue); and the **audio transport engine** — observation-only A2DP/HFP/HSP/LE Audio state with a 7-state connection vocabulary, audio-device observation separate from Bluetooth identity, a pure-function reconciler, a lifecycle-safe engine, API-33-guarded LE Audio, and audio/control plane separation; and the **codec capability engine** — extending the codec vocabulary with evidence/confidence/observability models, nullable metadata, per-device immutable snapshots, staleness handling, and an API-35-isolated Android adapter that honestly reports the active codec as NOT_OBSERVABLE (no public API exposes it). No audio is captured, no codec is configured or switched, no routing is changed. |
| What is **not** built | Codec discovery and control, audio capture or processing of any kind, production UI, notifications, widgets, and any contact with real headset hardware beyond observation contracts. The app still declares no unjustified permissions and requests none for observation. |
| Devices supported | **None tested.** No earbud or headphone has been tested on hardware, and no platform class has run on a phone — `platform/android` is a library with nothing installed to host it. Every statement in this repository about hardware behaviour is a rule, not a verified capability. |
| Test status | 944 JVM unit tests — 816 in `:core`, 128 in `:platform:android` — 0 failures. All run against scripted seams; none touches a radio, so none can justify a `HARDWARE_VERIFIED` claim. |
| Next phase | Phase 12 — codec configuration. It has not started and will not begin without an explicit instruction. |

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

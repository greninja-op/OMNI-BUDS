# OmniBuds

> **OmniBuds controls actual device capabilities and does not simulate unsupported hardware functionality.**

A universal hardware-control platform for Bluetooth earbuds and headphones: it discovers, verifies and controls the capabilities a connected device actually implements, regardless of manufacturer, so one app can replace a drawer full of brand-specific ones.

Primary platform Android. Language Kotlin. Architecture Kotlin-Multiplatform-ready.

## Current state — read this before judging anything else

**Phase 1 is complete. Nothing user-facing exists yet.**

| | |
|---|---|
| What is built | Module and build foundation, and the platform-independent domain contracts: device identity, fingerprint, session, capability states, codec state, audio transport state, structured errors, protocol and transport contracts, persistence contracts. |
| What is **not** built | Bluetooth of any kind, device detection, GATT, RFCOMM, LE Audio, codec control, ANC, transparency, EQ, gestures, battery reading, vendor protocols, storage, notifications, Quick Settings, widgets, and all UI. |
| Devices supported | **None.** No earbud, headphone or phone has been tested. Every statement in this repository about hardware behavior is a rule, not a verified capability. |
| Test status | 302 JVM unit tests, 0 failures. All are domain and architecture tests; none touches hardware, so none can justify a `HARDWARE_VERIFIED` claim. |
| Next phase | Phase 2 — Android Bluetooth Foundation. It has not started and will not begin without an explicit instruction. |

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
core/              platform-independent Kotlin/JVM domain — no Android, no dependencies
platform/android/  Android boundary — currently an empty, source-free library module
docs/              master contract, governance rulebook, per-phase records, templates
```

`core` is enforced, not trusted: an architecture test suite fails the build if Android or JVM-only packages are imported into it, if dependencies point upward, if a stub or a test double appears in production source, or if magic protocol literals show up in code.

## Building

Requires JDK 17 and the Android SDK (platform 35). Gradle comes from the wrapper.

```bash
export JAVA_HOME=/path/to/jdk-17     # not written into any committed file
./gradlew build                      # compiles both modules and runs all tests
./gradlew :core:test                 # domain and architecture tests only
```

Create a git-ignored `local.properties` containing `sdk.dir=<path to your Android SDK>`. Nothing machine-specific is committed, and no user-level Gradle or JDK configuration is modified by this project.

## Documentation

| Path | Contents |
|---|---|
| `docs/MASTER-CONTEXT.md` | Master source of truth: product principles, architecture, 52-phase roadmap |
| `docs/README.md` | Documentation map and authority order |
| `docs/phases/phase-0/` | Engineering contract, governance rulebooks, ADRs, risk register |
| `docs/phases/phase-1/` | This phase: requirements, design, specs, tasks, tests, ADRs, validation |
| `docs/templates/` | The document set every future phase copies from |

Work proceeds phase by phase, each started only by an explicit instruction, each stopping at its boundary and reporting what it did and did not prove.

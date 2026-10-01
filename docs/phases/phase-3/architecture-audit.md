# Phase 3 — Pre-Execution Architecture Audit

**Phase:** 3 — Connected Device Detection · **Scope id:** `P3` · **Written by:** the orchestrator
**Method.** Every statement below was produced by reading a file or running a command on this
workstation on 2026-10-01, after Phase 2 closed at `01b8b09`. Nothing here is recalled from a
previous session, and nothing here is inferred from what the architecture is *supposed* to be.
Where a question cannot be settled from the repository, it is listed in §9 as a dependency on the
connection-observation research rather than answered with a plausible guess.

**Prompt section 2 answer, up front: Phase 2 is complete and is not a blocker.** Every Phase 2
contract Phase 3 needs exists and is tested. What Phase 2 left open is recorded in §3 with its
actual status, and one of its open items — which module hosts Bluetooth on a device — was resolved
by the user during this audit (§4).

---

## 1. What is actually in the repository

| Thing | Measured state |
|---|---|
| Git | branch `main`, HEAD `01b8b09`, in sync with `origin/main`. Untracked and deliberately uncommitted: the whole of `tools/device-bridge/` except `bridge/deploy.py` and `tests/test_deploy.py`, which belong to a concurrent workstream whose agent ended on a turn limit. 21 files, suite green at 419 tests. Phase 3 does not touch them. |
| Modules | `:core` (Kotlin/JVM), `:platform:android` (AGP library), `:tools:companion-shell` (debug-only harness app). Declared in `settings.gradle.kts`; `FAIL_ON_PROJECT_REPOS` is on. |
| Core areas (11 registered + `platform`) | `common`/`state` (0), `transport`/`platform` (1), `device`/`capability`/`audio`/`config`/`diagnostics` (2), `session`/`persistence` (3), `protocol` (4). Enforced by `DependencyDirectionTest.areaLayer` (`core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt:33-48`). |
| Size | `:core` 96 main / 47 test files · `:platform:android` 13 main / 5 test files · 356 core + 45 platform JVM tests, 0 failures |
| Android boundary | `com.omnibuds.android.bluetooth.{adapter,permission,capability,mapping}` + `com.omnibuds.android.di`. Manifest declares no permission, no component (`platform/android/src/main/AndroidManifest.xml`), machine-checked. |
| Source sets on the boundary module | `src/main` and `src/test` only. **There is no `src/androidTest`, and `platform/android/build.gradle.kts` declares no `testInstrumentationRunner`.** The instrumented capability the user selected does not exist yet (§4, §8). |
| Dependency cache | `androidx.test*` is **absent** from `~/.gradle/caches/modules-2/files-2.1/`. `google()` was probed this session and returned HTTP 200 for `androidx/test/runner/1.5.2`, so the artifacts are reachable; every build so far has run `--offline` by choice, not necessity. |
| Device | `adb devices -l` → **no device attached** at the time of writing. |

### The seam inventory Phase 3 builds on

| Contract | Location | Why it matters to Phase 3 |
|---|---|---|
| `BluetoothPlatform` | `core/platform/BluetoothPlatform.kt:20-38` | Four questions only, all host-side. Phase 3 adds device-facing questions and must not retrofit them onto this interface's meaning of "no device involved". |
| `AdapterStateSource` / `AdapterStateChangeChannel` / `PlatformRegistration` / `TimeProvider` | `core/platform/` | The proven shape of a platform seam: cold stream, disposable registration, injected clock. Phase 3's connection source should follow it rather than invent a second idiom. |
| `AdapterStateObserver` | `core/platform/AdapterStateObserver.kt` | Single-slot, dedupe, teardown-in-`finally`, recorded `teardownProblem`. It is the precedent for "the engine lives in `:core` so it is testable without a radio". |
| `BluetoothOperation` | `core/platform/BluetoothOperation.kt` | `CONNECTED_DEVICE_INSPECTION`, `BONDED_DEVICE_LIST_INSPECTION` and `PROFILE_CONNECTION_STATE_INSPECTION` already exist with `authorizedInPhase = 3`. Phase 3 is the phase they were pre-computed for. |
| `FrozenPermissionRequirementResolver` | `core/platform/PermissionRequirementResolver.kt` | The Phase 3 rows are already written and tested as *model behaviour*: modern band → `BLUETOOTH_CONNECT`; legacy band → `BLUETOOTH` with the compatibility-guide asymmetry recorded in the row's own reason string. Phase 3 consumes them; it does not re-derive them. |
| `PermissionState` / `PermissionContext` / `ApiAvailability` | `core/platform/` | Standing without prompting, target-vs-device split, and the three-valued availability answer. |
| `OperationOutcome`, `OmniBudsError`, 23 categories | `core/common/` | Including `CONNECTION_UNAVAILABLE`, `RESOURCE_UNAVAILABLE`, `PLATFORM_EXCEPTION`, `PERMISSION_DENIED` — all Phase 2 additions that exist precisely because a connection-layer failure is not a transport failure. |
| `TransportKind` | `core/common/TransportKind.kt` | BLE/GATT/RFCOMM/CLASSIC_BLUETOOTH/LE_AUDIO/VENDOR_SPECIFIC/UNKNOWN — a *channel* axis. |
| `ConnectionState` + `ConnectionStateTransitions` | `core/state/ConnectionState.kt` | See §2.1 — this is the axis that most needs care. |

---

## 2. Two-axis risk: the thing most likely to go wrong in Phase 3

Prompt §6 requires a model distinguishing `BONDED / CONNECTED / DISCONNECTED / CONNECTING /
DISCONNECTING / UNKNOWN / UNAVAILABLE`. The repository already has an enum called `ConnectionState`
with `UNKNOWN, DISCOVERED, PAIRED, CONNECTED, IDENTIFYING, CAPABILITY_DISCOVERY, READY,
CONTROL_SESSION, DISCONNECTED, TEMPORARILY_UNAVAILABLE, ERROR`
(`core/state/ConnectionState.kt:15-27`) plus an enforced transition table (`:40+`).

**These are not the same fact, and merging them is the trap.** `core/state/ConnectionState` is
OmniBuds' *session* position — what work the app has done with a device — and its KDoc says so:
`IDENTIFYING` and `CAPABILITY_DISCOVERY` are "OmniBuds' own work, which is why they sit after
`CONNECTED` rather than before it". Android's connection state is a *platform link* fact reported by
the stack: it knows `CONNECTING` and `DISCONNECTING`, which the session model deliberately does not,
and it knows nothing about `READY`, which the session model is all about.

Consequences this audit records before any code is written:

- Phase 3 introduces a **separate** platform-side connection vocabulary. Reusing `ConnectionState`
  would either add `CONNECTING`/`DISCONNECTING` to a type whose transition table and `isOperational`
  semantics Phase 1 fixed, or lose the intermediate platform states the prompt demands.
- The name must not be `ConnectionState`. Two types with that name in two areas would be the
  "same fact modelled two ways" error Phase 1 prompt §24 and ADR-P1-004 exist to prevent; the
  distinction has to be in the name, not in a package path.
- The mapping from platform connection state into session `ConnectionState` is **not Phase 3's
  job** — it is session management, which prompt §21 explicitly places after the boundary.
  Phase 3 publishes observations; nothing in it may create a `DeviceState`.
- `BONDED` and `CONNECTED` are two fields, not one enum value. A bonded-and-disconnected device is
  the normal case, and prompt §6 makes the distinction mandatory. A single flattened enum would
  have to invent a `BONDED_NOT_CONNECTED` member and then decide, arbitrarily, which of the two
  facts a transition changed.
- `UNAVAILABLE` in §6's list is already an answer in the platform vocabulary
  (`BluetoothAdapterState.UNAVAILABLE`, `ApiAvailability.UNAVAILABLE`). What it means for a *device*
  — "the platform would not tell me about this one" — is a third thing and needs its own name and a
  documented difference from `UNKNOWN`.

### 2.1 The profile axis

`observedProfiles` (§7) is a third axis: per-profile connection state within one device. The existing
`TransportKind` is the wrong vocabulary for it — it names channels, not Android profile services, and
`TransportKindPinningTest` + ADR-P2-013 bind transport boundaries to it. Mapping an Android
`BluetoothProfile` constant onto `TransportKind` would claim, for instance, that observing A2DP
implies something about LE Audio. A distinct platform profile type is required, with its own
documented relationship to `TransportKind` — and `docs/phases/phase-2/transport-boundaries.md` is
where that relationship will have to be recorded so the two axes cannot be silently conflated later.

---

## 3. Phase 2's open items, re-checked rather than remembered

| Item | Status at Phase 3 start | Effect on Phase 3 |
|---|---|---|
| RISK-035 — nothing has executed on Android | **OPEN, and Phase 3 is the phase that closes it.** The user selected instrumented tests (§4). | Directly in scope. |
| RISK-031 — single-slot uniqueness rests on one composition root | OPEN | Phase 3 adds a second observer; the same convention must hold or the risk compounds. |
| RISK-038 — `docs/phases/phase-0/bluetooth-governance.md` is named as a binding rulebook and was never written | OPEN, escalated to the user | Phase 3 is Bluetooth-shaped work with no Bluetooth rulebook. Recorded again in the Phase 3 register; not silently authored here either. |
| RISK-039 — phase records assert things the tree no longer does | Partly closed (three of four instances fixed) | Discipline carried forward: every citation in Phase 3 documents was re-read before commit. |
| RISK-041 — the absent-adapter path is only a scripted answer | OPEN | Instrumented tests address the scripted-ness generally. |
| ADR-P0-018 — research verification-ladder ordering | Still `proposed`, awaiting the user | Blocks Phases 3, 5 and 20 per its own text. **Assessed in §5: not a hard dependency for the observation engine, but it is for any claim about a *research-derived* capability.** |
| Known issue 6 — Kotlin plugin loaded per-subproject | OPEN, unfixed by choice | Adding an `androidTest` source set widens the surface. Noted, not silently repaired. |
| `PhaseTwoScopeTest` | PASSES and **must keep passing** | It asserts what Phase 2 was authorised to do, not what Phase 3 may do. Phase 3 needs its own scope test; editing Phase 2's would erase a guard (ADR-P2-014). |

---

## 4. Resolved during this audit: the device-hosting decision

Phase 2's report left the user one question — which module hosts the Bluetooth layer on a device,
given that `:tools:companion-shell` is forbidden from importing product code (ADR-P2-010) and no
Phase 2 class had ever executed on hardware.

**The user chose instrumented tests on the phone** for Phase 3. That settles three things and opens
two:

- Settled: verification is expected to be *real*, so `UNKNOWN` results from Phase 2 must either be
  closed or explicitly recorded as still-open; there is no third option.
- Settled: the host is `:platform:android` itself, via its `androidTest` source set. No new
  production module, no UI, no second app — which is also the only reading consistent with §16's
  ban on production UI and §10's on persistence.
- Settled: `androidx.test` dependencies enter the build for the first time, as test-only
  coordinates under a new `androidTestImplementation` configuration, and each must carry a
  documented reason per Phase 1 prompt §34.
- Opened: instrumentation runs a test APK as a *different package* (`com.omnibuds.android.test`)
  from the library, so permission granting for the instrumented process is a real question, and the
  project's rule is no automatic permission granting (§13, and the deployment audit's non-negotiable
  list). This is an ADR, not an implementation detail.
- Opened: an instrumented run reads the user's bonded-device list and profile connections. That is
  personal data on a personal phone, and §13 forbids logging addresses. The instrumented suite must
  therefore be designed to report *counts and shapes*, never device names or addresses, and
  `docs/security/device-access-policy.md` has to record that boundary before the first run.

---

## 5. Is any Phase 2 gap a hard dependency?

Checked individually, because prompt §2 forbids treating a gap as satisfied and forbids building a
parallel implementation around it:

| Gap | Hard dependency for Phase 3? | Reasoning |
|---|---|---|
| `AdapterStateSource` and observation | **No** | It exists, is tested, and Phase 3 reads adapter state through it — including adapter disablement (§11), which is already modelled as `BluetoothAdapterState.DISABLED` with a `isProvablyDisabled()` predicate. |
| Permission resolver rows for Phase 3 operations | **No** | Pre-computed and tested in Phase 2 as model behaviour. Consumed, not re-derived. |
| `BluetoothPlatform` has no device-facing method | **Yes, but by design, not by omission** | The fix is a new seam in the `platform` area, not extra methods bolted onto a Phase 2 interface whose KDoc states the four-question boundary. Changing `BluetoothPlatform`'s meaning would retroactively break ADR-P2-001's scope claim and `PhaseTwoScopeTest`. |
| No `androidTest` infrastructure | **Yes** | The chosen verification route does not exist yet; creating it is Phase 3 work under §19, and it is the only genuinely missing piece of the phase's plan. |
| No `DeviceConnectionState` vocabulary | **Yes** | Everything downstream (§6-§12) is expressed in it. |
| ADR-P0-018 (research ladder) | **No for the engine, yes for later claims** | Phase 3 observes system state through platform APIs; it performs no *research* capability verification, which is what the ladder ordering governs. The observation model's confidence field must therefore not borrow the ladder ADR-P0-018 would change. Recorded as an open item rather than assumed away. |

**Verdict.** Phase 3 can proceed against the architecture as it stands. No Phase 0/1/2 rule has to
be weakened to admit it, and no parallel abstraction is needed. The three deltas are additive:
new platform vocabulary, a new source seam plus engine, and the first instrumented test surface in
the repository.

---

## 6. Reuse, don't duplicate: what Phase 3 must not rebuild

| Existing | Phase 3 relationship |
|---|---|
| `DeviceIdentity` (`device`, 2) | Has no address field, deliberately (`manufacturer/model/displayName/modelId` only, blank-normalising, `unknown()` factory). Phase 3's device key is **not** identity: a platform address that may be masked or absent is a different kind of thing and lives in the observation, not here. Writing an address into `DeviceIdentity.modelId` would be the "do not store sensitive identifiers unnecessarily" violation §13 names. |
| `DeviceFingerprint` (`device`, 2) | Phase 5's type. §16 forbids fingerprinting, so Phase 3 must not populate `manufacturerData`, `serviceUuids`, `deviceClass` or `transportCandidates`. `BluetoothDevice.getType()` is a legitimate Phase 3 platform fact and is **not** the same as `deviceClass`. |
| `DeviceSession` / `DeviceState` (`session`, 3) | Phase 4's job. Phase 3 publishes observations and creates no session, no `DeviceState`, no revision counter. |
| `DeviceRepository` / `CapabilityRepository` (`persistence`, 3) | §10's collection C (saved user devices) and persistence generally. Out of scope. The §14 "repository" is an in-memory observation projection; naming it `*Repository` would collide with the persistence contracts, so the naming decision is recorded in §7. |
| `PlatformRegistration` | Reused for every receiver registration Phase 3 adds. A second teardown-handle type would split the cleanup contract. |
| `TimeProvider` / `NoTimeProvider` | Reused for `lastObservedAt`. §7's `Instant?` must **not** become `java.time.Instant` in `:core` — rule 3 bans `java.*` imports in core and ADR-P1-012 keeps the module KMP-viable. Epoch millis plus the existing nullable-time discipline. |
| `OperationOutcome` / 23 error categories | Reused. §15's error list is checked against the existing categories before any new one is proposed; ADR-P1-006's union rule and `theCategorySetIsExactlyTheDocumentedOne` mean a careless addition fails a build. |
| `DependencyDirectionTest` rules 1-12 | Extended, never relaxed. Rule 7's token list currently bans `startDiscovery`, `BluetoothLeScanner`, `startScan` and friends; Phase 3 must keep those banned (§16 forbids discovery) while *allowing* bonded-list and profile inspection, which the list does not currently name. That asymmetry is the guard work this phase must do carefully: widening one entry without weakening another, in the ADR-P2-016 style of "narrow with a reason, re-guard elsewhere". |

---

## 7. Integration plan (subject to §9)

Layer map first, because it is the constraint everything else has to satisfy:

```text
platform (1)  DeviceConnectionState · BondState · ObservedProfile · DeviceObservation ·
              DeviceObservationKey · ObservationPhase · ConnectedDeviceSource (seam) ·
              ConnectedDeviceObserver (reconcile/dedupe/transition engine)
                  ↑ imports only common(0), state(0), platform(1)
device   (2)  DeviceIdentity, DeviceFingerprint — untouched by Phase 3
session  (3)  DeviceState — untouched by Phase 3
platform/android  AndroidConnectedDeviceSource · SystemConnectedDeviceHandle ·
                  instrumented suite (src/androidTest)
```

Placing the observation model and engine in `platform` (layer 1) mirrors the one Phase 2 precedent
that works: `AdapterStateSource` + `AdapterStateObserver` + `AdapterStateObservation` all live there,
so a device-facing observation of the same kind of thing — a platform fact, read through an injected
seam, deduplicated and timestamped — goes in the same area. The alternative, a new `observation`
area, would be a 13th registered boundary for one concept and is exactly the "unnecessary module"
prompt §5 forbids; putting it in `device` (2) would make a platform-derived fact depend on, and be
depended on by, identity types Phase 5 owns.

The §14 API is named `ConnectedDeviceProjection` rather than `ConnectedDeviceRepository`, because
`Repository` in this codebase already means the persistence contract in `persistence` (3) and §10
forbids Phase 3 from persisting anything. That is a naming decision with an ADR, not a preference.

The engine's hard cases, from §9 and §11, are all deterministic and all testable off-device:
snapshot/event reconciliation ordering, duplicate suppression across profiles for one device,
devices with no usable key (§12: names must not merge two devices), idempotent repeat
disconnections, adapter-off invalidation, and the §14 rule that failure is never an empty list.

---

## 8. Test infrastructure Phase 3 needs

| Tier | What | Can it exist now? |
|---|---|---|
| T1 JVM, `:core` | reconciler, dedupe, transitions, key-absence handling, failure-versus-empty, permission-shaped inputs, adapter invalidation | Yes — the Phase 2 fake-source pattern extends directly |
| T1 JVM, `:platform:android` | the Android classes' *decisions* through scripted seams | Yes |
| Architecture | layer map unchanged, no new area, no framework leak, rule 7 widening, no persistence, no UI, no fingerprint population | Yes — extension of `DependencyDirectionTest` |
| **T3/T4 instrumented, real device** | the first evidence that `getBondedDevices()` / profile connection state / the connection broadcasts behave as the documentation claims | **Needs building**: `src/androidTest`, `testInstrumentationRunner`, `androidx.test` coordinates (not cached; `google()` reachable), a connected authorised phone (none attached now), and a decision on granting `BLUETOOTH_CONNECT` to the instrumentation package |
| T8 device session, manual | §19's 10-step pair/connect/disconnect/reconnect script | Same prerequisites; must record phone model, Android version, and which profile was actually observed — one phone is not universal compatibility |

Privacy constraint carried into the instrumented design: assertions are on counts, state shapes and
transitions. No device name or address in any log, report or committed file (§13; SEC-LOG-002).

---

## 9. Questions this audit cannot answer from the repository

Listed so the research document is judged against them, and so no implementation starts on a guess.
Each is answered or explicitly left UNVERIFIED in
`docs/phases/phase-3/connection-observation-research.md`.

1. Which connection-state broadcasts are reachable by a third-party app at all — is
   `BluetoothDevice.ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` public, `@SystemApi`,
   signature/privileged, or absent from the compile SDK entirely? **This decides whether Phase 3 is
   event-driven or reconciliation-driven**, and §8 forbids aggressive polling.
2. `BluetoothManager.getConnectedDevices(profile)` and
   `getDevicesMatchingConnectionStates(profile, states)`: existence and introduction level, and —
   the load-bearing part — whether any single call returns *all* connected devices or whether the
   answer is a union across a list of profiles an app must choose in advance.
3. Whether a BLE-only connection is observable at all without having initiated it, and what that
   means for §7's "represent partial information honestly".
4. Address semantics on current Android: resolvable, per-app-masked, or unavailable — which decides
   whether "stable device keys where available" (§9) has anything to be stable on, and whether two
   observations of one device can be joined without an address.
5. `getAlias()` (API 30+) and `getMetadata(int)`: permission level, and what they do **not** prove
   about a device's type or vendor.
6. Denial versus emptiness: does a missing `BLUETOOTH_CONNECT` produce a thrown `SecurityException`,
   an empty collection, or a silent no-event — and is any of those distinguishable from "no devices
   connected"? Phase 2 found this exact hazard for adapter state (research Q5) and answered it with
   `UNKNOWN`; §14's "do not represent failure as an empty device list" is the same rule at the next
   layer up.
7. Bond-state change broadcasts: reachability, and whether they carry enough to distinguish paired
   from connected without a bonded-list read.
8. Whether reading profile connection state requires a `getProfileProxy` binding (async, lifecycle
   burden, cleanup obligations) or is a direct query — this determines what "no unnecessary
   background services" (§13) costs in practice.
9. Which `BluetoothProfile` constants a third-party app can name on API 26 through 35, since §7's
   `observedProfiles` can only be built from the ones that exist.

---

## 10. What this audit refuses, in advance

Because §16's list is long and the pressure to "just check the battery, it's one call" arrives in
Phase 6 rather than Phase 3, the boundaries this phase commits to now:

- No discovery, no scanning, no pairing, no `connect()`, no `close()` on anyone else's connection.
- No GATT, no RFCOMM, no socket, no `getProfileProxy` used as a back door to device enumeration.
- No fingerprinting, no vendor inference, no manufacturer or model claim, no reading of a Bluetooth
  class as evidence of earbuds (§7's "identity confidence" stays conservative).
- No battery, firmware, codec or ANC/transparent/EQ/gesture value, and no code path that could be
  mistaken for having read one.
- No persistence, no device history, no saved-device store, and no cache that outlives the process.
- No production UI, no notification, no tile, no widget, no background service.
- No `java.time`, no framework type in a core signature, no `Repository` name reused for a
  projection.
- Nothing about a device is reported as "supported" because it was observed connected.

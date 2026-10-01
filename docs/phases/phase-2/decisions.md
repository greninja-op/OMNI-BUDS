# Phase 2 — Architecture Decision Records

Phase: 2 — Android Bluetooth Foundation.
Authority order: `docs/MASTER-CONTEXT.md`, then accepted Phase 0 and Phase 1 ADRs, then this file.

**Process note, stated plainly.** The architecture audit run during this phase found that `ADR-P2-003` and `ADR-P2-004` were cited in code comments before they existed, that the `platform` area had been added to the layer map without a record, and that one retry-safety assertion had been widened to accept a category that should not have existed at all. Those are real defects in my own orchestration, not in a sub-agent's output. This file closes the documentation debt, and ADR-P2-004 and ADR-P2-005 below record the corrections rather than the original mistake.

---

### ADR-P2-001 — Phase 2 lives inside the Phase 1 boundary, and `platform` becomes a registered core area
**Status.** accepted
**Context.** Phase 1 created `:platform:android` as a source-free module and fixed an area layer map in `DependencyDirectionTest` (ADR-P1-001, ADR-P1-003). Phase 2 adds adapter state, permissions and capability inspection, which are policy plus mechanism, and the policy half must not live in the Android module.
**Decision.** No new Gradle module. Core policy goes into a new `:core` area `com.omnibuds.core.platform` registered at **layer 1**, alongside `state` and `transport`: it depends only on `common`, never on `device`, `capability`, `audio`, `session`, `persistence` or `protocol`. Android mechanism goes into `:platform:android` and depends on `:core`, never the reverse.
**Alternatives considered.** A `:bluetooth` module now — rejected: it would be the transport implementations of Phase 6 arriving as an empty shell, which ADR-P1-001 forbids. Putting policy in `:platform:android` — rejected: it makes the version matrix untestable off-device and breaks the domain/platform boundary.
**Consequences.** `everyCoreAreaIsRegisteredInTheLayerMap` is what makes the registration non-optional. `:platform:android` stops being asserted empty and gains a scope assertion instead (ADR-P2-006).

### ADR-P2-002 — minSdk 26 confirmed by the user, so the permission model must be genuinely version-aware
**Status.** accepted — closes ADR-P1-015
**Context.** Phase 1 set `minSdk 26` provisionally and handed the real decision to Phase 2, because Android split Bluetooth runtime permissions at API 31.
**Decision.** Keep 26, confirmed with the user. Consequence: the requirement resolver must answer for both the legacy model (API 26–30, where `BLUETOOTH`/`BLUETOOTH_ADMIN` apply and scan results are location-linked) and the modern model (API 31+, `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`). A single-branch implementation is not acceptable.
**Alternatives considered.** Raise to 31 — simpler code, excludes Android 8–11 devices; raise to 33 — also gains unconditional LE Audio APIs, narrowest reach.
**Consequences.** The API-level band is an input to every permission decision, so it is a value the platform supplies (`BluetoothPlatformCapabilities.apiLevel`) and never a constant assumed by core code.

### ADR-P2-003 — `kotlinx-coroutines-core` returns to `:core`, on the trigger ADR-P1-021 named
**Status.** accepted — supersedes the "no production dependency" property of ADR-P1-021, not its rule
**Context.** ADR-P1-021 removed coroutines from `:core` because nothing used it, and recorded that it should return "with the first `Flow` surface". Phase 2's adapter-state observation is that surface (`BluetoothAdapter.observeAdapterState()`), so the rule's own condition is met.
**Decision.** Declare `api(libs.kotlinx.coroutines.core)` in `:core` with the reason in the build file. The dependency policy (every dependency needs a stated reason) is unchanged and still applies: a dependency without a user is a defect, and ADR-P1-021 was correct to delete the unused one.
**Alternatives considered.** Expose observation through a callback list to keep `:core` dependency-free — rejected: Phase 2 prompt sections 5.2 and 5.8 name `Flow`, `StateFlow`, structured concurrency and cancellation explicitly, and a hand-rolled callback list would re-implement them worse.
**Consequences.** `:core` is no longer JVM-library-free; the KMP audit in Phase 46 must now verify coroutines targets, which is the honest state of a project that uses coroutines.

### ADR-P2-004 — Eight platform-failure categories are added; two candidate additions were refused
**Status.** accepted — amends ADR-P1-006
**Context.** Phase 2 prompt section 5.7 lists ten failure shapes. Phase 1 already had sixteen categories, and ADR-P1-006 fixed the rule that the set grows by union rather than substitution.
**Decision.** Add `ADAPTER_UNAVAILABLE`, `UNSUPPORTED_OPERATION`, `PLATFORM_API_UNAVAILABLE`, `CONNECTION_UNAVAILABLE`, `RESOURCE_UNAVAILABLE`, `PLATFORM_EXCEPTION`, `UNKNOWN_FAILURE`. Refuse two tempting additions:
  - **Cancellation** stays an `OperationOutcome` case, not an error category (ADR-P1-004). A category for it would let the same fact be modelled two ways.
  - **`ADAPTER_STATE_UNKNOWN`** was drafted during this phase and removed. "The adapter state could not be read" is already carried by `BluetoothAdapterState.UNKNOWN` as a *state*; a category with the same name invites the two to disagree, which is precisely the failure Phase 1 prompt section 24 forbids. The correct modelling is a failed read (`READ_FAILED` or `ADAPTER_UNAVAILABLE`) whose result leaves the state unknown.
**Consequences.** 23 categories. `theCategorySetIsExactlyTheDocumentedOne` pins the membership, and the two exhaustive tables below pin both properties for every member.

### ADR-P2-005 — Retry and invalidation properties are asserted by exhaustive tables, not by two lists
**Status.** accepted — closes Phase 1 known issue 6
**Context.** Phase 1 recorded as a known limitation that `invalidatesSession` was asserted through a "true" list and a "false" list, so a newly added category could join neither and pass unnoticed. Phase 2 added categories and the auditor demonstrated exactly that failure occurring immediately, in the same area, with three categories landing in neither list. A documented risk that recurs within one phase is not a risk, it is a defect.
**Decision.** Both properties are now asserted through a `Map<OmniBudsErrorCategory, V>` covering the whole enum, with `assertFullCoverage` comparing the map's keys to `OmniBudsErrorCategory.entries` and rejecting duplicate keys. The same mechanism guards the retry-class table.
**Consequences.** Adding a category without deciding its retry class and invalidation behaviour fails the build, which is the intended enforcement. `aWriteOrUnknownEffectIsNeverBlindlyRetried` still asserts that exactly one category — `READ_FAILED` — may be repeated without re-reading state.

### ADR-P2-006 — The boundary module is guarded by capability scope, not by emptiness
**Status.** accepted
**Context.** Phase 1's `platformAndroidModuleStillContainsNoSources` asserted that `:platform:android` contained no Kotlin files. Phase 2 puts code there, so deleting the check would have quietly removed a guard, and the audit flagged the widening of the layer map without a record.
**Decision.** Replace it with `platformModuleContainsNoUnauthorisedCapabilities`, which scans the platform module for references to capabilities Phase 2 does not authorise — GATT and RFCOMM traffic, discovery and scanning, UI, Quick Settings, widgets — plus `neitherModuleTouchesTheMediaAudioPath` and `neitherModuleReferencesUiFrameworks`. Rule 2 was also narrowed: it had banned any identifier beginning with "Bluetooth", which would have failed honest Phase 2 vocabulary such as `BluetoothAdapterState`; it now bans named Android framework types, which is what the rule actually means.
**Consequences.** The guard survives the module gaining code. A later phase must extend the token list by amending this ADR, rather than relaxing a check mid-implementation.

### ADR-P2-007 — Adapter-state observation is a single-slot machine with recorded teardown failure
**Status.** accepted
**Context.** Phase 2 prompt section 5.4 requires initial state retrieval, change handling, duplicate-broadcast suppression, registration and unregistration, cancellation, cleanup, missing adapter and permission failure, and section 5.8 forbids duplicate concurrent observers and leaked receivers.
**Decision.** `AdapterStateObserver` in `:core` owns the behaviour and takes a `Mutex` slot: a second concurrent observation is refused with `RESOURCE_UNAVAILABLE` rather than double-registering. Consecutive duplicate states are suppressed while an A-B-A sequence is preserved, because those are different claims. Teardown runs in `finally`, and a throwing disposal is captured in `teardownProblem` instead of being emitted — by then the channel may already be closed by cancellation, and sending there would raise a second unrelated failure above the real one.
**Alternatives considered.** Allowing multiple observers with reference counting — rejected for Phase 2: it hides the leak this check exists to prevent, and one UI surface plus one notification is a Phase 26 concern with real requirements.
**Consequences.** A failed read is not fatal: the failure is reported and the event stream continues, because "could not read now" plus "will announce later" is more truthful than stopping the stream.

### ADR-P2-008 — Platform facts are four separate values, and phone support rarely exceeds INFERRED
**Status.** accepted
**Context.** Phase 2 prompt section 5.5: OS API availability, phone hardware capability, runtime permission availability and connected-device support are different facts, and it forbids claiming LE Audio because an API class exists.
**Decision.** `PlatformFeatureSupport` keeps `apiAvailability`, `hardwareEvidence` and `permissionState` as separate fields, with `isUsable` requiring all three, and `blockingReason` naming which one refused. Connected-device support is deliberately not a field here; it belongs to the per-device capability model. `hardwareEvidence` may legitimately stay `INFERRED` forever on many phones, because a feature flag is not a test — and `isUsable` therefore often reports false with a reason, which is the honest answer rather than a guessed one.
**Consequences.** "Not usable because we were not permitted to look" is expressible and distinct from "unsupported by this phone", which is the distinction OmniBuds exists to preserve.

### ADR-P2-009 — Permission requirements key off `targetSdkVersion`, not the phone's API level
**Status.** accepted — **corrects Phase 0 `SEC-PERM-002`**
**Context.** The verified research (`bluetooth-api-research.md` sections 3, Q1-Q3, Q7) established that Android's Bluetooth permission model is selected by the app's declared `targetSdkVersion`, while several enforcement behaviours and API members depend on the device's actual API level. Phase 0's `SEC-PERM-002` framed requirements as device-version-driven, which is what I had remembered, and remembering is not evidence (`SEC-PERM-003`).
**Decision.** `PermissionContext` carries `targetSdk` and `deviceSdk` separately; the resolver branches on the former and emits device-level caveats from the latter. Where only the compatibility guide and not the per-method reference states a requirement (research Note A — `BLUETOOTH` for classic or BLE communication at target 30 and below), the guide is followed and that requirement's own reason string records the asymmetry, so a later reader cannot "fix" it back into a documentation gap.
**Consequences.** Phase 0's rule text stands as authored history; this ADR is the correction, indexed in `docs/decisions/README.md`. An unknown `targetSdk` yields `Failure(INVALID_STATE)` rather than the safer-looking band: a permission asked for on a guess is one the user cannot be given a reason for.

### ADR-P2-010 — A debug-only companion shell, so the bridge is verified rather than described
**Status.** accepted — user-approved
**Context.** The user specified a local-first device bridge that builds, deploys, captures and drives an Android app. This repository had nothing installable: Phase 1 §52 and Phase 2 §6 keep product UI out until Phase 49, and the approved Phase 1 shape was a source-free library module.
**Decision.** Add `:tools:companion-shell`, a debug-only application whose entire content is an inspectable view tree. It depends on nothing — not `:core`, not `:platform:android` — and declares no permission. Its resource ids and accessibility labels are a stable bridge contract, and its counter is stateful so that "did the tap land" is answerable rather than assumed.
**Consequences.** Deploy, capture, hierarchy and input become verifiable. The isolation is what keeps the honesty intact: a screenshot of this shell is evidence about the harness, and because it cannot import product code, a green harness run can never be read as evidence that OmniBuds works. No release variant is built; AGP's `beforeVariants` API does not resolve in this Kotlin DSL configuration, so the guarantee is that the bridge only ever asks for `debug`.

### ADR-P2-011 — Zero manifest permissions in Phase 2, in either module
**Status.** accepted
**Context.** Research Q7: for an app targeting API 31 or above, obtaining the adapter, reading `isEnabled`/`getState`, registering `ACTION_STATE_CHANGED`, inspecting permission status and checking `FEATURE_BLUETOOTH*` require no permission.
**Decision.** `platform/android`'s manifest keeps no `<uses-permission>` and no `<uses-feature>`; the harness shell declares none either. Rows for scanning, bonding, profiles and socket opens stay pre-computed for later phases, each of which re-verifies before acting (`SEC-PERM-003`).
**Consequences.** A declaration not tied to an executed call is a fabricated capability, and a library's manifest entries merge into every consumer silently — over-declaration is therefore expensive in exactly the way a false capability claim is. Declaring `BLUETOOTH_CONNECT` now would also move the app into the Nearby devices prompt group with no feature behind it. The honest alternative, `uses-feature` for store filtering, belongs to the module that ships.

### ADR-P2-012 — `DENIED_PERMANENTLY` stays in the model and is unreachable from app code
**Status.** accepted
**Context.** Phase 2 prompt section 5.3 lists `DENIED_PERMANENTLY` and separately forbids claiming it unless the platform supports the conclusion. Research Q4 found no app-visible signal that establishes it on current Android — the informative outcome is a system API. Research Q5 also found the callback the prompt's model implies does not exist: revocation kills the process, and silent `false`, empty-set or `STATE_DISCONNECTED` returns are indistinguishable from an adapter that is simply off.
**Decision.** Keep the enum member; forbid the Android layer from ever producing it. A refusal maps to `DENIED`, an unavailable answer to `UNKNOWN`, and only a genuine future platform signal may produce permanent denial. Where a call returns a falsy or empty result, the platform layer reports what it observed and leaves the state unknown rather than resolving it to "off".
**Consequences.** The model can express a real future state without the app ever asserting it on inference. This also removes the temptation to render "we were not allowed to look" as "your phone has no Bluetooth" — the same class of error as reporting an unverified codec as active.

### ADR-P2-013 — Transport boundaries pin their own kind; `TransportKind` gains `BLE`
**Status.** accepted
**Context.** The transport workstream reported that "each boundary answers for exactly one transport" could not be expressed in types, and that `BleTransport` had no `TransportKind` member to name, because the enum conflated BLE with GATT.
**Decision.** Add `TransportKind.BLE`: the BLE link layer and the GATT attribute protocol that may sit on it are different things (master section 8). Each sub-interface supplies a default getter for `kind` — a constant, not behaviour, so it stays legal in `:core` — and `TransportKindPinningTest` enforces it. The five interfaces remain otherwise empty by design: service discovery, characteristic and socket members added now would invent hardware behaviour that Phase 6 owns.
**Consequences.** An empty interface with a stated reason beats a populated one with fabricated members, and the deferred list is written down in `transport-boundaries.md`. The prompt's `BleTransport`/`GattTransport` sibling shape is recorded as a disagreement rather than silently reproduced.

### ADR-P2-014 — Phase authorisation is test metadata, not runtime gating
**Status.** accepted — closes audit finding R-10
**Context.** `BluetoothOperation.authorizedInPhase` risked becoming a runtime gate, which would need an ambient "current phase" value — the hidden global state Phase 0 forbids — and a shipped binary has no business knowing which phase built it.
**Decision.** The field stays as data. `PhaseTwoScopeTest` asserts the authorised set is exactly the five inspection operations, that no device-facing operation is authorised, and that every authorised plan requires nothing and produces no prompt. The resolver answers for any operation it is asked about, including Phase 3 and Phase 6 rows, and pre-computing those rows is explicitly not authorisation to perform them.
**Consequences.** Enforcement lands where it can fail a build instead of in a constant that reads as protection and is not.

### ADR-P2-015 — Framework class names are banned in core string literals too
**Status.** accepted
**Context.** The framework-name guard rejected a diagnostic string in the resolver that named `BluetoothLeAudio`. The intent could have been served by exempting string literals from the scan.
**Decision.** Keep the scan strict. A core string naming an Android class is the start of core code reasoning about platform specifics, and the message reads better without the identifier: "the LE Audio platform API does not exist below API 31".
**Consequences.** The rule is one a reader can rely on — no Android type name appears anywhere in `:core` outside comments — and the wording of user-facing diagnostics is decided by the layer that owns the concept.

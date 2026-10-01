# Phase 2 — Architecture audit

**Auditor:** Agent A (Repository and Architecture Auditor), Phase 2 §4.
**Snapshot:** taken 2026-10-01 between 18:57 and 19:01 (+0530) against `main` @ `0b28fd8`.
**Mode:** read-only. No source, build file, document or repository state was modified by this audit other than this file. No Gradle task was executed.
**Method:** filesystem reads, `git log`/`git status`/`git diff`, and direct reading of every file cited. Claims about code are cited to `file:line`. Nothing in this audit asserts that something exists without having read it.

> **Read this first: the audit is not of a clean Phase 1 baseline.** Phase 2 implementation source is **already in the working tree, uncommitted, and growing while this audit ran**. Between the first listing (18:57) and the last sweep (19:01:44) three further files appeared and one of them changed size mid-audit (`AdapterStateObserver.kt`, 6,395 → 7,787 bytes). `git status --porcelain -uall` at 19:01 reports eleven untracked Kotlin files in a **new `:core` area** plus one modified tracked file:
>
> ```text
>  M core/src/main/kotlin/com/omnibuds/core/common/OmniBudsErrorCategory.kt
> ?? core/src/main/kotlin/com/omnibuds/core/platform/  (11 files)
> ?? docs/security/device-access-policy.md
> ```
>
> `docs/phases/phase-2/` still contains only `execution-prompt.md` — there is no Phase 2 `decisions.md`, `design.md`, `specs.md`, `requirements.md` or `validation.md` for what has already been written. Phase 2 prompt §2 says "Do not assume the repository matches the intended architecture"; §10 says "Preserve unrelated changes". This tree matches neither instruction's premise. **The orchestrator must decide, before any further implementation, whether this in-flight work is adopted (and documented and repaired), or set aside.** This audit describes it rather than deleting it, because prompt §2 forbids silently overwriting previous work.
>
> Consequence for the audit's own claims: everything below about the `platform` area is true of files that exist right now, and it is also true that **`:core` does not currently compile** (§1.3, R-1). No build was run to confirm the exact compiler output; the claims are from reading imports against `core/build.gradle.kts:25-35`.

---

## 1. Module and boundary state

### 1.1 What exists

| Item | Evidence | State |
|---|---|---|
| Two Gradle modules, `:core` and `:platform:android` | `settings.gradle.kts:22,26` | present |
| `:core` = Kotlin/JVM, `jvmTarget 17`, `allWarningsAsErrors` | `core/build.gradle.kts:10-23` | present |
| `:core` production dependencies | `core/build.gradle.kts:25-35` — only four `testImplementation`/`testRuntimeOnly` entries | **zero**, per ADR-P1-021 (`phase-1/decisions.md:141-145`) |
| `:platform:android` = AGP library, `namespace com.omnibuds.android`, compileSdk 35, minSdk 26, `api(project(":core"))` | `platform/android/build.gradle.kts:17-42` | present |
| `:platform:android` sources | `find platform/android/src -type f` → `AndroidManifest.xml` only | **still source-free** |
| Manifest content | `platform/android/src/main/AndroidManifest.xml:11` — `<manifest xmlns:android=... />`, no permissions, no components | empty by design |
| Version catalog | `gradle/libs.versions.toml:8-26` — kotlin 2.0.21, coroutines 1.9.0, AGP 8.7.3, compileSdk 35, minSdk 26, targetSdk 35, junit 5.10.1; libraries: `kotlinx-coroutines-test`, `kotlin-test`, `junit-jupiter`, `junit-platform-launcher` | **no `kotlinx-coroutines-core` coordinate exists** |
| Architecture enforcement | `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt`, 11 checks | present, unmodified at HEAD |
| `:platform:android` test wiring | `platform/android/build.gradle.kts:38-42` — dependencies block is `api(project(":core"))` and nothing else | **no test framework in the Android module** |
| `android.useAndroidX=true` | `gradle.properties:7` | already on, so an androidx artifact would not require a flag change |

### 1.2 Is the Phase 1 boundary usable for Phase 2, or only nominally present?

**Usable, with one named exception.** The parts that matter are real, not decorative:

- The direction rule is a compiled module boundary plus a mechanical check, not a convention: `:core` forbidding `android.`/`androidx.`/`com.omnibuds.android` imports is asserted at `DependencyDirectionTest.kt:92-104`, and the scan `fail()`s if the source root is missing (`:44-59`) so it cannot pass vacuously.
- `TransportContract` (`transport/TransportContract.kt:40-89`) is a genuine platform-neutral seam: `kind`, `isOpen`, `suspend open()/close()/exchange()`, no GATT-shaped callback anywhere (ARCH-DEP-004, `architecture-governance.md:134`).
- The error/outcome vocabulary the platform must translate into already exists (`common/OperationOutcome.kt:16-44`, `common/OmniBudsError.kt:15-38`), which is exactly what ARCH-AND-003 (`architecture-governance.md:149`) requires of Phase 2.
- `diagnostics/OmniBudsLogger.kt:41-48` is a two-method sink whose KDoc already assigns the Android implementation to `:platform:android` (`:14-18`).

**The exception is the DI/composition root.** ADR-P1-009 (`phase-1/decisions.md:69-73`) and `phase-1/design.md:292` both state that "the composition root arrives with the first Android code in Phase 2, inside `:platform:android`". That root does not exist, and nothing in `:core` obliges it to — but the whole Phase 2 mechanism depends on it, and prompt §5.9 asks for a testable way to supply five different implementations. So: the *type-level* boundary is usable now; the *wiring* half of the boundary is not built yet, and it is the half that makes "no leaked receivers, no ambient globals" (`architecture-governance.md:187`, ARCH-STATE-003) enforceable rather than intended.

### 1.3 What the in-flight Phase 2 code already is

Eleven files in `core/src/main/kotlin/com/omnibuds/core/platform/`, none of them registered anywhere:

| File | Contents |
|---|---|
| `BluetoothAdapterState.kt:16-44` | enum `UNKNOWN/UNAVAILABLE/DISABLED/ENABLING/ENABLED/DISABLING` + three derived predicates |
| `PermissionState.kt:13-51` | the seven §5.3 states verbatim, plus `isGranted`/`allowsSilentProceeding`/`isRefused` |
| `BluetoothPermission.kt:17-36` | the six §5.3 permission names with `manifestName`, `introducedAtSdk`, `isRuntimePermission`, `isLegacyForTargeting31Plus`; `ApiRange` at `:42-64` |
| `PermissionRequirement.kt:11-26` | (operation, permission, range, required, reason) with a mandatory non-blank reason |
| `BluetoothOperation.kt:14-89` | 12 operations, each tagged `authorizedInPhase` (2/3/6/10) |
| `ApiAvailability.kt:14-36` | `UNKNOWN/AVAILABLE/UNAVAILABLE` + `apiLevelSupports()` |
| `PlatformRegistration.kt:12-23` | `isActive` + `suspend dispose()` handle |
| `TimeProvider.kt:11-18` | `fun interface` + `NoTimeProvider` |
| `AdapterStateSource.kt:19-65` | `readState()`, `openStateChanges()`, `AdapterStateChangeChannel`, and a production `UnavailableAdapterStateSource` |
| `AdapterStateObservation.kt:12-34` | `ObservationKind` + the observation record |
| `AdapterStateObserver.kt:37-172` | the observer machine: `channelFlow`, a `Mutex` single-observer slot, dedup, teardown in `finally` |

Plus `common/OmniBudsErrorCategory.kt` modified to append eight categories (`+ADAPTER_UNAVAILABLE, +ADAPTER_STATE_UNKNOWN, +UNSUPPORTED_OPERATION, +PLATFORM_API_UNAVAILABLE, +CONNECTION_UNAVAILABLE, +RESOURCE_UNAVAILABLE, +PLATFORM_EXCEPTION, +UNKNOWN_FAILURE`), with a KDoc citation to **`ADR-P2-004`, which does not exist** in any `decisions.md` or in `docs/decisions/README.md`.

---

## 2. The area layer map, and where Phase 2 belongs

### 2.1 The map as it actually is

Read from `DependencyDirectionTest.kt:30-42`, not restated from prose:

```text
0 common
1 state   transport
2 device  capability  audio  config  diagnostics
3 session  persistence
4 protocol
```

The rule (`:189-205`) is stricter than downward-only: a violation is `(areaLayer[target] ?: Int.MAX_VALUE) >= sourceLayer`, and `target != area` exempts self-imports. So **an area may import only strictly lower layers, or itself**. Layer 2 is five islands that cannot see each other; `phase-1/design.md:100` says this in prose and the test enforces it. Registration is separately forced by `everyCoreAreaIsRegisteredInTheLayerMap` (`:208-213`).

The in-flight `platform` area is **not in that map**. Both checks therefore fail: `coreAreasDependOnlyOnMoreFoundationalAreas` hits `areaLayer[area] ?: fail(...)` at `:193`, and `:209-212` reports it as unregistered. This is the mechanism working as designed, not a test bug.

### 2.2 The split: policy into `:core`, mechanism behind `:platform:android`

The governing rule is ARCH-LAYER-003 (`architecture-governance.md:85-94`): *platform implementation* owns "Android Bluetooth adapter, sockets, GATT, permissions, lifecycle, audio APIs" and **must not** own "capability classification decisions"; *core-domain* owns models and rules and **must not** own "transport mechanics, Android/JVM APIs". ARCH-AND-002 (`:148`) is explicit about the seam style: "the domain asks an 'adapter state source' and a 'permission gate'; it never asks an Android context."

| Phase 2 concern | Pure policy → `:core` | Mechanism → `:platform:android` |
|---|---|---|
| Adapter state model (§5.2) | **Yes** — the enum and its derivations are vocabulary | The reading of it |
| Adapter availability predicate | **Yes** as a shape (`isUsable`, `isIndeterminate`) | `BluetoothAdapter`/`BluetoothManager` contact |
| Permission requirement matrix + resolver (§5.3) | **Yes** — "which permission, which API band, which reason" is a rule, and the mandatory-reason guard is a policy guard | `checkSelfPermission`, `shouldShowRequestPermissionRationale` |
| Permission *state* model | **Yes** | Sampling it |
| Platform capability model (§5.5) | **Yes** for the four-axis types | `PackageManager.hasSystemFeature`, `Build.VERSION.SDK_INT` |
| Adapter-state observer machine (§5.4) | **Yes** — dedup, single-observer, initial-then-events, cleanup-on-every-path are policy; keeping them in `:core` is what makes §9's lifecycle/cancellation tests possible without a radio | `ACTION_STATE_CHANGED` `BroadcastReceiver`, `Intent.EXTRA_STATE` mapping, `Context.registerReceiver` |
| Transport boundaries (§5.6) | **Yes** — interfaces and availability records only | everything that touches a socket/GATT object (Phase 6) |
| Error categories (§5.7) | **Yes** | mapping a platform status → category (ARCH-AND-003) |
| Broadcast registration handle | interface **yes** (`PlatformRegistration`) | the concrete receiver handle |
| Clock | interface **yes** (`TimeProvider`, ADR-P1-012) | `System.currentTimeMillis()` |
| Dispatcher policy (§5.8) | **No** — `phase-1/specs.md:170` states "no core signature exposes a dispatcher"; ARCH-KMP-004 (`architecture-governance.md:208`) forbids hard Android assumptions in core | the whole thing, `internal` to the platform module |
| Lifecycle ownership | **No** — do not put an Androidx `LifecycleOwner` in a core signature | caller-scoped coroutine jobs |
| Composition root (§5.9) | **No** | yes — `phase-1/design.md:292` puts it in `:platform:android` |

The in-flight code made the **right** split for 9 of these 13 rows (`BluetoothAdapterState`, `PermissionState`, `PermissionRequirement`, `ApiAvailability`, `PlatformRegistration`, `TimeProvider`, `AdapterStateSource`, `AdapterStateObservation`, `AdapterStateObserver` are all policy/seam). It got `BluetoothOperation`'s phase-gating wrong (§6.3 below) and it has not yet put anything in `:platform:android`.

### 2.3 Proposed areas and packages

**A. `:core` — rename the area and place it at layer 1.**

`com.omnibuds.core.platform` is a bad name for the most Android-shaped vocabulary in the platform-neutral module. ADR-P1-002 (`phase-1/decisions.md:14-19`) reserves `com.omnibuds.android.<area>` for the platform module and Phase 0 `specs.md:25` reserves `platform.android.*` for the same idea — so a `core.platform` package reads as "Android is in core" to every future reader, and it is precisely the area whose job is to keep Android out. Recommend `com.omnibuds.core.bluetooth`:

- matches prompt §5.1's `bluetooth/` grouping,
- matches the `bluetooth` commit scope already in Phase 0 `git-workflow.md` and extended by ADR-P1-017,
- names the *subject*, not the mechanism, which is the rule `phase-1/specs.md:44` sets,
- and the eventual `:bluetooth:*` modules of Phase 6 (ADR-P1-001, `phase-1/decisions.md:11`) inherit the same word, so nothing renames twice.

**Layer: 1.** Every import the current files make resolves to `common` (layer 0) or to coroutines, so layer 1 is legal at today's edge set (`AdapterStateObserver.kt:3-5`, `AdapterStateSource.kt:3-5`). Layer 1 is also semantically right: this is shared vocabulary plus a contract, exactly what `transport` is.

Sub-packages (only those with files; prompt §5.1's nine-folder sketch must not be built out as empty directories — "Do not create unnecessary empty modules"):

```text
com.omnibuds.core.bluetooth           layer 1   adapter state, observation model, source +
                                                     registration + clock seams, observer machine
com.omnibuds.core.bluetooth.permission  layer 1  permission, requirement, state, range, resolver
com.omnibuds.core.bluetooth.capability  layer 1  ApiAvailability + the platform capability model
```

Check against the rule: a `bluetooth.permission` sub-package is still **one area** to the scanner (`areaOf()` at `DependencyDirectionTest.kt:76-81` takes the single path segment after `omnibuds/core/`), so sub-packaging costs nothing and creates no new layer edges. Confirmed by reading `:76-81` and `:215-218`.

**B. `:core` — transport boundaries go in the existing `transport` area, layer 1, not in `bluetooth`.**

`TransportContract` lives at `transport/TransportContract.kt:40`, layer 1. A boundary that must name `TransportKind` (`common`, 0) and reuse `TransportRequest`/`TransportResponse`/`TransportAvailability` (`transport`, 1) can only do so **from inside `transport`** — from `bluetooth` at layer 1 it is a sideways edge and `:197` fails it. Putting them in `bluetooth` at layer 2 instead would work arithmetically but would put transport shape above transport, which is the wrong reading order. So: `com.omnibuds.core.transport` gains the sub-interfaces; `com.omnibuds.core.bluetooth` does not import it.

**C. `:platform:android` — mechanism, `com.omnibuds.android.bluetooth.*`** (root fixed by ADR-P1-002, `phase-1/decisions.md:17`):

```text
com.omnibuds.android.bluetooth.adapter      AndroidAdapterStateSource (BroadcastReceiver + adapter)
com.omnibuds.android.bluetooth.permission   AndroidPermissionStateProvider
com.omnibuds.android.bluetooth.capability   AndroidPlatformCapabilityProvider (hasSystemFeature, SDK_INT)
com.omnibuds.android.bluetooth.mapping      platform status -> OmniBudsErrorCategory (ARCH-AND-003)
com.omnibuds.android.di                    composition root, dispatcher confinement (ADR-P1-009)
```

No `classic/`, `ble/`, `transport/` packages under `android` in Phase 2 — prompt §5.6 asks for boundaries, which are interfaces, and interfaces belong in `:core` where the direction rule can see them. Creating platform packages with nothing in them repeats the mistake ADR-P1-001 exists to prevent.

### 2.4 Layer-map changes this forces — all of them ADR-gated

1. **Registering a 12th area is itself a boundary change.** ARCH-GOV-002 (`architecture-governance.md:240`) requires "every change to a boundary in §3, a dependency rule in §4 … recorded as `ADR-<SCOPE>-<NNN>`". The layer map is the §4 rule expressed in code. So `bluetooth` at layer 1 needs an ADR *before* the test is edited, and the `everyCoreAreaIsRegisteredInTheLayerMap` edit is the ADR's implementation, not a fix-forward.
2. **A `:core` type holding mutable state.** `AdapterStateObserver.kt:41` (`private val slot = Mutex()`) is the first mutable holder in main source since `SnapshotBuilder`. `phase-1/design.md:285` states "`:core` has none" and lists the exception. ARCH-STATE-003 permits shared mutable state only in its owning boundary and "observable, not a singleton" — `isObserving` (`:44-45`) makes it observable, so it is admissible, but `phase-1/design.md` §14/§19 must be amended and the ADR must name `AdapterStateObserver` as the single owner of adapter-state observation.
3. **A `:core` dependency on `kotlinx.coroutines`.** See §5.2 — this reverses ADR-P1-021 and needs its own ADR.
4. **Adding sub-interfaces to `TransportContract`'s hierarchy.** Not a change to a released interface (so `phase-1/specs.md:250` is not triggered), but it *is* a change to the §3 transport boundary and to ARCH-XPORT-001, so it is ADR-galled anyway. See §3.2 for which boundaries I recommend **refusing**.
5. **No per-endpoint codec or audio-transport change.** If Phase 2's capability model reaches for `Codec`/`AudioTransportKind` (`audio`, layer 2) from `bluetooth` (layer 1), that is an upward edge and the build will fail. Keep `ApiAvailability` as the only host-axis vocabulary; do not let a capability-inspection type import `audio`.

---

## 3. Missing or insufficient contracts

### 3.1 `TransportContract` versus prompt §5.6

Read `transport/TransportContract.kt:40-89`. It supports four of the five boundaries §5.6 sketches, **without inventing protocol behavior**, and it does not support the fifth:

| §5.6 interface | Verdict against the code |
|---|---|
| `BluetoothTransport` (root) | Redundant. `TransportContract` *is* the root: `kind` (`:43`) already discriminates the channel. A second root interface above it gives transports two identities and no new behaviour. |
| `GattTransport` | Fine as an almost-empty sub-interface carrying `kind == TransportKind.GATT` (`common/TransportKind.kt:13`). |
| `RfcommTransport` | Fine; `TransportKind.RFCOMM` exists (`:16`). |
| `ClassicTransport` | Fine; `TransportKind.CLASSIC_BLUETOOTH` exists (`:19`). |
| `LeAudioTransport` | Type-checks (`TransportKind.LE_AUDIO`, `:22`) but Phase 2 must not give it *any* member. Master §20 treats LE Audio as "an architecture in its own right" (`protocol-governance.md:57`) and §5.5 forbids claiming it is active because an API exists. Marker interface + `ApiAvailability` only. |
| **`BleTransport` as a sibling of `GattTransport`** | **No.** BLE-without-GATT is not a control channel; a vendor control service over BLE *is* GATT. `TransportKind` reflects this — there is no `BLE` member, only `GATT` (`common/TransportKind.kt:11-29`), and master §366-374's own conceptual sketch lists exactly `GATTTransport, RFCOMMTransport, ClassicBluetoothTransport, LEAudioTransport, FutureTransport`. Creating both invites two implementations of one operation, which ARCH-DEP-007 (`architecture-governance.md:137`) names as a defect. **Surface this as a disagreement with prompt §5.6 rather than satisfying it.** |
| **A2DP / AVRCP / HFP transports** (Agent E's list, prompt §4.2/§5.6) | **No.** These are audio/call profiles, not control channels, and `PROT-XPORT-002`'s table forbids the confusion outright: for A2DP, "control code must not send commands over a media channel" (`protocol-governance.md:54`). The existing vocabulary already places them correctly in `audio/AudioTransportKind.kt:15-27` (`CLASSIC_A2DP`, `HFP`, `LE_AUDIO`, `UNKNOWN`). Giving them `TransportKind` values would imply OmniBuds opens them. Profile *availability* belongs to the platform capability model as an `ApiAvailability`/feature-flag fact, never as a transport. |

One genuine insufficiency, already recorded: `TransportContract` has **no notification/event channel** (`phase-1/validation.md:76`, architecture question 2's caveat). A notify-driven vendor channel will need an addition. **Phase 2 must not add it** — that is a member on a released interface (`phase-1/specs.md:250`, ADR required) and it is Phase 6's transport mechanics. Leave the gap stated.

Also: `TransportAvailability.reason` accepts an `OmniBudsErrorCategory` (`transport/TransportAvailability.kt:40`), so the eight new categories become legal "unavailable reasons" for a channel with no observed channel. Recommend Phase 2 constrain which categories may appear there in the resolver's tests, or an `ADAPTER_UNAVAILABLE`-reason `TransportAvailability` becomes a claim about a transport nobody probed.

### 3.2 Seams that must exist and do not (interfaces only)

| Needed by | Status | What to add |
|---|---|---|
| §5.2 adapter abstraction — `isBluetoothAvailable`, `getPlatformCapabilities` | **absent** (prompt's `BluetoothPlatform`, `execution-prompt.md:276-283`, appears in no source file — grep for `BluetoothPlatform` matches only the prompt) | `interface BluetoothAdapterInspector { fun isAvailable(): ApiAvailability… }` or a `BluetoothPlatform` seam in `bluetooth`; availability must be a *fact*, not the absence of a throw |
| §5.3 **permission-state provider** | **absent** — `PermissionState` and `PermissionRequirement` exist but nothing reads the platform | `interface PermissionStateProvider { fun standingOf(permission: BluetoothPermission): PermissionState }` — the "permission gate" ARCH-AND-002 names |
| §5.3 **centralised permission requirement resolver** ("Create a centralized permission requirement resolver") | **absent but referenced**: `BluetoothPermission.kt:9` and `BluetoothOperation.kt:11` both KDoc-link `[PermissionRequirementResolver]`, a symbol that does not exist | `interface PermissionRequirementResolver { fun requirementsFor(op: BluetoothOperation, sdkInt: Int): List<PermissionRequirement> }` + one implementation over a frozen table |
| §5.5 platform capability model (`BluetoothPlatformCapabilities`) | **absent**, and the four axes are only partly representable. Axis 1 (OS API) = `ApiAvailability`; axis 3 (permission) = `PermissionState`; axis 4 (device support) = `CapabilityState`. **Axis 2 — "phone hardware capability" — has no type.** `ApiAvailability.kt:25-31`'s KDoc describes exactly such a type ("Whether the phone's hardware is believed to support a feature") and then documents `apiLevelSupports()` (`:32-36`), which answers the OS axis. So the doc is orphaned and the type is missing | a `HostHardwareCapability`-shaped enum with an honest `UNKNOWN`, plus a `BluetoothPlatformCapabilities` record holding the four axes **separately**, never collapsed |
| §5.4 lifecycle/registration handle | **present** — `PlatformRegistration.kt:12-23` (`isActive`, idempotent `suspend dispose()`). This is adequate | nothing |
| clock | **present** — `TimeProvider.kt:11-18` | nothing |
| broadcast/event source | **present** — `AdapterStateSource` + `AdapterStateChangeChannel`, `AdapterStateSource.kt:19-45` | nothing, but see §4/R-6 about `unavailable()` |
| dispatcher | **absent, and must stay out of `:core`** | platform-internal confinement only (`phase-1/specs.md:170`) |
| §5.9 transport factories | **absent** — and a factory that can produce a transport is a channel that can be opened, which §6 forbids | declare the factory *type* returning `OperationOutcome.Failure(TRANSPORT_UNAVAILABLE)` for every kind in Phase 2, or defer the factory entirely to Phase 6. Recommend deferring; a Phase 2 factory is an unimplemented path that reads as implemented, which is the exact thing `DependencyDirectionTest.kt:141-153` exists to stop |

Two further shape problems in what already exists:

- `BluetoothOperation.kt:14-89` puts the project's phase plan into domain data (`authorizedInPhase = 2/3/6/10`) and `:11` says the resolver "refuses to produce an executable plan for any operation whose authorizedInPhase is ahead of the running phase". Producing that verdict at runtime needs a value for "the running phase" — an ambient constant, which is what ARCH-STATE-003 (`architecture-governance.md:187`) forbids, and which a shipped binary should not contain in any case. **Model the boundary as a test**, not as runtime data: a Phase 2 test that asserts no Phase-3+ operation is reachable, plus a documented allowed-set in the resolver.
- `BluetoothPermission.kt:55-56` hard-codes `MIN_SUPPORTED = 26` / `MAX_SUPPORTED = 35` inside a domain type, duplicating `libs.versions.toml:13-14` (`androidMinSdk = "26"`, `androidTargetSdk = "35"`). Two representations of one fact drift (`phase-1/specs.md:118` states the principle). The band values should be *injected* into the resolver or the constants should be renamed to say "the permission matrix's supported band", which is a different claim from the app's minSdk.

---

## 4. Error model impact

### 4.1 What §5.7 asks and what exists

Phase 1 fixed sixteen categories (`common/OmniBudsErrorCategory.kt:20-35` at HEAD) as the union of master §30's thirteen (`MASTER-CONTEXT.md:1117-1130`) plus `READ_FAILED`, `UNKNOWN_DEVICE`, `INVALID_STATE` — ADR-P1-006 (`phase-1/decisions.md:51-55`). Prompt §5.7 lists ten Bluetooth failure shapes. Six of the ten already have a home, which the in-flight change recognised: "adapter disabled" → `BLUETOOTH_DISABLED`, "permission denied" → `PERMISSION_DENIED`, "unsupported operation" partially → `UNSUPPORTED_FEATURE`, "bluetooth unavailable" → `TRANSPORT_UNAVAILABLE`, and — correctly — "operation cancelled" is **not** a category at all, because `OperationOutcome.Cancelled` is an outcome case (ADR-P1-004, `common/OperationOutcome.kt:24-28`; the diff's own comment says so).

Eight were added. 16 → 24.

### 4.2 Exactly what breaks, file by file

All enumeration sites are in **one** test class (verified by grepping `OmniBudsErrorCategory.entries`/`.values()` across `core/src`; the only hits are lines 30, 56, 64 below):

| Site | Assertion | Result with 24 categories |
|---|---|---|
| `core/src/test/.../common/OmniBudsErrorCategoryTest.kt:21-31` `theCanonicalCategoriesAreAllPresent` | `assertEquals(16-name set, entries.map{name}.toSet())` | **FAILS** — set inequality |
| `:34-60` `everyCategoryDeclaresItsRetryClassAndNoneIsUnspecified` | `:56` `assertEquals(entries.toSet(), expected.keys, "every category must be pinned")` | **FAILS** — this is the *exhaustiveness* pin added at commit `908aa9c`; eight new categories are unpinned by design of the test |
| `:62-67` `onlyIdempotentReadsMayBeRetriedWithoutCheckingStateFirst` | `assertEquals(setOf(READ_FAILED), entries.filter{retryClass==SAFE_TO_RETRY})` | **FAILS** — and it should: `ADAPTER_STATE_UNKNOWN` was declared `RetryClass.SAFE_TO_RETRY` in the diff. The test's name is the safety rule ("only *idempotent reads* may be retried without checking state first"); an "adapter state unknown" category is not an idempotent read |
| `:76-102` `categoriesThatLeaveTheDeviceStateUncertainSaySo` | two hand-written `listOf(...)`s, not derived from `entries` | **PASSES — vacuously.** All eight new categories' `invalidatesSession` go unasserted |
| `core/src/test/.../common/OperationOutcomeTest.kt` | three-case shape only | passes |
| Any `when` over the enum in main source | none exists — attributes are constructor-declared (`OmniBudsErrorCategory.kt:11-19`) | no compile breakage from that angle |

**That last row is the finding, not the four FAILs.** The four failures are loud and correctable. The silent pass is Phase 1's *known issue 6* (`validation.md:143`) landing exactly as predicted: "a newly added category could slip past the invalidation assertion". Eight categories now carry `invalidatesSession` values that nothing tests — and two of them (`CONNECTION_UNAVAILABLE`, `PLATFORM_EXCEPTION`, `UNKNOWN_FAILURE`) are set `true`, which is the *expensive* setting: it forces re-discovery before the next write (`phase-1/specs.md:144`).

### 4.3 Least-invasive correct approach

1. **Keep the pin, extend the pin.** Do not loosen `:21-31` or `:56`. Update the expected set to 24 names and add the eight `(category, retryClass)` rows to the `:35-52` map. The test's whole purpose is that a category cannot be added without someone deciding its retry policy; that purpose survives Phase 2 unchanged.
2. **Convert `:76-102` into the exhaustive map it should have been** — one `mapOf(category to invalidatesSession)` over all 24, plus `assertEquals(entries.toSet(), map.keys)` — closing known issue 6 as part of Phase 2 rather than leaving it open a second phase. This is a test-only change, zero production impact, and it is the honest fix: the current shape is the reason eight values arrived untested.
3. **Delete `ADAPTER_STATE_UNKNOWN` as a category.** It is a **state value**, and the state value already exists: `BluetoothAdapterState.UNKNOWN` (`platform/BluetoothAdapterState.kt:16-18`, with `isIndeterminate()` at `:43-44`). ADR-P1-004 is the governing rule and it says so directly (`common/OperationOutcome.kt:11-14`): "Unknown is not an outcome at all: it is a property of the value". A read that could not classify the adapter is `Success(state = UNKNOWN)`, not a `Failure`. Keeping it as a category also (a) breaks the SAFE_TO_RETRY invariant, and (b) creates a second, weaker representation of `UNKNOWN` that a later phase can branch on. Removing it takes the count to 16 → **23**.
4. **Do not add a category for anything prompt §5.2 already models as state.** "Adapter disabled" and "adapter enabling" are `BluetoothAdapterState` values observed through the stream; `BLUETOOTH_DISABLED` is what an *operation* returns when it needs an on adapter. Both representations are legitimate — one is a fact, one is a refusal — and the design doc must say which is which or the state engine will get two owners of the same fact (ARCH-STATE-001).
5. **Reconcile `CONNECTION_UNAVAILABLE` against `DEVICE_DISCONNECTED` before adopting it.** The existing category already means "session gone" (`protocol-governance.md:288`) and `ConnectionState.DISCONNECTED`/`TEMPORARILY_UNAVAILABLE` already express the state side (`state/ConnectionState.kt:23-25`). §5.7's "connection unavailable" is most plausibly *the same fact*. Two categories for one fact is the drift Phase 1 spent ADR-P1-020 removing (`phase-1/decisions.md:135-139`). If it is kept, the ADR must state the sentence that distinguishes them.
6. **Vocabulary-change governance is mandatory, not stylistic.** Adding categories is a §1.3 canonical-vocabulary change → ARCH-GOV-002 → one ADR (the diff already promises `ADR-P2-004`; it must be written). It also amends ADR-P1-006, so `docs/decisions/README.md` must mark the supersession in both places per its own rule 2 (`:58`). Documents that currently state sixteen and will become wrong: `phase-1/validation.md:52`, `phase-1/design.md:272`, `phase-1/specs.md:110` and `:272`, `phase-1/requirements.md:169,174,267`, `phase-1/domain-model-review.md:98`, `phase-1/testing-review.md:66`, `phase-1/test-plan.md:159,261`. Phase 0's documents state thirteen and are already superseded (C5, `architecture-governance.md:68`). **Phase 1's records are historical and should not be edited**; Phase 2's own `specs.md` carries the 24 (or 23) row table forward.

---

## 5. Dependency and build impact

### 5.1 What `:platform:android` needs to compile real Android code

| Need | Verdict |
|---|---|
| `BluetoothAdapter`, `BluetoothManager`, `BroadcastReceiver`, `Intent`, `Context.checkSelfPermission`, `Build.VERSION` | **all in `android.jar` for compileSdk 35. Zero artifacts required.** (`platform/android/build.gradle.kts:19` already sets it.) |
| Coroutines for `Flow`/`Mutex`/`channelFlow` | **required — but in `:core`, not only in the platform module**, because the observer machine and `AdapterStateSource`'s `Flow` live in core (see §5.2) |
| `kotlinx-coroutines-android` | **avoidable.** Only needed for `Dispatchers.Main`. Phase 2 has no UI and no main-thread surface (prompt §6 forbids UI), so confine to `Dispatchers.IO`/`Default` from `coroutines-core`. Adding it would be a dependency without a reason |
| `androidx.core` (`ContextCompat`, `registerReceiver` `RECEIVER_NOT_EXPORTED` flags) | **avoidable.** `Context.registerReceiver` and the `Context.RECEIVER_*` constants are framework API from API 33/34 on; the `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED` requirement on targetSdk 34+ can be met with the framework constant. If `ContextCompat.registerReceiver` is used instead, that is a real androidx `core` dependency and needs a recorded reason |
| `androidx.annotation` (`@RequiresPermission`, `@RequiresApi`) | **optional.** Nice for lint documentation, but it is a new coordinate purely for annotations; ADR-P1-011's baseline is "compiler plus dependency-free checks" |
| `androidx.lifecycle` (`LifecycleOwner`, `repeatOnLifecycle`) | **do not add.** §5.4's "lifecycle registration and unregistration" is satisfied by `PlatformRegistration` + the caller's coroutine job. Adopting androidx-lifecycle in a library module before any UI exists (Phase 49) is the "large dependency for a trivial abstraction" prompt §5.9 forbids |
| Test framework for `:platform:android` unit tests | **required.** `build.gradle.kts:38-42` currently declares no test dependencies at all, so §9's "Android-specific tests" cannot be written in that module today. Needs `kotlin-test`, `junit-jupiter` (both already in the catalog, `libs.versions.toml:19-21`) and `kotlinx-coroutines-test` (`:18`) as `testImplementation` — all *existing* catalog entries, so this is a wiring change, not a new coordinate |

### 5.2 The `:core` dependency reversal

**This is the build blocker.** `AdapterStateObserver.kt:6-9` and `AdapterStateSource.kt:6` import `kotlinx.coroutines.flow.Flow`, `flow.channelFlow`, `sync.Mutex`, `sync.tryLock` from `:core` **main** source, while `core/build.gradle.kts:25-35` declares only `testImplementation` entries and `gradle/libs.versions.toml:18` provides only `kotlinx-coroutines-test`. Test-scope artifacts are not on the `main` compile classpath, so **`:core:compileKotlin` cannot succeed as the tree stands**. Two secondary suspicions worth a look when the build is run, which this audit does not assert as fact: `import kotlinx.coroutines.sync.tryLock` (`:9`) names a `Mutex` *member*, and member functions are not importable — it is likely an unresolved reference; and `send(...)` from the `finally` block at `:139-149` targets a channel whose collector may already be gone.

The fix is not "add the dependency quietly". ADR-P1-021 (`phase-1/decisions.md:141-145`) removed `kotlinx-coroutines-core` on the explicit grounds that "the coroutines library returns in the phase that introduces the first `Flow` surface — the Phase 2 state engine — **with a recorded reason**". Phase 2 §5 and prompt §34 repeat it. So:

- Add `kotlinx-coroutines-core` to the catalog using the **existing** `kotlinCoroutines = "1.9.0"` ref (`libs.versions.toml:10`) — no new version to justify.
- Add it to `:core` as `implementation` (not `api`) unless a `:platform:android` or future module must see `Flow` in a signature *it* declares — `AdapterStateSource` returns `Flow` in core API, so **`api` is the honest scope** and the ADR should say so rather than discovering it later.
- Record the ADR. ADR-P1-021 is not being reversed so much as *discharged*: it pre-authorised the return, and the ADR is the trigger.
- Say what it costs: `:core` loses its "zero production dependencies" property, which `phase-1/design.md:36` and §19 list as a KMP strength. The honest statement is that `kotlinx-coroutines-core` **is** multiplatform, so Phase 46 portability is not harmed — but RISK-017's mitigation ("`:core` has zero production dependencies, which shrinks the transitive surface to nothing", `phase-1/risk-register.md:20`) needs re-scoring, because there is now a transitive surface.
- The alternative that avoids the dependency entirely — hand-rolling the observer on a callback interface — should be considered and rejected in the ADR, not avoided silently: §5.4 needs cancellation and cleanup, and prompt §5.2 shows `Flow`. Re-implementing cancellation by hand to keep a zero-dependency badge would be the worse trade.

`allWarningsAsErrors` is on in both modules (`core/build.gradle.kts:15`, `platform/android/build.gradle.kts:34`), so an unused import, a deprecation or an unchecked opt-in in the new code is a **build failure**, and `phase-1/specs.md:19` warns that even a doc edit leaving an unused import breaks the build. `channelFlow` and `Mutex` are stable APIs; `BroadcastReceiver.onReceive` and the `RECEIVER_NOT_EXPORTED` path will produce a deprecation only if the wrong overload is used.

### 5.3 What changes when the first Android source lands — named checks and honest revisions

**Check 1 — the one the question is about: `platformAndroidModuleStillContainsNoSources`** (`DependencyDirectionTest.kt:264-271`). It walks `../platform/android/src/main` and asserts the `.kt` list is empty. Phase 2 puts files there, so it fails by construction.

It must be **replaced, not deleted**, and the replacement must keep the *claim* the check protected ("nothing here talks to hardware", `phase-1/validation.md:130-132`). Proposed successor, same file, same dependency-free style:

- `platformAndroidSourcesLiveOnlyUnderTheAuthorizedPackages` — every `.kt` under `platform/android/src/main` sits below `com/omnibuds/android/bluetooth/` or `com/omnibuds/android/di/`. That kills the "unnecessary module/package" drift and keeps an accidental `ui/`, `notification/`, `quicksettings/` or `protocol/` package from appearing, which is what the old check's message actually cared about.
- `platformAndroidManifestDeclaresOnlyJustifiedPermissions` — parse the manifest's `<uses-permission>` names and assert membership in the Phase 2 allowed set. This is the machine-checkable form of SEC-PERM-004 ("request nothing the app cannot justify") and it is the check that stops `BLUETOOTH_SCAN` being declared "for later" while §6 forbids scanning.
- `coreContainsNoAndroidFrameworkSources` stays exactly as is (`:92-104`) — this is the one rule Phase 2 must not relax.

**Check 2 — `coreReferencesNoBluetoothOrAudioFrameworkTypes`** (`:124-136`, regex `\b(Bluetooth[A-Za-z0-9_]*|AudioTrack|AudioManager|AudioRecord|MediaPlayer)\b` over comment-stripped code lines). Today's in-flight code violates it dozens of times on the first line of every file (`enum class BluetoothAdapterState` at `BluetoothAdapterState.kt:16`, `enum class BluetoothPermission` at `BluetoothPermission.kt:17`, `BluetoothAdapterState.UNKNOWN` at `AdapterStateObserver.kt:69`, etc.).

Do not delete it; **re-target it from "no identifier that looks like Bluetooth" to "no framework reference"**, and keep the media half intact:

- the real leak shapes are the framework names, so narrow the pattern to `BluetoothAdapter|BluetoothManager|BluetoothDevice|BluetoothGatt|BluetoothGattCallback|BluetoothSocket|BluetoothLeAudio|BluetoothProfile` — the same list ARCH-DEP-003 enumerates (`architecture-governance.md:133`). Domain vocabulary of the shape `BluetoothAdapterState` survives; a framework handle does not.
- add a companion rule that no `:core` code line contains `android.bluetooth` in any form, including a fully qualified inline use. `phase-1/specs.md:213` admits the import scans miss inline FQNs; this closes the hole the new area opens.
- **keep `AudioTrack|AudioManager|AudioRecord|MediaPlayer` banned unchanged**, and extend that ban into `:platform:android` for Phase 2: prompt §7 forbids touching the audio path at all, so "no media-audio type appears anywhere" is *stronger* and still true after Phase 2. That is the phase's audio isolation acceptance criterion made mechanical.
- the Phase 1 claim it protected must be re-established by a different check, and one already exists: `noProductionClassImplementsTheProtocolOrRepositoryContracts` (`:171-184`). Extend its contract list with `AdapterStateSource` and the new permission/capability seams, so "`:core` implements no platform mechanism" survives as a check rather than becoming a memory. Note its regex anchors on `(class|object)\s+\w+[^{]*:`, so `interface X : TransportContract` is intentionally invisible — fine, and worth a comment in the test so nobody "fixes" it.

**Check 3 — the two layer-map checks** (`:189-205`, `:208-213`) must gain the `bluetooth` (or whatever the area is named) entry from the ADR. **Check 4 — `mainSources()`** (`:44-59`) needs no change. **Check 5 — nothing in the test file scans `:platform:android` for dependency direction**, so the new module's imports are unenforced; the successor checks above are the cheapest way to close that, at zero dependency cost.

---

## 6. Known-issue interactions

| # | Phase 1 limitation (source) | Phase 2 verdict | If untouched, why is that safe? |
|---|---|---|---|
| 1 | `DeviceState.copy()` bypasses the state machine — `validation.md:138`, `session/DeviceState.kt:30-36` | **Phase 2 does not touch it and must not.** Phase 2's adapter state is a different type; nothing in scope constructs or mutates a `DeviceState`. **But Phase 2 is creating the same class of defect next door:** `AdapterStateObserver`'s `Mutex` slot is a guard that convention alone enforces at the call site (a second observer instance = two slots). The ADR should require exactly one injected observer instance, or move the slot onto the source, so the phase does not ship a second `copy()` problem while leaving the first open. Owner stays Phase 24 | Safe: the bypass cannot be exercised without a `DeviceState`, and Phase 2 creates none. It is not *fixed*, and `validation.md` must not be allowed to imply otherwise |
| 2 | `DeviceSession` ↔ `DeviceState` join only on `sessionId` — `validation.md:142` | **Untouched, and correctly so.** Prompt §6 forbids "automatic connected-earbud session creation", so Phase 2 creates no session and no pairing to mis-join | Safe, provided Phase 2 does not introduce a `DeviceSession` value from adapter facts — a real temptation, since `BluetoothOperation.CONNECTED_DEVICE_INSPECTION` already exists in the enum (`BluetoothOperation.kt:50-54`). Keep it as a *modelled future operation*, never as a Phase 2 code path |
| 3 | Duplicated vendor-identity grammar — `validation.md:141`, deferred to Phase 7 | **Untouched.** Partly closed already: `VendorExtension.isVendorFeature` now delegates to `FeatureId.isVendorExtension` (`capability/VendorExtension.kt:82`), so what remains is a thin alias plus `vendorSegmentOf` (`:88-95`). Phase 2 adds no feature identities | Safe: nothing in the Bluetooth foundation reads or writes a `FeatureId` |
| 4 | No per-endpoint codec support — ADR-P1-018 (open), `phase-1/risk-register.md:81-87` | **Untouched, and Phase 2 is the first real temptation to break it.** §5.5 demands "phone hardware capability" as a distinct axis, and the honest shape is close to a per-endpoint discriminator. Two guardrails: (a) `ApiAvailability`/`HostHardwareCapability` answer *host* questions and must never carry a `Codec`, `CodecState` or `AudioTransportKind` — which the layer map also forbids from layer 1 (`audio` is layer 2); (b) §5.5's "do not claim LE Audio is active merely because an API exists" and §7's "do not claim an audio codec is active" mean the Phase 2 capability model must return zero codec facts | Safe: the model gap remains open with Phase 11 as owner, and Phase 2's host-axis type is *narrower* than a codec endpoint record, so it does not pre-empt it — provided the ADR says so out loud |
| 5 | No redactor for diagnostics — ADR-P1-019, `validation.md:145`, `diagnostics/DiagnosticEvent.kt:30-36` | **Phase 2 must not create the sink.** It is the first phase with a real reason to emit logs, and `OmniBudsLogger.kt:30-33` states plainly that redaction is the sink's obligation and that `message` arrives pre-redaction with nothing enforcing it. Phase 2 options, in order of preference: ship **no** sink (leave `OmniBudsLogger` unimplemented, as now); or ship one that emits only `category` + `operationId` + severity, never `message` and never `OmniBudsError.detail`. The current in-flight detail strings are benign (`AdapterStateObserver.kt:145` prints `cause::class.simpleName`, an exception class, not an identifier) — but that is luck about a code path, not a rule | Not safe to leave *silently*: SEC-LOG-002's redaction duty has no owner until Phase 36, and Phase 2 is the first phase that could put a `Log.i` in front of it. The ADR must name the sink's absence as the mitigation |
| 6 | No coverage measurement — `validation.md:139`, gap G-3 | **Phase 2 should not claim it.** Adding a Kover/Jacoco artifact is a dependency whose reason is "a number we want", not "the code needs it" — ADR-P1-011's baseline argument. The higher-value move is cheap: give `state` and the new `bluetooth` area direct test classes so the areas are covered by *tests*, not by a percentage | Safe: the risk was always "300 green tests say nothing about untested branches"; Phase 2's §9 test list is explicit about which behaviours are asserted, which is the substitute evidence |
| 7 | No CI, no remote — RISK-020 (`phase-1/risk-register.md:41-47`), "CI in Phase 2 is the intended closure" | **Phase 2 should still not add CI, and must record why.** The user builds centrally; inventing a `.github/workflows` file in a repo with no remote (verified: no CI config anywhere in the tree, and `docs/decisions/README.md:69` says the remote decision is open) would be an untestable claim — the very thing prompt §8's `validation.md` rule ("never mark a test as passed if it was not executed") forbids. The substitute gate for Phase 2 is the honest one: a central `./gradlew build` plus the revised architecture checks, recorded as executed | Safe **only** because RISK-020's mitigation is already "treat local execution as mandatory before each commit" — and today's tree is that risk materialising: local execution has not caught a non-compiling `:core` |
| 8 | `minSdk 26` provisional — ADR-P1-015 (`phase-1/decisions.md:105-109`), `validation.md:150`, `docs/decisions/README.md:67` | **Phase 2 must close it, and 26 is now confirmed.** The consequence is concrete and already half-modelled: `ApiRange.LEGACY_BLUETOOTH_MODEL = 26..30` and `MODERN_BLUETOOTH_MODEL = 31..35` (`BluetoothPermission.kt:59-62`) is the right shape, and `BluetoothPermission.isLegacyForTargeting31Plus` (`:28`) captures the asymmetry that an app targeting 31+ is *not* granted the old Bluetooth permissions on a 31+ device. The resolver must therefore produce **two different answers for the same operation** depending on band, and `BLUETOOTH`/`BLUETOOTH_ADMIN` stay install-time normal permissions (`isRuntimePermission = false`, `:30-31`) while `BLUETOOTH_SCAN`/`CONNECT` are runtime (`:32-33`) | N/A — this one is Phase 2's to settle. It also needs the location coupling: SEC-PERM-002's BLE-scan row (`security-governance.md:42`) says "`neverForLocation`-style declarations behave differently across versions", so the matrix cannot treat the pre-31 band as merely "fewer permissions" |

---

## 7. Open and conflicting architecture decisions

| Item | Status in the record | Can Phase 2 proceed? | What must be recorded first |
|---|---|---|---|
| **ADR-P0-018** — research ladder order (enumerate → identify vs identify → discover) | **proposed — needs user confirmation**, `phase-0/decisions.md:130-135`; indexed `docs/decisions/README.md:24`, and its open-items table (`:66`) states it blocks "Phases 3, 5, 20 ordering — **not** Phase 2" | **Yes.** Phase 2 touches no device, so no ordering is exercised. `TransportKind` carries no ordering assumption and `AdapterStateSource` is host-only | Nothing. But the index rule at `docs/decisions/README.md:59` ("an ADR in `proposed` status must not be relied on by implementation tasks") means Phase 2 must **not resolve it by implication**. The one place it could: `BluetoothOperation.CONNECTED_DEVICE_INSPECTION` vs `DEVICE_DISCOVERY_SCAN` ordering (`BluetoothOperation.kt:50-64`) pre-commits a Phase 3 sequence. Keep both unauthorised in Phase 2 and say the ladder is still open |
| **`OB-P2-REQ-NNN`** (prompt §8, `execution-prompt.md:563-568`) vs **ADR-P0-013**'s canonical `REQ|TASK|TEST|ADR-<SCOPE>-<NNN>` (`phase-0/decisions.md:100-104`; `architecture-governance.md:52,235`) | Direct conflict. Phase 0 already resolved the identical case for its own prompt as conflict **C7** (`architecture-governance.md:70`): "the prompt's numbering is a non-conforming example, not a rule", and Phase 1 in fact shipped `REQ-P1-0NN`/`TASK-P1-0NN` (`phase-1/requirements.md`) | **Yes** — the governance document already won this argument once | Use `REQ-P2-<NNN>`, `TASK-P2-<NNN>`, `TEST-P2-<NNN>`, `ADR-P2-<NNN>`, `RISK-<NNN>` (continuing `RISK-025`). Record the choice as one line in Phase 2 `decisions.md` citing ADR-P0-013 + C7 so it is not read as a new decision, and surface the prompt's deviation in the phase report per ARCH-GOV-004. **Do not create an `OB-` namespace** — two grammars in one project is the exact outcome ADR-P0-013 exists to prevent |
| **ADR-P1-015** — minSdk 26 provisional, "expires at Phase 2" | **accepted — revisit required**, `phase-1/decisions.md:105-109`; open item blocking Phase 2, `docs/decisions/README.md:67` | **Yes, but it is a debt Phase 2 owns, not may defer** | An ADR that (a) confirms 26 on the user's decision, (b) states that the permission resolver therefore must model **both** the legacy 26–30 and the API 31+ runtime models, (c) names the evidence used (SEC-PERM-002's table, re-verified per SEC-PERM-003), and (d) resolves the duplicated band constants — `ApiRange.MIN_SUPPORTED = 26` / `MAX_SUPPORTED = 35` (`BluetoothPermission.kt:55-56`) vs `libs.versions.toml:13-14` — declaring which is authoritative. Then mark ADR-P1-015 `closed by ADR-P2-…` in **both** places (index rule 2, `docs/decisions/README.md:58`) |
| **`ADR-P2-004`**, cited by the working-copy edit to `OmniBudsErrorCategory.kt:6-8` | **Does not exist.** No `docs/phases/phase-2/decisions.md`; no index row | No — a code comment already points at a decision that has not been written | Write the ADR (or renumber) before the categories ship, per ARCH-GOV-002 |
| **Phase 0 `specs.md` §5.5** — "cold flows are not used for device state; every flow declares buffering and overflow behaviour" (`specs.md:145`) | **Conflicts with the in-flight design.** `AdapterStateObserver.observe()` returns `channelFlow { … }` (`AdapterStateObserver.kt:54`), which is a cold flow, and no buffering/overflow policy is declared. Prompt §5.2's illustrative `Flow<BluetoothAdapterState>` (`execution-prompt.md:280`) is cold too, so the conflict is between the prompt and Phase 0 — and Phase 0's rule wins (ARCH-DOC-002) | **Not without an ADR.** The cold shape is arguably right for a *registration-owned* stream (start = register, stop = unregister, which is exactly what §5.4 demands and what `StateFlow`/`SharedFlow` cannot express). But "arguably right" is a boundary change | Either (a) an ADR amending §5.5 for adapter state specifically, stating that a per-collector registration is not "device state" and therefore cold is correct, and declaring the buffer policy; or (b) split it: hot `StateFlow<BluetoothAdapterState>` for latest value plus a `SharedFlow<AdapterStateObservation>` for transitions, with explicit `extraBufferCapacity`/`onBufferOverflow`. Recommend (a) with the single-collector guard moved to the source (§ R-5), because (b) makes duplicate-registration *easier* to hit |
| **ADR-P1-001** — "no `:bluetooth:*` module until Phase 6" | Accepted (`phase-1/decisions.md:11`). Phase 2 putting a `bluetooth` **area** in `:core` and packages in `:platform:android` does not contradict it — areas are not modules (`architecture-governance.md:19`, ARCH-DOC-005) | **Yes** | Say so in the ADR so a later reader does not mistake the area for a module decision |

---

## 8. Compatibility recommendations and risk list, ordered by severity

**Findings R-1 … R-8 are blockers or near-blockers for writing any further Phase 2 code. Ordered by severity (probability × impact, continuing the project register).**

| # | Risk | Evidence | Action the orchestrator must take before implementation |
|---|---|---|---|
| **R-1** | **`:core` does not compile.** Main source imports `kotlinx.coroutines.*`; `:core` declares zero production dependencies and the catalog has no `kotlinx-coroutines-core`. Also suspect: an import of a `Mutex` member | `AdapterStateObserver.kt:6-9`, `AdapterStateSource.kt:6` vs `core/build.gradle.kts:25-35`, `libs.versions.toml:17-21` | Decide the ADR-P1-021 discharge first, then add `kotlinx-coroutines-core` at the existing 1.9.0 ref as `api` on `:core` with the recorded reason. Fix or confirm the `sync.tryLock` import. Build before adding another file |
| **R-2** | **Undocumented Phase 2 work is already in the tree, uncommitted, in an unregistered area, with no phase documents.** `ADR-P2-004` is cited by a code comment and does not exist | `git status --porcelain -uall` (11 untracked `.kt` + 1 modified tracked file); `docs/phases/phase-2/` holds only `execution-prompt.md`; `OmniBudsErrorCategory.kt` working-copy KDoc | Adopt-or-set-aside decision, then the ADRs (§2.4, §4.3, §5.2, §7) **before** more code. `README.md:19` ("Phase 2 … has not started") and `docs/security/device-access-policy.md:44-48` ("when Phase 2 was implemented and validated") cannot both stay true |
| **R-3** | **Four architecture checks fail and one passes vacuously.** New area unregistered; `Bluetooth*` identifier scan now hits domain vocabulary; `platformAndroidModuleStillContainsNoSources` fails by construction | `DependencyDirectionTest.kt:189-205`, `:208-213`, `:124-136`, `:264-271` | Apply §5.3's replacements. Every revised check keeps its original claim and states the new scope; **no check is deleted**. Add the manifest-permission check as the substitute for "declares nothing" |
| **R-4** | **Error vocabulary widened 16 → 24 with three tests failing, eight `invalidatesSession` values unasserted, and a state value smuggled in as a category** | `OmniBudsErrorCategory` diff; `OmniBudsErrorCategoryTest.kt:21-31`, `:34-60`, `:62-67`, `:76-102`; `validation.md:143` | §4.3 in full: pin all 24 (or 23), delete `ADAPTER_STATE_UNKNOWN` in favour of `BluetoothAdapterState.UNKNOWN` + `Success`, convert the invalidation lists into one exhaustive map, reconcile `CONNECTION_UNAVAILABLE` against `DEVICE_DISCONNECTED`, write ADR-P2-004 |
| **R-5** | **§5.8's "no duplicate concurrent adapter observers" is not actually guaranteed.** The single-observer slot is per-instance state on a class nothing forces to be a singleton; two injected observers = two platform registrations, which is the leak the rule exists to stop | `AdapterStateObserver.kt:37,41,54-66`, `isObserving` at `:44-45` | Move the guard onto `AdapterStateSource` (the platform object is the real singleton), or make exactly-one-observer a composition-root invariant with a test. Also declare the buffering/cold-flow policy per Phase 0 §5.5 |
| **R-6** | **A production default reports a hardware fact it cannot know.** `AdapterStateSource.unavailable()` returns `ADAPTER_UNAVAILABLE` — "this phone has no usable adapter" — for the "no source was wired up" case. That is a fabricated negative, the founding prohibition | `AdapterStateSource.kt:34-36`, `:47-65`; `BluetoothAdapterState.kt:20-21` (`UNAVAILABLE` = "no adapter"); ADR-P0-001, ARCH-QUAL-011, ARCH-TERM-004, master §53 | Remove the default, or return `Success(UNKNOWN)` / a distinct wiring failure. Never let an un-wired seam assert that the user's phone lacks Bluetooth |
| **R-7** | **Cancellation is relabelled as a platform exception, and a teardown failure is emitted into a channel that may already be closed.** Both corrupt the outcome semantics Phase 1 fixed | `AdapterStateObserver.kt:92-103` (`OperationOutcome.Cancelled` → `PLATFORM_EXCEPTION`) vs ADR-P1-004 / specs §5.3 ("a cancelled operation reports CANCELLED, not success"); `:135-151` `send()` inside `finally` | Keep cancellation as `Cancelled` end-to-end; report cleanup failure through a channel that survives cancellation (e.g. a side-channel callback or the platform logger), or state in the design that a teardown failure during cancellation is dropped — but not silently |
| **R-8** | **`ObservationKind` mislabels the first genuine transition.** After a successful initial read, the first real state *change* is tagged `INITIAL_EVENT`, i.e. "this is where we started" — the exact distinction the type exists to preserve | `AdapterStateObserver.kt:108-128` (`announcedFirstChange` starts false) vs `AdapterStateObservation.kt:6-10`; also `DEDUPED` (`:9-10`, `:17`) is documented as never emitted, so it is unconstructible vocabulary | Fix the flag to mean "no initial read succeeded". Add a test for `initial=ENABLED, event=ENABLED, event=DISABLED` asserting the third element is `PLATFORM_EVENT` |
| R-9 | **Two runtime facts have two representations:** `ApiRange.MIN_SUPPORTED/MAX_SUPPORTED` vs `libs.versions.toml` `androidMinSdk`/`androidTargetSdk` | `BluetoothPermission.kt:55-56,59-62` vs `libs.versions.toml:13-14` | One owner; inject the band or rename the constants to what they actually mean |
| R-10 | **The project's phase plan is baked into shipped domain data**, and enforcing it at runtime needs an ambient "current phase" value | `BluetoothOperation.kt:14-89` (`authorizedInPhase`), `:11` (resolver gating), ARCH-STATE-003 | Replace with a Phase 2 test that asserts no unauthorised operation is reachable; keep the enum as *permission metadata* only |
| R-11 | **§5.5's four axes are three.** No host-hardware type exists, and an orphaned KDoc describes the missing one | `ApiAvailability.kt:25-36`; prompt `execution-prompt.md:382-392` | Add the host-axis type + the aggregate `BluetoothPlatformCapabilities` record; state in `specs.md` that it carries zero codec and zero device claims (§7 audio isolation) |
| R-12 | **`:platform:android` has no test framework**, so prompt §9's "Android-specific tests" and §5.9's fake-platform-dependency testing have nowhere to run | `platform/android/build.gradle.kts:38-42` | Add existing catalog entries as `testImplementation` in that module, with the recorded reason. Do **not** add Robolectric or androidx.test purely to run glue tests — prompt §5.9 and ADR-P1-011 argue against it, and `device-access-policy.md:46` confirms no device/AVD was available |
| R-13 | **Android API/permission facts are being modelled from memory.** `BLUETOOTH`/`BLUETOOTH_ADMIN` at `introducedAtSdk = 18` (`BluetoothPermission.kt:30-31`) and the `isLegacyForTargeting31Plus` asymmetry are plausible but unverified here, and no device or emulator was touched | `execution-prompt.md:330-331`; `security-governance.md:53` (SEC-PERM-003: "remembered behaviour is not evidence"), `docs/security/device-access-policy.md:46` | Agent B must re-verify every name, level and `ApiRange` boundary against current platform documentation and cite the source in `specs.md`. Until then every such row is `VerificationLevel.INFERRED`, never `IMPLEMENTED` — and the ceiling must appear in `validation.md` |
| R-14 | **Manifest pressure toward unauthorised permissions.** The matrix models `BLUETOOTH_SCAN` (Phase 3 scanning) while §6 forbids scanning; declaring it now is a permission without a use | `BluetoothPermission.kt:32`, `BluetoothOperation.kt:60-64`; prompt §6 lines 476-509; SEC-PERM-004 | Declare only what adapter inspection genuinely needs, justified line by line in Phase 2's security section, and pin the allowed set with the R-3 manifest check |
| R-15 | **Convention drift in the new code:** a >120-column comment line was added to a tracked file; three `BluetoothPermission.kt` enum lines exceed 120 columns; the tracked edit introduced CRLF into a file `.gitattributes` normalises to LF; `PermissionState.kt:39-45`'s KDoc says NOT_REQUIRED *and a genuine grant* allow silent proceeding while the code returns true only for `NOT_REQUIRED` | `.editorconfig` (120 col, per `phase-1/specs.md:19`), `.gitattributes` (`*.kt eol=lf`), `git diff` CRLF warning; `PermissionState.kt:39-45` | Fix while the code is being repaired. The KDoc/code mismatch is the one that matters: it is a permission-semantics claim stated backwards, and specs §11 requires every KDoc claim to be honest |

**Cross-cutting recommendation.** Prompt §2 asked whether any required Phase 1 artifact is missing. Two are, and neither is Phase 1's fault: `docs/phases/phase-0/bluetooth-governance.md` is named as a binding sibling in `architecture-governance.md:248` but does not exist in `docs/phases/phase-0/` (verified by listing), and `docs/phases/phase-0/device-support-matrix.md` is already recorded as *proposed, not existing* (`protocol-governance.md:42`, D8). Phase 2 is the Bluetooth phase and its rulebook is absent. **The orchestrator should treat prompt §5 and §7 as the operative Bluetooth rules and record the missing sibling in Phase 2's `risk-register.md`, rather than inventing the file.** Neither blocks Phase 2.

**Verdict on the boundary itself: Phase 2 can proceed safely — but the tree cannot.** Nothing in the Phase 1 architecture has to be weakened to admit the Android Bluetooth foundation; the seams ARCH-AND-002 asked for ("an adapter state source and a permission gate") are the right shape and are partly built. What blocks Phase 2 is that it has already been started out of order: code before decisions, vocabulary before tests, a new area before its layer-map ADR, and a `:core` dependency before its recorded reason. R-1 through R-4 must be closed before R-5 onward are worth fixing, because everything after them is being written against a non-compiling, red-checked baseline.

**Scope confirmation:** this audit recommends nothing from prompt §6's forbidden list. No scanning, no connection, no GATT/RFCOMM traffic, no vendor protocol, no audio path change, no UI, and no hardware claim is proposed above — the only new artifacts recommended are interfaces, one dependency with a reason, revised architecture checks, and ADRs. **Phase 2 implementation must not begin until the orchestrator settles R-1/R-2.** This document is an audit, not an authorisation (`architecture-governance.md:244`, ADR-P0-009).

---

## Addendum — changes observed during the audit (19:01 → 19:10:51)

The audit body above is accurate to its 18:57–19:01 snapshot. Implementation continued while it was being written, so this addendum records what moved and what is now **verified** rather than inferred. `:platform:android` still contains **zero** Kotlin sources; all new code remains in the `:core` `platform` area, now 15 files.

**Closed while I watched (credit where due):**

- **R-1 addressed.** `api(libs.kotlinx.coroutines.core)` is now declared at `core/build.gradle.kts:30`, and `kotlinx-coroutines-core` added to `gradle/libs.versions.toml:18-20` on the existing 1.9.0 ref — with a comment citing "ADR-P2-003" and discharging ADR-P1-021's stated trigger, in `api` scope exactly as §5.2 predicted.
- **R-3 addressed in the recommended form, not by deletion.** `DependencyDirectionTest.kt` now registers `"platform" to 1` in `areaLayer` (`:34-42`) — the layer I recommended; the area name stayed `platform` despite §2.3's objection, which is the orchestrator's call to make, but the rename argument and the ADR that ARCH-GOV-002 requires have not been recorded anywhere. The old blanket `Bluetooth[A-Za-z0-9_]*` scan was replaced by `coreReferencesNoAndroidFrameworkTypes`, which names the framework classes (the list I proposed, plus `BluetoothServerSocket`, `BluetoothLeScanner`, `BluetoothA2dp`, `BluetoothHeadset`) and keeps all four media-audio types banned. `platformAndroidModuleStillContainsNoSources` was replaced rather than removed by `platformModuleContainsNoUnauthorisedCapabilities`, which bans `connectGatt`/`startDiscovery`/`startScan`/`createRfcommSocket`/`TileService`/`AppWidgetProvider`/UI artifacts in the platform module, plus a further media-audio rule and a build-script check that `:platform:android` depends on `:core`. That is the honest revision §5.3 asked for.
- Four more seam files appeared, covering gaps in §3.2: `BluetoothPlatform.kt`, `BluetoothPlatformCapabilities.kt`, `PlatformFeature.kt`, `PlatformFeatureSupport.kt`. A permission-*state provider* seam and a requirement *resolver* still need confirming by reading them; the resolver was still absent at the last sweep even though two KDocs link it.

**Now verified worse than reported (these were inferences in the body, and they are facts):**

- **R-4's silent failure is confirmed and partly worse.** `OmniBudsErrorCategoryTest.kt:76-102`'s successor (`categoriesThatLeaveTheDeviceStateUncertainSaySo`, now `:94-124`) is still two hand-written lists rather than one exhaustive map. Five new categories were added to the `assertFalse` list; **`CONNECTION_UNAVAILABLE`, `PLATFORM_EXCEPTION` and `UNKNOWN_FAILURE` — the three new categories carrying `invalidatesSession = true` — were added to neither list.** They remain unasserted. Phase 1 known issue 6 (`validation.md:143`) has therefore recurred in the very commit that was supposed to outgrow it. §4.3 step 2 is still outstanding.
- **A safety invariant was weakened to fit the code.** `onlyIdempotentReadsMayBeRetriedWithoutCheckingStateFirst` (`:79-83`) now asserts `setOf(READ_FAILED, ADAPTER_STATE_UNKNOWN)` are the `SAFE_TO_RETRY` members. The test's *name* is the rule ("idempotent reads"): widening its expected set so that an "adapter state unknown" category may be retried blindly does not fix the failure, it redefines the safety property. This is the §4.3 step 3 finding — delete `ADAPTER_STATE_UNKNOWN` as a category and keep `BluetoothAdapterState.UNKNOWN` as the state value — and it is now a **governance** finding as much as a modelling one: ARCH-QUAL-015 (`architecture-governance.md:229`) forbids silent architecture changes, and a guard was relaxed at the assertion rather than at the design.
- **`ADR-P2-003` and `ADR-P2-004` are cited by committed-intent files (`core/build.gradle.kts:26-28`, `libs.versions.toml:18-19`, `OmniBudsErrorCategory.kt:6-8`) and exist in no `decisions.md`,** including this audit's own directory, which still holds only `execution-prompt.md` and this file. The dependency reason, the vocabulary change, the new area, the mutable `Mutex` holder, and the cold-flow amendment to Phase 0 `specs.md` §5.5 are all still undocumented decisions with numbers attached to them.

**Net effect on the verdict.** The boundary work is heading the right way and the checks are being revised honestly rather than deleted, which is the main thing this audit asked for. Nothing in the addendum changes §8's conclusion: the phase is proceeding ahead of its ADRs, so R-2 (adopt-and-document) and R-4 (the three unpinned `true` categories, plus `ADAPTER_STATE_UNKNOWN`) are the two items to close before further implementation.

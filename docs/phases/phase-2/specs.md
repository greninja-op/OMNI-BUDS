# Phase 2 — Specs (contract reference, as realised)

**Phase:** 2 — Android Bluetooth Foundation · **Scope id:** `P2` · **Owner agent:** Phase 2 documentation workstream
**Document status:** written from the working tree at `main` / HEAD `608eba5`; the uncommitted Phase 2 paths are listed in `design.md` §0. Every signature below is copied from the file it cites, not paraphrased; every member of every enum is listed; every numeric value has a source. `design.md` §0 records one limit that matters here: the observer, its tests, the architecture checks and the capability provider were being edited *while* this file was written, so a line number in `AdapterStateObserver.kt`, `AdapterStateObservation.kt`, `SystemBluetoothAdapterHandle.kt`, `AndroidPlatformCapabilityProvider.kt` or `DependencyDirectionTest.kt` is the likeliest place for drift, and drift there is an erratum against this file.
**Scope.** The 17 files of `core/src/main/kotlin/com/omnibuds/core/platform/` (1,174 lines), the 8 Phase 2 files of `core/.../core/transport/` (the area is 12 files / 834 lines), the Phase 2 changes to `common/`, the 13 files of `platform/android/src/main/kotlin/com/omnibuds/android/` (929 lines) with their manifest and build script, `core/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, and the architecture test that enforces them. Section numbering continues Phase 1's `specs.md` so a citation of the form "specs.md section 2.2" in source KDoc still resolves.
**Authority.** `docs/MASTER-CONTEXT.md` → Phase 0 → Phase 1 → `docs/phases/phase-2/decisions.md` (ADR-P2-001 … ADR-P2-018, by id, never restated) → this file. Deviations are marked ⚠ and §17 collects them. **No hardware, adapter, GATT, RFCOMM, scan, bond list, battery, codec or audio path was touched**; nothing here is `LAB_TESTED` or higher about Android, and the platform facts belong to `docs/phases/phase-2/bluetooth-api-research.md` (documentation tier) or to the SDK's own API table named at `platform/android/src/main/kotlin/com/omnibuds/android/bluetooth/capability/AndroidPlatformCapabilityProvider.kt:202-205`.

## 0. What enforces what

| Enforced by the build | Enforced by review or prose only |
|---|---|
| Kotlin compiles for JVM target 17, AGP 8.7.3, Gradle 8.9 (`gradle/wrapper/gradle-wrapper.properties:3`); `allWarningsAsErrors = true` in both modules (`core/build.gradle.kts:15`, `platform/android/build.gradle.kts:36`) | whether a KDoc sentence about the platform matches the code. §16 names the three places where it does not |
| 12 architecture rules / 17 checks in `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt` — §7 of `design.md` and §15 below say which token lists Phase 2 changed | whether the composition is created once per process (`OmniBudsBluetooth.kt:34-38` states it; nothing checks it) |
| No Android framework type name in `:core` code lines (rule 2, `:146-159`); no media-audio type name in either module (rule 8, `:321-340`) | whether a `detail` string carries an identifier — Phase 2 emits only fixed sentences and `${problem::class.simpleName}` |
| Platform sources confined to `bluetooth/` and `di/` (rule 11, `:412-435`, which **fails when the module has no sources**, `:413-420`); manifest carries no permission and no component (rule 11, `:438-457`) | whether a seam's default implementation is the honest one |
| No unauthorised capability token in the platform module (rule 7, `:292-316`, whose list now includes `requestPermissions` and `requestPermission` at `:301`); receiver types confined to `bluetooth/adapter/` and no send-capable broadcast API anywhere (rule 10, `:383-407`); no UI framework token in either module (rule 9, `:352-374`) | whether a `PlatformFeature` row deserves `UNKNOWN` rather than a neighbour's answer (argued per row at `AndroidPlatformCapabilityProvider.kt:130-146`, decided by ADR-P2-017) |
| The 23-category error set, and `retryClass` **and** `invalidatesSession` for every one of them, through two exhaustive maps guarded by `assertFullCoverage` (`core/src/test/kotlin/com/omnibuds/core/common/OmniBudsErrorCategoryTest.kt:40-128`, ADR-P2-005) | whether a category that exists is ever produced — §8's last column |
| Area layer direction, area registration, package-equals-directory (rules 4 and 6, `:213`, `:232`, `:265`) | the `state` layer-1 → layer-0 move, which no ADR records (`DependencyDirectionTest.kt:34-37`; `design.md` §3) |
| Five transport boundaries pin five distinct `TransportKind`s, none `UNKNOWN`, none `VENDOR_SPECIFIC` (`TransportKindPinningTest.kt:23-49`); `probeAvailability` declared exactly once and called nowhere, and no mechanism name in the package (`TransportBoundariesTest.kt:331-356`) | whether an empty marker interface should stay empty — each file argues its own case in its KDoc |
| The five authorised Phase 2 operations require nothing, prompt nobody and never ask for location (`PhaseTwoScopeTest.kt:55-68`, `PermissionRequirementResolverTest.kt:150-166`); an undetermined target yields `INVALID_STATE`, not a plan (`PhaseTwoScopeTest.kt:88-97`) | whether a later phase may *perform* a pre-computed operation — `authorizedInPhase` is data, and ADR-P2-014 makes the test the enforcement |
| Single-slot refusal, dedup, kind discipline, teardown-on-every-exit and cancellation propagation, all against a scripted source (`AdapterStateObserverTest` — 16 cases, listed in §14) | dispatcher choice: no rule inspects one, and `:core` is forbidden from exposing one |

Because both modules fail the build on a warning, an edit that leaves an unused import in a touched file is a broken build (Phase 1 `specs.md` §0, unchanged).

## 1. Naming conventions as realised (inherits Phase 0 §1, Phase 1 §1)

| Kind | Rule as Phase 2 built it | Examples with file:line |
|---|---|---|
| Core seam | `<Subject>Source`, `<Subject>Observer`, `<Subject>Resolver`, `<Subject>Registration`, `<Subject>Provider` | `AdapterStateSource.kt:19`, `AdapterStateObserver.kt:36`, `PermissionRequirementResolver.kt:35`, `PlatformRegistration.kt:12`, `TimeProvider.kt:11` |
| Android implementation of a core interface | `Android<Thing>` | `AndroidBluetoothPlatform.kt:35`, `AndroidAdapterStateSource.kt:35`, `AndroidPermissionStateProvider.kt:35`, `AndroidPlatformCapabilityProvider.kt:65` |
| Android implementation that touches the framework | `System<Thing>` | `SystemBluetoothAdapterHandle.kt:30`, `SystemApiLevelProvider.kt:21`, `SystemPermissionStandingReader.kt:27`, `SystemPlatformFeatureProbe.kt:24`, `SystemTargetSdkProvider.kt:35`, `SystemTimeProvider.kt:14`, `SystemPlatformFeatureProbe` |
| ⚠ Implementation suffix | The frozen resolver is `FrozenPermissionRequirementResolver` (`PermissionRequirementResolver.kt:57`), not `…Impl`: "frozen" says something about the design — a table transcribed from the research — where `Impl` would say nothing, which is the test Phase 0 §1.1 applies | — |
| Class rather than interface | Where there is no second platform to abstract over yet: `AndroidPermissionStateProvider`, `AndroidPlatformCapabilityProvider`, `AdapterStateObserver`, `InMemoryPermissionRequestLedger` | `AndroidPermissionStateProvider.kt:35`, `AndroidPlatformCapabilityProvider.kt:65`, `AdapterStateObserver.kt:36`, `PermissionRequestLedger.kt:24` |
| `fun interface` | Every one-method seam, including the lambda-shaped Android reads — which is why tests replace them without a mocking framework | `TimeProvider.kt:11`, `ApiLevelProvider.kt:15`, `TargetSdkProvider.kt:30`, `PlatformFeatureProbe.kt:19`, `PermissionStandingReader.kt:14`, `PermissionRequestLedger.kt:19` |
| Derived boolean | `is`/`has`/`can`/`allows`/`permits` prefixes stay reserved for facts computed from state, per Phase 1 §1.1's ⚠ amendment: `isUsable`, `isObserving`, `isIndeterminate`, `isProvablyDisabled`, `isGranted`, `isRefused`, `allowsSilentProceeding`, `needsNoPermission`, `isUndetermined`, `isActive` | `BluetoothAdapterState.kt:37-44`, `PermissionState.kt:37-51`, `AdapterStateObserver.kt:43`, `PermissionRequirementResolver.kt:30-31`, `TransportNegotiation.kt:72`, `PlatformRegistration.kt:14` |
| Stored boolean | Bare adjective or asserted fact in a constructor: `adapterPresent`, `required`, `assertsNeverForLocation`, `available`, `indeterminate`, `isRuntimePermission`, `isLegacyForTargeting31Plus` | `BluetoothAdapterHandle.kt:21`, `PermissionRequirement.kt:15`, `PermissionContext.kt:31`, `PermissionRequirementResolver.kt:19`, `BluetoothPermission.kt:25,28` |
| Transition function | `with…` returns a copy, never mutates: `withFeature`, and Phase 1's `withCapability` family | `BluetoothPlatformCapabilities.kt:52-53` |
| Factory | `unavailable`, `unobserved`, `unknown`, `established`, `refused`, `available` | `AdapterStateSource.kt:35`, `BluetoothPlatformCapabilities.kt:57`, `PlatformFeatureSupport.kt:57`, `TransportBoundary.kt:102,119`, `TransportAvailability.kt:67` |
| Test class | `<Subject>Test`; the phase-boundary guard is `PhaseTwoScopeTest`, the architecture guard `DependencyDirectionTest` | `core/src/test/kotlin/com/omnibuds/core/platform/`, `.../architecture/` |
| ⚠ Test method | behaviour-plus-condition lowerCamel, no backticks, and **no `TEST-P2-<NNN>` id in any method name** — ids are issued by `test-plan.md`, a different workstream's file. The convention note is in `TransportBoundariesTest.kt:28-29` | `aCancelledReadIsReportedAsCancellationNotAsABrokenAdapter`, `theFirstChangeAfterASuccessfulReadIsAPlatformEventNotAnInitialOne`, `phaseTwoOperationsRequireNoPermissionForAModernTarget` |
| operationId strings | kebab-case, subject-first, one owner per string | `AdapterStateSource.kt:52,61` (`adapter-source.*`), `AdapterStateObserver.kt:182` (`adapter-observer.observe`), `AndroidAdapterStateSource.kt:97` (`android-adapter-source`), `AndroidPlatformCapabilityProvider.kt:200` (`android-platform-capabilities`), `PermissionRequirementResolver.kt:67` (`permission-resolver.plan`) |
| Area name | `com.omnibuds.core.platform` (ADR-P2-001). The audit §2.3 recommendation to name it `bluetooth` is unanswered in `decisions.md`; `design.md` §2 records the consequence — this is the one core area whose vocabulary is Android-shaped by necessity | all 17 files |

## 2. Enums, every member

### 2.1 `BluetoothAdapterState` — the host's own state, six members
`core/src/main/kotlin/com/omnibuds/core/platform/BluetoothAdapterState.kt:16-34`: `UNKNOWN` ("not sampled, unreadable, or refused — not evidence that Bluetooth is unavailable"), `UNAVAILABLE` ("no usable adapter, or the platform could not obtain one"), `DISABLED`, `ENABLING`, `ENABLED`, `DISABLING`. Derived predicates (`:37-44`): `isUsable()` true only of `ENABLED`; `isProvablyDisabled()` only of `DISABLED`; `isIndeterminate()` of `UNKNOWN` **and** `UNAVAILABLE`. No transition table exists for this enum — §7.

### 2.2 `ObservationKind` — how a value was obtained, four members
`AdapterStateObservation.kt:16-21`: `INITIAL_READ`, `INITIAL_EVENT`, `PLATFORM_READ`, `PLATFORM_EVENT`. Realised semantics, from `AdapterStateObserver.kt:85-138` and asserted at `AdapterStateObserverTest.kt:92-111` and `:160-182`: `PLATFORM_READ` comes only from `readOnce()` (`:176`); `INITIAL_READ` is a successful first `readState()`; **`INITIAL_EVENT` is the first differing event of a stream whose initial read did not answer**, and `PLATFORM_EVENT` is every later one — "a successful `INITIAL_READ` is the starting value, so `INITIAL_EVENT` never follows it" (`AdapterStateObservation.kt:8-10`, `AdapterStateObserver.kt:79-82`). ⚠ This is a Phase 2 correction made while this document was being written: an earlier shape tagged the first change after a successful read as `INITIAL_EVENT`, which is audit finding R-8, now closed. There is deliberately **no** `DEDUPED` member: suppressed duplicates never reach a consumer, and "an enum entry nothing can produce is vocabulary standing in for a capability" (`AdapterStateObservation.kt:12-14`, ADR-P2-007).

### 2.3 `PermissionState` — seven members, the prompt's list verbatim
`PermissionState.kt:13-34`: `NOT_REQUIRED`, `NOT_REQUESTED`, `GRANTED`, `DENIED`, `DENIED_PERMANENTLY`, `REQUIRES_USER_ACTION`, `UNKNOWN`. Predicates (`:37-51`): `isGranted()` only `GRANTED`; `allowsSilentProceeding()` **only `NOT_REQUIRED`** — the KDoc sentence at `:42-43` also names "a genuine grant", the code does not, and `isGranted()` is the separate predicate that a grant is read through (`PlatformFeatureSupport.kt:33-36` ORs the two); `isRefused()` covers `DENIED`, `DENIED_PERMANENTLY`, `REQUIRES_USER_ACTION`. Produced by app code: `NOT_REQUIRED`, `NOT_REQUESTED`, `GRANTED`, `DENIED`, `UNKNOWN` (`AndroidPermissionStateProvider.kt:42-62, 88`). `DENIED_PERMANENTLY` and `REQUIRES_USER_ACTION` are unreachable from app code by ADR-P2-012 and asserted forbidden (`AndroidPermissionStateProviderTest.kt:71-87`, `AndroidPlatformCapabilityProviderTest.kt:134-145`).

### 2.4 `ApiAvailability` — three members
`ApiAvailability.kt:14-23`: `UNKNOWN`, `AVAILABLE`, `UNAVAILABLE`. Answers the **OS axis only**. Companion function (`:32-36`):

```kotlin
fun apiLevelSupports(sdkInt: Int?, minimumSdk: Int): ApiAvailability = when {
    sdkInt == null || minimumSdk <= 0 -> ApiAvailability.UNKNOWN
    sdkInt >= minimumSdk -> ApiAvailability.AVAILABLE
    else -> ApiAvailability.UNAVAILABLE
}
```

Called twice, both in the platform module (`AndroidPlatformCapabilityProvider.kt:157,158`). ⚠ The KDoc block above it (`ApiAvailability.kt:25-31`) describes the *phone-hardware* axis and then documents this OS-axis function; the hardware axis is carried by `PlatformFeatureSupport.hardwareEvidence` (§3, ADR-P2-008/017). The mismatch is on disk and is not glossed here.

### 2.5 `PlatformFeature` — eight members, each with a stable `technicalName`
`PlatformFeature.kt:10-22`: `CLASSIC_BLUETOOTH("platform.classic-bluetooth")`, `BLE_CENTRAL("platform.ble-central")`, `GATT_CLIENT("platform.gatt-client")`, `RFCOMM_CLIENT("platform.rfcomm-client")`, `LE_AUDIO("platform.le-audio")`, `A2DP_CONNECTION_STATE("platform.a2dp-connection-state")`, `HEADSET_CONNECTION_STATE("platform.headset-connection-state")`, `ADAPTER_STATE_OBSERVATION("platform.adapter-state-observation")`. These name **phone** capabilities and are a different type from `common/FeatureId`, which names something a *device* can do (`:6-8`) — the split that stops "this phone has LE Audio APIs" being rendered as "your earbuds support LE Audio".

### 2.6 `BluetoothPermission` — six members with four attributes each
`BluetoothPermission.kt:17-36`: manifest name, `introducedAtSdk`, `isRuntimePermission`, `isLegacyForTargeting31Plus`.

| Member | `manifestName` | `introducedAtSdk` | runtime? | legacy for t≥31? |
|---|---|---|---|---|
| `BLUETOOTH` | `android.permission.BLUETOOTH` | 18 ⚠ | no | yes |
| `BLUETOOTH_ADMIN` | `android.permission.BLUETOOTH_ADMIN` | 18 ⚠ | no | yes |
| `BLUETOOTH_SCAN` | `android.permission.BLUETOOTH_SCAN` | 31 | yes | no |
| `BLUETOOTH_CONNECT` | `android.permission.BLUETOOTH_CONNECT` | 31 | yes | no |
| `ACCESS_FINE_LOCATION` | `android.permission.ACCESS_FINE_LOCATION` | 1 | yes | no |
| `ACCESS_COARSE_LOCATION` | `android.permission.ACCESS_COARSE_LOCATION` | 1 | yes | no |

⚠ The two legacy rows are **not supported by this phase's research**: `bluetooth-api-research.md` §2 records `BLUETOOTH` and `BLUETOOTH_ADMIN` as existing since API 1, citing `Manifest.permission`, and audit R-13 had already flagged 18 as unverified. Neither field is read by any code path in Phase 2 — no resolver branch, no provider, no test consults `introducedAtSdk`, and `isLegacyForTargeting31Plus` is likewise unread (the band decision comes from `PermissionContext.targetSdk`, §6) — so no behaviour depends on it, and the number is owed a correction by whoever first reads it.

### 2.7 `BluetoothOperation` — twelve members, five authorised here
`BluetoothOperation.kt:18-93`, each carrying `technicalName`, `authorizedInPhase`, `description`:

| Member | `technicalName` | phase |
|---|---|---|
| `ADAPTER_AVAILABILITY_INSPECTION` | `adapter.availability-inspection` | 2 |
| `ADAPTER_STATE_INSPECTION` | `adapter.state-inspection` | 2 |
| `ADAPTER_STATE_OBSERVATION` | `adapter.state-observation` | 2 |
| `PLATFORM_CAPABILITY_INSPECTION` | `platform.capability-inspection` | 2 |
| `PERMISSION_STATUS_INSPECTION` | `platform.permission-status-inspection` | 2 |
| `CONNECTED_DEVICE_INSPECTION` | `device.connected-inspection` | 3 |
| `BONDED_DEVICE_LIST_INSPECTION` | `device.bonded-list-inspection` | 3 |
| `DEVICE_DISCOVERY_SCAN` | `device.discovery-scan` | 3 |
| `PROFILE_CONNECTION_STATE_INSPECTION` | `profile.connection-state-inspection` | 3 |
| `TRANSPORT_GATT_OPEN` | `transport.gatt-open` | 6 |
| `TRANSPORT_RFCOMM_OPEN` | `transport.rfcomm-open` | 6 |
| `LE_AUDIO_SESSION_INSPECTION` | `leaudio.session-inspection` | 10 |

`fun isAuthorizedIn(phase: Int): Boolean = authorizedInPhase <= phase` (`:92`) is consulted only by `PhaseTwoScopeTest` (`core/src/test/.../platform/PhaseTwoScopeTest.kt:36,43,50-52`) — ADR-P2-014. The five Phase 2 rows are asserted to be exactly the authorised set (`:34-39`) and to require nothing (`:55-68`).

### 2.8 ⚠ `TransportKind` — seven members; `BLE` added by ADR-P2-013
`common/TransportKind.kt:11-36`: `BLE`, `GATT`, `RFCOMM`, `CLASSIC_BLUETOOTH`, `LE_AUDIO`, `VENDOR_SPECIFIC`, `UNKNOWN`. `BLE` is new in Phase 2 and its KDoc (`:12-16`) gives the reason: the BLE link layer and the GATT attribute protocol that may sit on it are different things (master section 8). This amends Phase 1's six-member pinning. `VENDOR_SPECIFIC` still has no mechanism behind it, and no boundary may pin it (`TransportKindPinningTest.kt:38`).

### 2.9 `OmniBudsErrorCategory` — 23 members, table in §8.

### 2.10 Named constants — the only numeric literals Phase 2 declares

| Constant | Value | Where | Source of the number |
|---|---|---|---|
| `ApiRange.MIN_MATRIX_SDK` | 26 | `BluetoothPermission.kt:91` | the band the matrix covers; deliberately not named `minSdk` (`:55-60`) |
| `ApiRange.MAX_MATRIX_SDK` | 35 | `:92` | ditto |
| `ApiRange.LEGACY_BLUETOOTH_MODEL` | `26..30` | `:95` | research §1 band naming — read by every legacy-band row the resolver builds |
| `ApiRange.MODERN_BLUETOOTH_MODEL` | `31..35` | `:98` | ditto — read by every modern-band row the resolver builds |
| `PermissionContext.MODERN_MODEL_TARGET_SDK` | 31 | `PermissionContext.kt:39` | research §3 (`Build.VERSION_CODES.S`) — read by `usesModernBluetoothModel`, which is now the resolver's single band decision (`PermissionRequirementResolver.kt:74`) |
| ~~`PermissionContext.ALIAS_API_LEVEL`~~ · ~~`PermissionContext.RECEIVER_EXPORT_TARGET_SDK`~~ | 30 · 34 | deleted at close-out | both were transcribed from research and read by nothing, in code or in a test. A named value in the model that no code consults reads like an enforced rule that does not exist, so they were removed rather than kept as comments; the receiver policy they described is stated in `SystemBluetoothAdapterHandle.register()`'s KDoc, next to the branch it explains |
| `FrozenPermissionRequirementResolver.MODERN_TARGET` / `LEGACY_MAX` | 31 / 30 | `PermissionRequirementResolver.kt:265-266` | now only the band *label* and the device-level caveat test; the decision itself comes from `PermissionContext.usesModernBluetoothModel`, so 30/31 has one owner (ADR-P2-009) |
| `SystemBluetoothAdapterHandle.RECEIVER_FLAG_API_LEVEL` | 33 | `SystemBluetoothAdapterHandle.kt:114` | research §5 Q6 item R-1: the `Context.RECEIVER_*` **constants** exist from 33 and may be passed from 33; the **requirement** is keyed to targetSdk ≥ 34 |
| `AndroidPlatformCapabilityProvider.BLUETOOTH_SOCKET_API_LEVEL` | 5 | `AndroidPlatformCapabilityProvider.kt:206` | the SDK's `data/api-versions.xml`, as the comment at `:202-205` states |
| `…​.GATT_API_LEVEL` | 18 | `:207` | same |
| `…​.BLUETOOTH_MANAGER_API_LEVEL` | 18 | `:208` | same |
| `…​.LE_AUDIO_API_LEVEL` | 31 | `:209` | same. Research §6 U-7 recommends gating *profile-based* work at 33 (`BluetoothProfile.LE_AUDIO`); Phase 2 calls no profile API, so the floor is used only as an availability statement |
| `mapping.RAW_STATE_UNREADABLE` | `-1` | `AdapterStateMapping.kt:38` | chosen because it is outside the platform's adapter-state range, so `bluetoothAdapterStateOf` resolves it to `UNKNOWN` |
| `AdapterStateObserver.OPERATION_ID` | `"adapter-observer.observe"` | `AdapterStateObserver.kt:182` | not a number; listed because it is the id in every observer-produced error |

No other numeric literal exists in Phase 2 main source: the magic-literal scan (rule 5, `DependencyDirectionTest.kt:247-259`) runs over all of `:core` and finds none.

## 3. Data models

| Type | Fields (name · type · default) | Invariants and notes | File |
|---|---|---|---|
| `AdapterStateObservation` | `state: BluetoothAdapterState` · `kind: ObservationKind` · `observedAtEpochMillis: Long?` | no defaults; `null` time means "the platform supplied no clock reading", never epoch (`:23-28`). Derived `isUsable` (`:34-36`) delegates to the state predicate | `AdapterStateObservation.kt:29-37` |
| `AdapterStateChangeChannel` | `registration: PlatformRegistration` · `states: Flow<BluetoothAdapterState>` | an interface, not a record: the stream and the ownership of its registration must travel together | `AdapterStateSource.kt:40-45` |
| `PermissionContext` | `targetSdk: Int?` · `deviceSdk: Int?` · `assertsNeverForLocation: Boolean = false` | both version facts nullable, neither defaulted; `usesModernBluetoothModel` (`:34-35`) computes `(targetSdk ?: 0) >= 31` and is called nowhere — the resolver keeps its own `modern` value | `PermissionContext.kt:17-32` |
| `PermissionRequirement` | `operation: BluetoothOperation` · `permission: BluetoothPermission` · `appliesTo: ApiRange` · `required: Boolean` · `reason: String` | `require(reason.isNotBlank())` (`:18-22`): a requirement that cannot say why it exists is unconstructible. `appliesAt(sdkInt)` (`:25`) | `PermissionRequirement.kt:11-26` |
| `PermissionPlan` | `operation` · `requirements: List<PermissionRequirement>` · `bandLabel: String` · `indeterminate: Boolean` · `caveat: String? = null` | derived `runtimePermissions` and `installTimePermissions` both filter on `required` (`:23-28`); `needsNoPermission = !indeterminate && requirements.none { it.required }` (`:31`). ⚠ `indeterminate` is only ever set `false` by the one resolver (`:220`); the indeterminate case travels as `Failure(INVALID_STATE)` (`:64-70`) instead. `bandLabel` is written (`:219`) and read nowhere | `PermissionRequirementResolver.kt:15-32` |
| `PlatformFeatureSupport` | `apiAvailability: ApiAvailability` · `hardwareEvidence: VerificationLevel` · `permissionState: PermissionState` | no defaults, all three mandatory. `isUsable` requires `AVAILABLE` **and** `hardwareEvidence >= LAB_TESTED` **and** (granted or not-required) (`:33-36`). `blockingReason` (`:44-53`) is a six-branch `when` naming the first refusal it finds. `unknown()` = `UNKNOWN / INFERRED / UNKNOWN` (`:57-61`) | `PlatformFeatureSupport.kt:24-63` |
| `BluetoothPlatformCapabilities` | `apiLevel: Int?` · `adapterPresent: ApiAvailability` · `private features: Map<PlatformFeature, PlatformFeatureSupport>` · `permissionStatus: Map<BluetoothPermission, PermissionState>` · `candidateTransports: Set<TransportKind>` | `apiLevel > 0` if present (`:34-37`). Sparse map: `supportFor` falls back to `PlatformFeatureSupport.unknown()` (`:41-42`), so an unprobed feature is "not determined", never `UNAVAILABLE`. `supportedFeatures` is a defensive `toMap()` (`:31-32`); `usableFeatures` (`:45-46`) and `isObserved` (`:49-50`) have no reader in the tree. `withFeature` copies (`:52-53`); `unobserved()` is the pre-question state (`:57-63`). Cannot express a codec, a device or a battery | `BluetoothPlatformCapabilities.kt:14-65` |
| `ApiRange` | `minSdkInclusive: Int` · `maxSdkInclusive: Int` | `init` requires the start inside `26..35` and a non-inverted range (`:43-50`) so a hole cannot be created by a missing `else` (`:38-41`); `contains` is inclusive (`:52`) | `BluetoothPermission.kt:42-70` |
| `TransportBoundary` | `kind: TransportKind` · `availability: TransportAvailability` · `supportEvidence: VerificationLevel` · `notes: String?` | `kind == availability.kind`; `available == true` requires `supportEvidence.atLeast(LAB_TESTED)`; `notes` null-or-non-blank (`:69-91`). `established(kind, supportEvidence, notes = null)` requires the tier; `refused(kind, reason, supportEvidence = INFERRED, notes = null)` (`:93-130`) | `TransportBoundary.kt:41-131` |
| `TransportNegotiation` | `candidates: List<TransportBoundary>` · `selected: TransportKind?` | `selected` must appear among candidates **and** appear as an available one (`:47-62`); `isUndetermined` (`:72-73`); `preferring(order)` takes the caller's order, skips refused rows without falling through, and returns null for an empty order (`:90-96`) | `TransportNegotiation.kt:28-97` |

⚠ `TransportAvailability`, `TransportRequest` and `TransportResponse` are unchanged from Phase 1 (`TransportAvailability.kt:28-80` locks `available` and `reason` together; `TransportRequest.kt:26-50` requires a non-blank `commandId` and a `timeoutMillis` that is null or positive). Phase 2 added no field to any Phase 1 record except the new `OmniBudsErrorCategory` members.

## 4. Public interfaces — `:core`, signatures as written

```kotlin
// core/src/main/kotlin/com/omnibuds/core/platform/BluetoothPlatform.kt:20-38
interface BluetoothPlatform {
    suspend fun readAdapterState(): OperationOutcome<AdapterStateObservation>
    fun observeAdapterState(): Flow<OperationOutcome<AdapterStateObservation>>
    suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities>
    suspend fun permissionState(permission: BluetoothPermission): OperationOutcome<PermissionState>
}

// AdapterStateSource.kt:19-45
interface AdapterStateSource {
    suspend fun readState(): OperationOutcome<BluetoothAdapterState>            // :21
    suspend fun openStateChanges(): OperationOutcome<AdapterStateChangeChannel>  // :31
    companion object { fun unavailable(): AdapterStateSource }                   // :33-36
}
interface AdapterStateChangeChannel {
    val registration: PlatformRegistration                                       // :41
    val states: Flow<BluetoothAdapterState>                                      // :44
}

// PlatformRegistration.kt:12-23
interface PlatformRegistration {
    val isActive: Boolean
    suspend fun dispose()                                                        // idempotent by contract
}

// TimeProvider.kt:11-18
fun interface TimeProvider { fun nowEpochMillis(): Long? }
object NoTimeProvider : TimeProvider                                             // always null

// PermissionRequirementResolver.kt:35-37, 57-62
interface PermissionRequirementResolver {
    fun planFor(operation: BluetoothOperation, context: PermissionContext): OperationOutcome<PermissionPlan>
}
class FrozenPermissionRequirementResolver : PermissionRequirementResolver

// AdapterStateObserver.kt:36-54, 63, 172
class AdapterStateObserver(
    private val source: AdapterStateSource,
    private val time: TimeProvider = NoTimeProvider,
) {
    val isObserving: Boolean                                                     // :43-44, reads slot.isLocked
    var teardownProblem: OmniBudsError?; private set                             // :53-54, last run, cleared at :83
    fun observe(): Flow<OperationOutcome<AdapterStateObservation>>               // :63, cold channelFlow
    suspend fun readOnce(): OperationOutcome<AdapterStateObservation>            // :172, takes no slot
}
```

Phase 1's `OperationOutcome` (`common/OperationOutcome.kt:16-44`, three cases plus `map`/`valueOrNull`/`errorOrNull`/`isSuccess`, and the `getOrNull()` extension at `:47`), `OmniBudsError` (`common/OmniBudsError.kt:15-38`) and `TransportContract` (`transport/TransportContract.kt:40-89`) are consumed unchanged. `BluetoothOperation.isAuthorizedIn` (`BluetoothOperation.kt:92`) is the only public function on that enum, and `PermissionRequirement.appliesAt` (`:25`) the only one on its record.

## 5. Public interfaces — transport boundaries and the Android module

```kotlin
// core/src/main/kotlin/com/omnibuds/core/transport/BluetoothTransport.kt:31-69
interface BluetoothTransport : TransportContract {
    override val kind: TransportKind                                              // :47
    suspend fun probeAvailability(): OperationOutcome<TransportAvailability>      // :68 — no caller, no body
}

// BleTransport.kt:36-41; identically in GattTransport.kt:34-39, ClassicTransport.kt:31-36,
// RfcommTransport.kt:35-40, LeAudioTransport.kt:45-50
interface BleTransport : BluetoothTransport {
    override val kind: com.omnibuds.core.common.TransportKind
        get() = com.omnibuds.core.common.TransportKind.BLE
}
```

```kotlin
// platform/android/.../bluetooth/adapter/BluetoothAdapterHandle.kt:19-34
interface BluetoothAdapterHandle {
    val adapterPresent: Boolean
    fun readRawState(): Int?
    fun openStateChanges(emit: (Int) -> Unit): PlatformRegistration
}

// :mapping/AdapterStateMapping.kt:21-29, 38
fun bluetoothAdapterStateOf(adapterPresent: Boolean, rawState: Int?): BluetoothAdapterState
const val RAW_STATE_UNREADABLE: Int = -1

// :adapter/AndroidAdapterStateSource.kt:35-38 · :adapter/SystemBluetoothAdapterHandle.kt:30-33
class AndroidAdapterStateSource(
    private val handle: BluetoothAdapterHandle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AdapterStateSource
class SystemBluetoothAdapterHandle(
    private val context: Context,
    private val apiLevel: ApiLevelProvider,
) : BluetoothAdapterHandle

// :permission/PermissionStandingReader.kt:14-17, 27-32, 35-36
fun interface PermissionStandingReader { fun isGranted(manifestName: String): Boolean? }
class SystemPermissionStandingReader(private val context: Context) : PermissionStandingReader
fun PermissionStandingReader.isGranted(permission: BluetoothPermission): Boolean? =
    isGranted(permission.manifestName)

// :permission/PermissionRequestLedger.kt:19-33
fun interface PermissionRequestLedger { fun wasRequested(permission: BluetoothPermission): Boolean }
class InMemoryPermissionRequestLedger : PermissionRequestLedger {
    fun record(permission: BluetoothPermission)                                   // :28-30 — never called in production
    override fun wasRequested(permission: BluetoothPermission): Boolean           // :32
}

// :permission/AndroidPermissionStateProvider.kt:35-39, 42, 65, 75-79
class AndroidPermissionStateProvider(
    private val reader: PermissionStandingReader,
    private val ledger: PermissionRequestLedger,
    private val resolver: PermissionRequirementResolver = FrozenPermissionRequirementResolver(),
) {
    fun standingOf(permission: BluetoothPermission): PermissionState
    fun standings(): Map<BluetoothPermission, PermissionState>
    fun stateFor(operation: BluetoothOperation, permission: BluetoothPermission,
                 context: PermissionContext): PermissionState
}

// :capability/ApiLevelProvider.kt:15-23 · :capability/PlatformFeatureProbe.kt:19-28
fun interface ApiLevelProvider { fun apiLevel(): Int }
object SystemApiLevelProvider : ApiLevelProvider                                   // Build.VERSION.SDK_INT
fun interface PlatformFeatureProbe { fun hasFeature(featureName: String): Boolean }
class SystemPlatformFeatureProbe(context: Context) : PlatformFeatureProbe           // hasSystemFeature

// :capability/AndroidPlatformCapabilityProvider.kt:30-37, 65-71, 81
fun interface TargetSdkProvider { fun targetSdk(): Int? }
class SystemTargetSdkProvider(private val context: Context) : TargetSdkProvider      // :36
class AndroidPlatformCapabilityProvider(
    private val apiLevel: ApiLevelProvider,
    private val featureProbe: PlatformFeatureProbe,
    private val permissionProvider: AndroidPermissionStateProvider,
    private val targetSdk: TargetSdkProvider,
    private val adapterPresent: () -> Boolean,
) { fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> }

// :bluetooth/AndroidBluetoothPlatform.kt:35-66 · :di/OmniBudsBluetooth.kt:44-52
class AndroidBluetoothPlatform(
    private val handle: BluetoothAdapterHandle,
    private val capabilityProvider: AndroidPlatformCapabilityProvider,
    private val permissionProvider: AndroidPermissionStateProvider,
    private val time: TimeProvider = SystemTimeProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BluetoothPlatform
fun omniBudsBluetoothPlatform(context: Context, /* six further defaulted seams */): BluetoothPlatform
```

Contract obligations an implementation owes, stated because they are not types: `standingOf` returns a state and never a `Failure`, so `AndroidBluetoothPlatform.permissionState` always wraps `Success` (`AndroidBluetoothPlatform.kt:66`) and that method's failure channel is unreachable through it; `dispose()` must be safe to call twice and must report failure rather than pretend (`PlatformRegistration.kt:16-22`); `openStateChanges()` implementations must unregister when the collector is lost (`AdapterStateSource.kt:23-30`); `BluetoothAdapterHandle` implementations must make disposal idempotent because the observer's teardown can run on a path that already disposed (`BluetoothAdapterHandle.kt:26-32`).

## 6. Permission matrix

Requirements come from `FrozenPermissionRequirementResolver.planFor` (`PermissionRequirementResolver.kt:59-224`), which branches on `context.targetSdk` and never on `deviceSdk` (ADR-P2-009). `install` = `isRuntimePermission == false`, so never a prompt. **Phase 2's own answer is that it needs nothing** — the first five rows in both bands.

### 6.1 Band `targetSdk ≥ 31`

| Operation | Required permissions | `appliesTo` | Phase 2 may perform? | Code |
|---|---|---|---|---|
| `ADAPTER_AVAILABILITY_INSPECTION` | **none** | — | yes | `:77-78` |
| `ADAPTER_STATE_INSPECTION` | **none** | — | yes | `:80-84` |
| `ADAPTER_STATE_OBSERVATION` | **none** | — | yes | `:80-84` |
| `PLATFORM_CAPABILITY_INSPECTION` | **none** | — | yes | `:96-99` |
| `PERMISSION_STATUS_INSPECTION` | **none** | — | yes | `:96-99` |
| `CONNECTED_DEVICE_INSPECTION` | `BLUETOOTH_CONNECT` (runtime) | 31..35 | no — Phase 3 | `:102-114` |
| `BONDED_DEVICE_LIST_INSPECTION` | `BLUETOOTH_CONNECT` (runtime) | 31..35 | no — Phase 3 | `:102-114` |
| `PROFILE_CONNECTION_STATE_INSPECTION` | `BLUETOOTH_CONNECT` (runtime) + caveat C-2 | 31..35 | no — Phase 3 | `:102-114` |
| `DEVICE_DISCOVERY_SCAN` | `BLUETOOTH_SCAN` (runtime); **plus** `ACCESS_FINE_LOCATION` (runtime) unless `assertsNeverForLocation` + caveat C-3 | 31..35 | no — Phase 3 | `:126-145` |
| `TRANSPORT_GATT_OPEN` | `BLUETOOTH_CONNECT` (runtime) | 31..35 | no — Phase 6 | `:175-186` |
| `TRANSPORT_RFCOMM_OPEN` | `BLUETOOTH_CONNECT` (runtime) | 31..35 | no — Phase 6 | `:175-186` |
| `LE_AUDIO_SESSION_INSPECTION` | `BLUETOOTH_CONNECT` (runtime) | 31..35 | no — Phase 10 | `:198-212` |

### 6.2 Band `targetSdk ≤ 30`

| Operation | Required permissions | `appliesTo` | Phase 2 may perform? | Code |
|---|---|---|---|---|
| `ADAPTER_AVAILABILITY_INSPECTION` | **none** — the row has no band branch | — | yes | `:77-78` |
| `ADAPTER_STATE_INSPECTION` | `BLUETOOTH` (install) | 26..30 | yes, as a plan | `:80-94` |
| `ADAPTER_STATE_OBSERVATION` | `BLUETOOTH` (install) | 26..30 | yes, as a plan | `:80-94` |
| `PLATFORM_CAPABILITY_INSPECTION` | **none** | — | yes | `:96-99` |
| `PERMISSION_STATUS_INSPECTION` | **none** | — | yes | `:96-99` |
| `CONNECTED_DEVICE_INSPECTION` | `BLUETOOTH` (install), reason "guide-level, not method-level" | 26..30 | no — Phase 3 | `:102-124` |
| `BONDED_DEVICE_LIST_INSPECTION` | `BLUETOOTH` (install), same reason | 26..30 | no — Phase 3 | `:102-124` |
| `PROFILE_CONNECTION_STATE_INSPECTION` | `BLUETOOTH` (install), same reason | 26..30 | no — Phase 3 | `:102-124` |
| `DEVICE_DISCOVERY_SCAN` | `BLUETOOTH` (install) + `BLUETOOTH_ADMIN` (install) + `ACCESS_COARSE_LOCATION` (runtime) + `ACCESS_FINE_LOCATION` (runtime) | 26..30 | no — Phase 3 | `:146-173` |
| `TRANSPORT_GATT_OPEN` | `BLUETOOTH` (install), guide-level reason | 26..30 | no — Phase 6 | `:175-196` |
| `TRANSPORT_RFCOMM_OPEN` | `BLUETOOTH` (install), guide-level reason | 26..30 | no — Phase 6 | `:175-196` |
| `LE_AUDIO_SESSION_INSPECTION` | **none** + caveat C-1 — an API-availability answer, not a permission answer | — | no — Phase 10 | `:198-202`, `:237-238` |

Reason strings are code, not prose: the `REASON_*` constants at `:270-287`. The "guide-level, not method-level" one (`:276-277`) carries research Note A verbatim, so a later reader cannot "correct" the asymmetry back into a documentation gap. Every row is asserted by `PermissionRequirementResolverTest.kt:33-175`, including the negative halves — no Phase 2 operation asks for location in **either** band (`:150-166`), and a legacy scan row must not spill to 31 (`:98-102`).

### 6.3 Caveats — `caveatFor` (`:232-249`): device-level facts that change behaviour but not the requirement list

| # | Trigger | Text (verbatim) |
|---|---|---|
| C-1 | `LE_AUDIO_SESSION_INSPECTION` and `targetSdk < 31` | "the LE Audio platform API does not exist below API 31; this is an availability limit, not a permission grant" (`:238`) |
| C-2 | `PROFILE_CONNECTION_STATE_INSPECTION`, modern target, `deviceSdk ≥ 31` | "profile-state enforcement is actually active on devices running API 31 or above; a 31+ target on an older device is documented but not enforced" (`:240-242`) |
| C-3 | `DEVICE_DISCOVERY_SCAN`, modern target, `assertsNeverForLocation == false` | "without neverForLocation, scan results are location-derived; asserting the flag filters some BLE beacons and must be decided with the scan design" (`:244-245`) |

`caveat` is never a permission and is never parsed to decide a capability. Asserted at `PermissionRequirementResolverTest.kt:124-147`.

### 6.4 Undeterminable input

`context.targetSdk == null` → `OperationOutcome.Failure(OmniBudsError(INVALID_STATE, "permission-resolver.plan", …))` (`:63-70`). `PermissionPlan.indeterminate` exists (`:19`) but the one resolver sets it `false` on every success (`:220`), so `needsNoPermission` cannot be tricked by an indeterminate plan; and `AndroidPermissionStateProvider.stateFor` maps a `Failure` or a `Cancelled` plan to `PermissionState.UNKNOWN`, never `NOT_REQUIRED` (`AndroidPermissionStateProvider.kt:91-95`; asserted at `AndroidPermissionStateProviderTest.kt:152-165` and `AndroidPlatformCapabilityProviderTest.kt:170-178`).

### 6.5 ⚠ Upper band limit

The modern rows carry `appliesTo = ApiRange(31, 35)` (`:111, 131, 142, 182, 207`) using `MAX_MATRIX_SDK = 35`, while the band decision itself is `targetSdk >= 31` with **no upper clamp** (`:72`). A hypothetical target of 36 therefore yields requirements whose `appliesAt(36)` is `false` even though the plan names them (`BluetoothPermission.kt:52`, `PermissionRequirement.kt:25`). `bluetooth-api-research.md` §0 warning 1 notes that live documentation already references API 36/37 members. The matrix's declared coverage is 26..35 (`BluetoothPermission.kt:55-62`), this is the first place that coverage becomes load-bearing, and no Phase 2 test asserts the boundary at 36.

## 7. Adapter-state machine

**States:** the six of `BluetoothAdapterState` (§2.1). **Producers of a value:** `readState()` — a snapshot the app asked for (`AndroidAdapterStateSource.kt:40-55`, reading `adapter?.state` at `SystemBluetoothAdapterHandle.kt:47`) — and the platform's `ACTION_STATE_CHANGED` announcement (`SystemBluetoothAdapterHandle.kt:81-89`). Nothing else in this tree can produce one, because no other adapter read exists.

**Transition legality is not modelled.** There is no `canTransition` for adapter state: the observer filters duplicates and forwards what it is given (`AdapterStateObserver.kt:120-138`). That is a deliberate difference from `state/ConnectionState.kt`, which does have `ConnectionStateTransitions`, and `design.md` §16 states the reason and the consequence — the platform's own sequence is not a contract Phase 2 verified, and inventing edges would be inventing behaviour.

**What is suppressed, and what is not:**

| Input | Result | Code |
|---|---|---|
| `ENABLED` announced again while `lastState == ENABLED` | **suppressed**, no element emitted | `AdapterStateObserver.kt:121` |
| `DISABLED`, `ENABLED`, `DISABLED` announced after an `ENABLED` read | four elements: `INITIAL_READ`, then three `PLATFORM_EVENT` | `:122-137`, `AdapterStateObserverTest.kt:65-90` |
| First differing event when the initial read **failed** | `INITIAL_EVENT` | `:122-126`, `AdapterStateObserverTest.kt:181` |
| A broadcast carrying no adapter-state extra | element with `UNKNOWN`, not `DISABLED` | `SystemBluetoothAdapterHandle.kt:87`, `AdapterStateMapping.kt:28, 38` |
| An unrecognised platform integer | `UNKNOWN` | `AdapterStateMapping.kt:28` |
| A platform call that throws | `Failure(PLATFORM_EXCEPTION)`, state untouched | `AndroidAdapterStateSource.kt:45-54, 83-92` |
| Initial `readState()` returns `Failure` | one `Failure`, then the event stream still runs with `lastState` unset | `AdapterStateObserver.kt:100-105`, `AdapterStateObserverTest.kt:160-182` |
| Initial `readState()` returns `Cancelled` | one `Cancelled`, stream ends, slot released, **nothing registered** | `AdapterStateObserver.kt:107-114`, `AdapterStateObserverTest.kt:211-224` |
| `openStateChanges()` returns `Cancelled` | `Success(INITIAL_READ)` then one `Cancelled`; slot released | `AdapterStateObserver.kt:143-146`, `AdapterStateObserverTest.kt:226-238` |
| A second collector starts while one runs | one `Failure(RESOURCE_UNAVAILABLE)`; `openStateChanges()` is **not** called a second time | `AdapterStateObserver.kt:64-75`, `AdapterStateObserverTest.kt:113-131` |

**Slot machine** — the other half of "state machine" in this phase: `unlocked → tryLock succeeds → collecting → finally { dispose; unlock }`, with the parallel `unlocked → tryLock fails → one Failure → closed` (`AdapterStateObserver.kt:63-163`). `isObserving` (`:43-44`) exposes it; the reset of `teardownProblem` at `:83` makes it last-run state rather than per-collection state.

**Terminal states:** none. `BluetoothAdapterState` has no terminal value; `DISABLED` is not the end of the machine, and a stream may end (cancellation, source completion, refused slot) while the adapter's state stays whatever it was.

## 8. Error contracts

The 23 members (`common/OmniBudsErrorCategory.kt:20-47`) with the two properties `OmniBudsErrorCategoryTest` pins exhaustively, and the column this phase owes — which code can actually produce the category.

| Category | retryClass | invalidatesSession | Produced in Phase 2 by |
|---|---|---|---|
| `BLUETOOTH_DISABLED` | RETRY_AFTER_REREAD | false | nothing — adapter state is a fact, not a refusal (§7) |
| `PERMISSION_DENIED` | NEVER_RETRY | false | nothing — no request path exists (§14 of `design.md`) |
| `DEVICE_DISCONNECTED` | RETRY_AFTER_REREAD | true | nothing |
| `TRANSPORT_UNAVAILABLE` | RETRY_AFTER_REREAD | false | nothing — legal as a `TransportAvailability.reason` (`TransportAvailability.kt:77-78`), constructed nowhere |
| `GATT_FAILURE` | RETRY_AFTER_REREAD | true | nothing |
| `RFCOMM_FAILURE` | RETRY_AFTER_REREAD | true | nothing |
| `PROTOCOL_MISMATCH` | NEVER_RETRY | true | nothing |
| `UNSUPPORTED_FEATURE` | NEVER_RETRY | false | nothing |
| `READ_FAILED` | SAFE_TO_RETRY | false | nothing in main source; scripted at `AdapterStateObserverTest.kt:162-167` |
| `WRITE_REJECTED` | NEVER_RETRY | true | nothing |
| `VERIFICATION_FAILED` | NEVER_RETRY | true | nothing |
| `TIMEOUT` | RETRY_AFTER_REREAD | true | nothing — no Phase 2 operation waits on anything (§10) |
| `FIRMWARE_MISMATCH` | NEVER_RETRY | false | nothing |
| `CODEC_UNAVAILABLE` | NEVER_RETRY | false | nothing |
| `UNKNOWN_DEVICE` | NEVER_RETRY | false | nothing |
| `INVALID_STATE` | NEVER_RETRY | false | `PermissionRequirementResolver.kt:66` (unknown target band); Phase 1's `DeviceState.kt:103` |
| `ADAPTER_UNAVAILABLE` | RETRY_AFTER_REREAD | false | `AdapterStateSource.kt:51, 60` — the unwired-seam case only |
| `UNSUPPORTED_OPERATION` | NEVER_RETRY | false | nothing |
| `PLATFORM_API_UNAVAILABLE` | NEVER_RETRY | false | nothing — an absent API is `ApiAvailability.UNAVAILABLE`, a state, not a refusal |
| `CONNECTION_UNAVAILABLE` | RETRY_AFTER_REREAD | true | nothing |
| `RESOURCE_UNAVAILABLE` | RETRY_AFTER_REREAD | false | `AdapterStateObserver.kt:68` (slot refused) and `:157` (teardown threw) |
| `PLATFORM_EXCEPTION` | RETRY_AFTER_REREAD | true | `AndroidAdapterStateSource.kt:48, 87`; `AndroidPlatformCapabilityProvider.kt:86`. **No `:core` file constructs it** |
| `UNKNOWN_FAILURE` | NEVER_RETRY | true | nothing in main source; the double's queue-ran-dry case (`FakeAdapterStateSource.kt:51`) |

Rules realised: a `Failure` is a value with `attempts >= 1` (`OmniBudsError.kt:22-24`) and `invalidatesSession` delegated to the category (`:27-28`); an error is never converted into a fabricated value — every "could not read" here leaves a state at `UNKNOWN` rather than a negative one; `detail` never carries an address, name or manufacturer data (`OmniBudsError.kt:11-13`), and the Phase 2 `detail` strings are fixed sentences or `${problem::class.simpleName}`. ⚠ One mismatch belongs in `validation.md` as loudly as here: `PLATFORM_EXCEPTION` carries `invalidatesSession = true`, which is right for the Phase 6 case where a platform exception leaves a device channel suspect, but the Phase 2 uses of it are **host-side reads that cannot invalidate any device session** — so `OmniBudsError.invalidatesSession` currently reports `true` for a failed `getState()`. The category is the enum's; the meaning in this phase is the mismatch.

## 9. Coroutine, cancellation and Flow contracts

| Contract | As built | File |
|---|---|---|
| Suspended entry points | 3 of the 4 `BluetoothPlatform` members (`observeAdapterState` returns a `Flow`), both `AdapterStateSource` members, `readOnce`, `dispose`, and the four `TransportContract`/`BluetoothTransport` mechanics | `BluetoothPlatform.kt:22,31,34,37`, `AdapterStateSource.kt:21,31`, `AdapterStateObserver.kt:172`, `PlatformRegistration.kt:22`, `TransportContract.kt:60,68,85`, `BluetoothTransport.kt:68` |
| Dispatcher | never a `:core` signature; confined to the platform module, defaulted to `Dispatchers.IO`, and every framework read is wrapped in `withContext` | `AndroidBluetoothPlatform.kt:40,41,51,66`, `AndroidAdapterStateSource.kt:37,41,71`, `OmniBudsBluetooth.kt:46` |
| Scope | none declared anywhere; the collector's scope owns the observation and `observe()` launches nothing | verified absence — no `launch`, `CoroutineScope`, `GlobalScope` or `Dispatchers.Main` in either module's main source |
| Cancellation of a **source** result | forwarded as `OperationOutcome.Cancelled`, never relabelled, and the stream ends; the slot still releases | `AdapterStateObserver.kt:107-114, 143-146, 162`; asserted at `AdapterStateObserverTest.kt:211-224, 226-238` |
| Cancellation of a **collector** | `finally` disposes the registration on cancellation, normal completion or a downstream throw | `AdapterStateObserver.kt:148-162`; `AdapterStateObserverTest.kt:146-158` |
| Flow shape | `observe()` is a cold `channelFlow` (`:63`); cold is what makes "collecting registers, losing the collector unregisters" expressible, and `AdapterStateSource.kt:23-30` requires it of implementations. ⚠ Phase 0 `specs.md` §5.5 rule 5 forbids a cold flow for device state and requires every flow to declare buffering and overflow. The buffer half **is** satisfied (conflated, latest-wins, reasoned at `AndroidAdapterStateSource.kt:57-68, 72`); the cold half of the rule is contradicted and **no ADR in 001-017 amends it** — the audit (§7) said an ADR was required | — |
| Buffering / overflow | `Channel.CONFLATED` + `trySend`, read out with `receiveAsFlow` | `AndroidAdapterStateSource.kt:72, 74, 114`. No `MutableStateFlow`, `SharedFlow`, replay, `buffer` or `flowOn` anywhere |
| Commands as values | none exist: nothing in this phase writes to anything | — |
| Shared mutable state | `AdapterStateObserver`'s `Mutex` and `teardownProblem` (`:40, 53`), the ledger's concurrent key set (`PermissionRequestLedger.kt:25`), the receiver registration's `AtomicBoolean` (`SystemBluetoothAdapterHandle.kt:101`). Every Phase 2 model is an immutable value | `design.md` §12 |
| Main-thread safety | declared at three sites and delivered by the default dispatcher; **no** lint or architecture check inspects a dispatcher | `AndroidBluetoothPlatform.kt:31-34`, `AndroidAdapterStateSource.kt:26-28`, `SystemBluetoothAdapterHandle.kt:24-28` |

## 10. Timeouts, retries and validation rules

**Phase 2 has no timeout mechanism, and stating that plainly is the specification.** No `withTimeout`, `timeout(`, `delay`, `Timer` or `Handler` appears in either module's main source; nothing in Phase 2 awaits a device, so nothing declares or enforces a bound. The Phase 1 timeout *fields* survive, unread by Phase 2: `TransportContract.exchange(request, timeoutMillis: Long)` (`TransportContract.kt:85-88`) has no implementation, `TransportRequest.timeoutMillis: Long?` (`TransportRequest.kt:39`), `CommandDefinition.timeoutMillis` (`CommandDefinition.kt:49`) and `ProtocolConfiguration.timeoutMillis` (`ProtocolConfiguration.kt:37`) are protocol-side, and `OmniBudsErrorCategory.TIMEOUT` is produced nowhere (§8). The only wait-like behaviour in the phase is `channelFlow`'s suspension on `source.openStateChanges()` and on `collect` — unbounded by construction, ended by the collector's cancellation. A binder call that hangs would hang the `Dispatchers.IO` thread it is on; that is the honest state of the phase, and the bound belongs to the phase that first exchanges bytes.

| Operation class | Timeout | Retry policy | Budget | Read-back | Validation rule |
|---|---|---|---|---|---|
| Host read (`readState`, `capabilities`, `standingOf`) | none declared | none implemented — a failure is reported once (`AndroidAdapterStateSource.kt:26-28`) | 1 (`OmniBudsError.attempts` default, never incremented) | n/a | a `Failure` leaves state `UNKNOWN`, never `DISABLED` |
| Observation stream | none | n/a | n/a | n/a | duplicates dropped; kind rule per §7 |
| Teardown (`dispose`) | none | none, deliberately: a half-torn-down registration is recorded in `teardownProblem`, not re-attempted in a loop | 1 attempt, idempotent-by-contract handle | n/a | `AtomicBoolean` CAS makes a second call a no-op (`SystemBluetoothAdapterHandle.kt:101-109`) |
| Device-facing exchange | **no such operation exists in Phase 2** | Phase 0 §4's read/write asymmetry is inherited unchanged: `READ_FAILED` alone is `SAFE_TO_RETRY` (`OmniBudsErrorCategoryTest.kt:76-82`); a timed-out write is `RETRY_AFTER_REREAD`, never resent | — | — | — |

Validation rules that run in Phase 2: `require(attempts >= 1)` (`OmniBudsError.kt:23`), `require(reason.isNotBlank())` (`PermissionRequirement.kt:19-21`), `require(minSdkInclusive in 26..35)` and the non-inverted `ApiRange` (`BluetoothPermission.kt:44-49`), `apiLevel > 0` when present (`BluetoothPlatformCapabilities.kt:34-37`), the three `TransportBoundary` invariants (`:70-90`) and the two `TransportNegotiation` ones (`:47-62`), and the getValue-style lookups that make an unregistered feature throw rather than default (`AndroidPlatformCapabilityProvider.kt:117-118`). Phase 1's capability-rung guards (`core/src/main/kotlin/com/omnibuds/core/capability/FeatureCapability.kt:66-98`) are untouched by Phase 2 and are not re-run here.

## 11. Resource-cleanup rules

1. **Every registration is disposed in a `finally`, once per collection**, on cancellation, normal completion or a downstream throw (`AdapterStateObserver.kt:148-162`).
2. **A teardown that throws is recorded, not emitted**: `teardownProblem` (`:53-54, 153-161`) — the channel may already be closed and a send there would raise a second unrelated failure above the real one (ADR-P2-007).
3. **Disposing the channel's registration also closes the channel** (`AndroidAdapterStateSource.kt:124-134`), so a torn-down stream ends instead of leaving a collector suspended on a receiver that has gone away (`:101-107`).
4. **`unregisterReceiver` is called at most once**, guarded by a compare-and-set, because unregistering an unknown receiver throws and the cancellation path may dispose twice (`SystemBluetoothAdapterHandle.kt:100-111`).
5. **A registration that was never collected is still disposable** — `openStateChanges()` registers eagerly and hands the handle back even if collection never starts (`AndroidAdapterStateSource.kt:57-64`; `AndroidAdapterStateSourceTest.kt:90`), and disposal counts are asserted (`:90`, `:104`, `:116` for "opened but never collected", "disposed twice reaches the platform once", "disposal ends the stream").
6. **A failed registration leaves no emission path behind** (`AndroidAdapterStateSource.kt:83-92` closes the channel before returning the failure; `AndroidAdapterStateSourceTest.kt:129-138`).
7. **The slot is released after teardown, and only if held** (`AdapterStateObserver.kt:162`); `isObserving` (`:43-44`) is the observable side of that.
8. **No lifecycle object to unregister**: no manifest component, no service, no `LifecycleOwner` (ADR-P2-011 and rule 11), so nothing can outlive the collector except a registration, and rules 1-4 cover it.
9. **No persisted state to clean up**: the ledger is in-memory and process-scoped by decision (`PermissionRequestLedger.kt:15-17`) and nothing in Phase 2 writes to storage.

## 12. DI seams and their default production implementations

Manual constructor injection, no container (ADR-P1-009). A default is the production implementation; a test replaces it by passing something else.

| Seam | Default production implementation | Replacement seen in tests | Cited |
|---|---|---|---|
| `BluetoothAdapterHandle` | `SystemBluetoothAdapterHandle(appContext, apiLevel)` | `ScriptedHandle`, `FakeHandle` | `OmniBudsBluetooth.kt:54`, `AndroidBluetoothPlatformTest.kt:151`, `AndroidAdapterStateSourceTest.kt:148` |
| `AdapterStateSource` | `AndroidAdapterStateSource(handle, dispatcher)`, built inside the platform class | `FakeAdapterStateSource.asSource()` | `AndroidBluetoothPlatform.kt:43`, `AdapterStateObserverTest.kt:42` |
| `AdapterStateObserver` | one per `AndroidBluetoothPlatform` | not injectable — the composition is the point | `AndroidBluetoothPlatform.kt:44` |
| `PermissionRequirementResolver` | `FrozenPermissionRequirementResolver()` | any alternate table in a test | `AndroidPermissionStateProvider.kt:38` |
| `PermissionStandingReader` | `SystemPermissionStandingReader(appContext)` | `{ manifestName -> manifestName in granted }` | `OmniBudsBluetooth.kt:48`, `AndroidBluetoothPlatformTest.kt:132` |
| `PermissionRequestLedger` | `InMemoryPermissionRequestLedger()` | `{ false }`, or the real class with `record()` | `OmniBudsBluetooth.kt:49`, `AndroidBluetoothPlatformTest.kt:133`, `AndroidPermissionStateProviderTest.kt:41-44` |
| `ApiLevelProvider` | `SystemApiLevelProvider` (`Build.VERSION.SDK_INT`) | `ApiLevelProvider { apiLevel }` | `OmniBudsBluetooth.kt:47`, `AndroidPlatformCapabilityProviderTest.kt:229` |
| `TargetSdkProvider` | `SystemTargetSdkProvider(appContext)` (`applicationInfo.targetSdkVersion`) | `TargetSdkProvider { targetSdk }` | `OmniBudsBluetooth.kt:51`, `AndroidPlatformCapabilityProviderTest.kt:235` |
| `PlatformFeatureProbe` | `SystemPlatformFeatureProbe(appContext)` (`hasSystemFeature`) | `PlatformFeatureProbe { name -> … }`, one that throws | `OmniBudsBluetooth.kt:50`, `AndroidPlatformCapabilityProviderTest.kt:220-223` |
| `TimeProvider` | `SystemTimeProvider` | `TimeProvider { null }`, `TimeProvider { 1_700_000_000_000L }`, `NoTimeProvider` default | `AndroidBluetoothPlatform.kt:39`, `AdapterStateObserverTest.kt:269`, `AdapterStateObserver.kt:38` |
| `adapterPresent: () -> Boolean` | `{ handle.adapterPresent }` | a lambda that throws, to prove the report fails rather than guesses | `OmniBudsBluetooth.kt:66`, `AndroidPlatformCapabilityProviderTest.kt:224-227` |
| `CoroutineDispatcher` | `Dispatchers.IO` | `Dispatchers.Unconfined` under `runTest` | `AndroidBluetoothPlatform.kt:40`, `AndroidAdapterStateSource.kt:37`, `AndroidBluetoothPlatformTest.kt:146` |
| Transport factories (prompt 5.9's fifth role) | **not supplied** — a factory that can produce a transport is a channel that can be opened, which prompt section 6 forbids; deferred to Phase 6 with the audit's reason (`architecture-audit.md:194`, `transport-boundaries.md` §4 item 2) | — | — |

`omniBudsBluetoothPlatform` takes a `Context` and keeps `context.applicationContext` (`OmniBudsBluetooth.kt:48-53`) because a Bluetooth platform outlives the screen that asked for it, and it deliberately does not cache — the reason is stated at `:34-38`.

## 13. Compatibility requirements

| Item | Value | Source |
|---|---|---|
| `minSdk` | 26 | `gradle/libs.versions.toml:13`, applied at `platform/android/build.gradle.kts:11,24`; **confirmed by ADR-P2-002**, which closes ADR-P1-015 precisely because the permission model splits at 31 and both bands must be modelled |
| `compileSdk` | 35 | `libs.versions.toml:12`, `platform/android/build.gradle.kts:10,21` |
| `targetSdk` | 35 in the catalog (`:14`) and **used by only one module**: `tools/companion-shell/build.gradle.kts:21,30`. `:platform:android` is a library and declares none, so the band must be read at runtime (`AndroidPlatformCapabilityProvider.kt:36`) — `design.md` §16 | research §1, ADR-P2-009 |
| JVM target / Java compatibility | 17 | `core/build.gradle.kts:12,20-23`, `platform/android/build.gradle.kts:27-30,35` |
| Kotlin / AGP / Gradle | 2.0.21 / 8.7.3 / Gradle 8.9 | `libs.versions.toml:9,11`, `gradle/wrapper/gradle-wrapper.properties:3`, ADR-P1-014 |
| Coroutines | `kotlinx-coroutines-core` 1.9.0 as `api` on `:core` (`core/build.gradle.kts:29`, `libs.versions.toml:10,20`) — restored on ADR-P1-021's own trigger (ADR-P2-003). `kotlinx-coroutines-android` is **not** used: no main-thread surface exists, so `Dispatchers.Main` has no reason (audit §5.1) | — |
| AndroidX | none, anywhere. `Context.registerReceiver` and `Context.RECEIVER_NOT_EXPORTED` are framework API, so `androidx.core` was not added; ADR-P2-016 rejects the `ContextCompat` alternative | `libs.versions.toml:17-25`, `SystemBluetoothAdapterHandle.kt:72-79` |
| `checkSelfPermission` | exists since API 23; the floor is 26, so no version branch (`PermissionStandingReader.kt:22-25`) | research §5 Q4 |
| Receiver export flag | constants from 33, requirement keyed to targetSdk ≥ 34, `ACTION_STATE_CHANGED` is a protected system broadcast so the flag is optional; the code branches on the **device** level at 33 and passes `RECEIVER_NOT_EXPORTED` | research §5 Q6 item R-1, §5 Q7; `SystemBluetoothAdapterHandle.kt:56-79, 114` |
| API-level floors used as availability gates | 5 / 18 / 18 / 31 | `AndroidPlatformCapabilityProvider.kt:202-209`, read from the SDK's `platforms/android-35/data/api-versions.xml`; ADR-P2-017 |
| Feature names | `PackageManager.FEATURE_BLUETOOTH`, `PackageManager.FEATURE_BLUETOOTH_LE` — framework constants, never retyped as strings | `AndroidPlatformCapabilityProvider.kt:99-100`, `PlatformFeatureProbe.kt:11-15` |
| Permission-model bands | target ≤ 30 legacy, target ≥ 31 modern; matrix coverage 26..35 | §6, `BluetoothPermission.kt:61-68` |
| Verification ceiling | nothing above `IMPLEMENTED`; `hardwareEvidence` pinned to `INFERRED` for every platform feature | `AndroidPlatformCapabilityProvider.kt:115`, ADR-P2-008, ADR-P2-017, `docs/security/device-access-policy.md:61-63` |
| Device-validation status | no device attached during implementation, no AVD, no instrumented test, no `androidx.test` dependency; the Android glue is compiled and lint-checked only | `docs/security/device-access-policy.md:44-63` |

## 14. Test-double seams

| Double | Seam it stands in for | How it refuses to invent | Cited |
|---|---|---|---|
| `FakeAdapterStateSource` (core test) | `AdapterStateSource`, `PlatformRegistration`, `AdapterStateChangeChannel` | reads come from a fixed `vararg` queue and answer `Failure(UNKNOWN_FAILURE)` when it runs dry rather than inventing a state (`:48-56`); `openCancels` scripts a cancelled registration (`:28, 71`); `failOpen` refuses and `failDisposal` throws (`:42-43, 61-69, 80-82`); counts `readCalls`, `openCalls`, `disposeCalls`, `activeRegistrations` so a leak is observable (`:30-40`); `keepOpen` uses `awaitCancellation()` to model a stream that never ends, so slot and cancellation behaviour need no sleep (`:93-96`) | `core/src/test/.../platform/FakeAdapterStateSource.kt:24-108` |
| `ScriptedHandle` / `FakeHandle` (Android tests) | `BluetoothAdapterHandle` plus an anonymous `PlatformRegistration` | keep the emission callback so a test can push a raw integer and assert disposal counts; `readFailure`/`registerFailure` throw on demand | `AndroidBluetoothPlatformTest.kt:151-171`, `AndroidAdapterStateSourceTest.kt:148-182` |
| `KindReader` and the five `…Pin` objects | `BluetoothTransport`, for kind pinning only | every member throws, because a reader that answered would be a transport implementation in a phase that forbids one; legal because it is test source | `TransportKindPinningTest.kt:57-87` |
| `fun interface` lambdas | `TimeProvider`, `ApiLevelProvider`, `TargetSdkProvider`, `PlatformFeatureProbe`, `PermissionStandingReader`, `PermissionRequestLedger` | no double *type* is needed for a one-method seam, which is why those are `fun interface`s | §12, column three |
| `AdapterStateSource.unavailable()` (**main source**) | the "nothing was wired" case | answers `Failure(ADAPTER_UNAVAILABLE)` with the detail "no adapter state source was available" — it does **not** report the `UNAVAILABLE` *state*, which would be a fabricated hardware negative (audit R-6). A production default that only a test uses | `AdapterStateSource.kt:33-36, 47-65`; `AdapterStateObserverTest.kt:256-264` |
| Phase 1 doubles still in force | `ScriptedOutcome`, `FakeDeviceRepository`, `FakeCapabilityRepository`, `FakeProtocolRecordAccess` | unchanged; `TestDoublesAreNotHardwareTest` still guards the persistence seams | `core/src/test/.../testing/` |

Guard on the other side: `productionSourcesDefineNoTestDoubles` rejects any main-source class named `Fake…`/`Mock…`/`Stub…`/`Dummy…`/`Test…` (`DependencyDirectionTest.kt:179-191`), and `noProductionClassImplementsTheProtocolOrRepositoryContracts` (`:194-208`) still covers `TransportContract` — its regex anchors on `(class|object)\s+\w+[^{]*:`, so an `interface X : TransportContract` is deliberately invisible to it, which is what lets Phase 2's boundaries exist without tripping it. `AdapterStateObserverTest`'s 16 cases are the machine's proof surface; `TransportBoundariesTest` carries the boundary invariants, and its fixtures are fictional (`TransportBoundariesTest.kt:25-29`).

## 15. Which file owns which spec entry

| Entry | Owner file |
|---|---|
| §2.1 adapter states and predicates | `core/src/main/kotlin/com/omnibuds/core/platform/BluetoothAdapterState.kt` |
| §2.2 observation kinds; §3 `AdapterStateObservation` | `core/.../platform/AdapterStateObservation.kt` |
| §4 `AdapterStateSource`, `AdapterStateChangeChannel`; §14 `unavailable()` | `core/.../platform/AdapterStateSource.kt` |
| §6 slot machine, §9 `observe()`/`readOnce()`, §11 rules 1-2 and 7, §8 `RESOURCE_UNAVAILABLE` | `core/.../platform/AdapterStateObserver.kt` |
| §4 `PlatformRegistration` | `core/.../platform/PlatformRegistration.kt` |
| §4 `TimeProvider` / `NoTimeProvider` | `core/.../platform/TimeProvider.kt` |
| §2.3 `PermissionState` and its three predicates | `core/.../platform/PermissionState.kt` |
| §2.6 `BluetoothPermission`; §2.10 `ApiRange` constants | `core/.../platform/BluetoothPermission.kt` |
| §3 `PermissionContext`; §2.10 model constants | `core/.../platform/PermissionContext.kt` |
| §3 `PermissionRequirement` | `core/.../platform/PermissionRequirement.kt` |
| §4-§6 resolver, matrix, caveats, reason strings, §6.4, §6.5 | `core/.../platform/PermissionRequirementResolver.kt` |
| §2.5 `PlatformFeature` | `core/.../platform/PlatformFeature.kt` |
| §2.4 `ApiAvailability`, `apiLevelSupports` | `core/.../platform/ApiAvailability.kt` |
| §3 `PlatformFeatureSupport` (three axes, `isUsable`, `blockingReason`) | `core/.../platform/PlatformFeatureSupport.kt` |
| §3 `BluetoothPlatformCapabilities` | `core/.../platform/BluetoothPlatformCapabilities.kt` |
| §4 `BluetoothPlatform` | `core/.../platform/BluetoothPlatform.kt` |
| §2.7 `BluetoothOperation` and the phase-authorisation surface | `core/.../platform/BluetoothOperation.kt` + `core/src/test/.../platform/PhaseTwoScopeTest.kt` |
| §2.8 `TransportKind.BLE` | `core/src/main/kotlin/com/omnibuds/core/common/TransportKind.kt` |
| §8 23 categories, `retryClass`, `invalidatesSession` | `core/.../common/OmniBudsErrorCategory.kt` + `core/src/test/.../common/OmniBudsErrorCategoryTest.kt` |
| §5 boundary interfaces and pinned kinds | `core/.../transport/{BluetoothTransport,BleTransport,GattTransport,ClassicTransport,RfcommTransport,LeAudioTransport}.kt` + `core/src/test/.../transport/TransportKindPinningTest.kt` |
| §3 `TransportBoundary`, `TransportNegotiation` | `core/.../transport/{TransportBoundary,TransportNegotiation}.kt` + `TransportBoundariesTest.kt` |
| §7 platform producers, §11 rules 3-6, §16 | `platform/android/src/main/kotlin/com/omnibuds/android/bluetooth/adapter/{BluetoothAdapterHandle,AndroidAdapterStateSource,SystemBluetoothAdapterHandle}.kt`, `bluetooth/mapping/AdapterStateMapping.kt` |
| §12 permission seams | `platform/android/.../bluetooth/permission/{PermissionStandingReader,PermissionRequestLedger,AndroidPermissionStateProvider}.kt` |
| §12 capability seams, §2.10 API floors, §13 gates, §16 | `platform/android/.../bluetooth/capability/{ApiLevelProvider,PlatformFeatureProbe,AndroidPlatformCapabilityProvider}.kt` |
| §4 `BluetoothPlatform` implementation, §9 dispatcher confinement, §13 clock | `platform/android/.../bluetooth/{AndroidBluetoothPlatform,SystemTimeProvider}.kt` |
| §12 composition root, one-instance rule | `platform/android/.../di/OmniBudsBluetooth.kt` |
| §13 build facts, §14 zero-permission manifest | `core/build.gradle.kts`, `platform/android/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `settings.gradle.kts`, `platform/android/src/main/AndroidManifest.xml` |
| §0 enforcement columns | `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt` |

## 16. Three places where Phase 2's prose overstates its code

Recorded here rather than fixed silently, because `design.md` §0 and Phase 1 `specs.md` §11 rule 6 both make an undocumented deviation a defect:

1. `PermissionState.kt:42-43` says `NOT_REQUIRED` **and a genuine grant** allow silent proceeding; `:45` returns true only for `NOT_REQUIRED`. The code is authoritative, and a grant is read through the separate `isGranted()` (`:37`).
2. `AndroidAdapterStateSource.kt:30-33` says the platform exception "is preserved in the error's cause for diagnostics only". `OmniBudsError` has no `cause` field (`OmniBudsError.kt:15-21`): the exception object is dropped and only `${problem::class.simpleName}` survives in `detail` (`:50`, `:89`).
3. `ApiAvailability.kt:25-31` documents the phone-hardware axis above `apiLevelSupports` (`:32-36`), which answers the OS axis. The hardware axis is `PlatformFeatureSupport.hardwareEvidence` (`:26`), and ADR-P2-017 is the decision that says so.

And two documents, not code, are behind the code: `BleTransport.kt:20-23` still asserts that `TransportKind` "deliberately has no `BLE` constant" while `:36-41` pins one (ADR-P2-013 postdates them), and `docs/phases/phase-2/transport-boundaries.md:64-71` records the same pre-ADR position. `design.md` §9 cites both.

## 17. Amendments Phase 2 makes to Phase 0 and Phase 1 `specs.md`

| Prior clause | Status after Phase 2 | Authority |
|---|---|---|
| Phase 0 §1.3 / Phase 1 §1.3 canonical enum vocabulary | ⚠ `TransportKind` gains `BLE` — seven members | ADR-P2-013 |
| Phase 1 §2.3 / §3 sixteen error categories | ⚠ **23**, by union: `ADAPTER_UNAVAILABLE`, `UNSUPPORTED_OPERATION`, `PLATFORM_API_UNAVAILABLE`, `CONNECTION_UNAVAILABLE`, `RESOURCE_UNAVAILABLE`, `PLATFORM_EXCEPTION`, `UNKNOWN_FAILURE`; `ADAPTER_STATE_UNKNOWN` was drafted and refused, cancellation stayed an outcome | ADR-P2-004 (amends ADR-P1-006) |
| Phase 1 §3 rule 5 and §8, where `invalidatesSession` was pinned by two lists | ⚠ **closed**: both properties now run through exhaustive maps with `assertFullCoverage` | ADR-P2-005 |
| Phase 1 §5.1 "`:core` has zero production dependencies"; Phase 0 §5.5 rule 5 "no Flow yet" | ⚠ `kotlinx-coroutines-core` returns as `api` on `:core`, and the first `Flow` surface exists. ⚠ its **cold** shape conflicts with Phase 0 §5.5's device-state rule and no ADR amends it — `design.md` §12 | ADR-P2-003 (discharges ADR-P1-021's trigger) |
| Phase 0 §2.3 and `security-governance.md` SEC-PERM-002, which framed requirements as device-version-driven | ⚠ **corrected**: requirements key off `targetSdkVersion`; `deviceSdk` produces caveats only | ADR-P2-009 |
| Phase 0 §5 rule 3 "a cancelled operation reports CANCELLED, not success" | **inherited and now honoured at the platform seam**: `AdapterStateObserver.kt:107-114, 143-146` forward `Cancelled` unchanged and still release the slot; asserted at `AdapterStateObserverTest.kt:211-224, 226-238` | ADR-P1-004, closing audit R-7 |
| Phase 1 §2.4 time rule | **inherited and applied**: `observedAtEpochMillis: Long?`, clock behind a seam, `null` never filled with `0` | ADR-P1-012 |
| Phase 1 §7 layer map (`common` 0; `state`, `transport` 1; …) | ⚠ `platform` registered at **1**; ⚠ `state` moved to **0**. The registration is recorded; the move is **not** — `DependencyDirectionTest.kt:34-37` is the only statement of it | ADR-P2-001 (partial) |
| Phase 1 §7 `platformAndroidModuleStillContainsNoSources`; Phase 1 §10 "a member on a released interface needs an ADR" | ⚠ replaced by `platformModuleContainsNoUnauthorisedCapabilities`, rule 2 re-targeted from Bluetooth-shaped identifiers to named framework types, rules 8-11 added, rule 9's `Intent` token moved into rule 10. `TransportContract` gained no member — `probeAvailability` is on the new sub-interface, which Phase 1's rule does not constrain | ADR-P2-006, ADR-P2-015, ADR-P2-016 |
| Phase 1 §6 data-model conventions, §10 visibility | unchanged and followed: immutable value records, no `internal`, no `@VisibleForTesting`, no ambient singleton | — |
| Phase 1 §9 serialization rules | **untouched and unreached**: Phase 2 persists nothing; non-persistence of the ledger is a §11 rule instead (`PermissionRequestLedger.kt:15-17`) | — |
| Phase 1 §11 documentation requirements | ⚠ one obligation strained in practice — §16 lists the three KDoc/code mismatches found while writing this file | — |

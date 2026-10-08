# Phase 9 — Specifications

**Phase:** 9 — Hardware Feature Engine · **Scope id:** P9
**Document status:** accepted

## 1. Module and package layout

```text
core/src/main/kotlin/com/omnibuds/core/feature/
    FeatureValueType.kt          # value-shape vocabulary + accepts()
    FeatureConstraints.kt        # bounds; violationOf() returns a reason or null
    FeatureAccess.kt             # access enum + accessOf() + permits()
    FeatureState.kt              # sealed control states + FeatureStateTransitions table
    FeatureErrorCode.kt          # prompt §24 names -> OmniBudsErrorCategory + toError()
    FeatureDefinition.kt         # control contract: id, category, shape, constraints, relations
    FeatureRelation.kt           # six relation kinds + isActiveValue() convention
    FeatureDependencyEvaluator.kt# relation verdicts; Requires via DependencyValidator
    FeatureProtocolPort.kt       # handed-in seam (read/write + supports flags)
    FeatureOperation.kt          # operation value: id, feature, type, value, timeout, side-effect class
    FeatureValidator.kt          # 10-step gate; pure, no I/O
    FeatureStateRepository.kt    # single-owner reactive state holder + in-memory impl
    FeatureEngine.kt             # lifecycle: validate -> pending -> port -> read-back -> reconcile
    StandardFeatures.kt          # 19 definitions + EqualizerValues + GestureValues
    VendorFeatureContract.kt     # vendor.<vendor>.<feature> factory + namespace rules

core/src/main/kotlin/com/omnibuds/core/config/
    ConfigurationValue.kt        # + FloatValue, RangeValue, StructuredValue (+StructuredField),
                                 #   BitmaskValue, CustomValue (ADR-P9-001)

core/src/test/kotlin/com/omnibuds/core/feature/
    FeatureTestFixtures.kt       # record()/snapshot() builders + ScriptedFeaturePort
    FeatureValueTest.kt
    FeatureStateTest.kt
    FeatureAccessTest.kt
    FeatureValidatorTest.kt
    FeatureDependencyEvaluatorTest.kt
    FeatureEngineTest.kt
    FeatureConcurrencyTest.kt
    StandardFeaturesTest.kt
    VendorFeatureContractTest.kt
    PhaseNineScopeTest.kt
```

The `feature` area registers as layer 5 in `DependencyDirectionTest`'s layer
map (ADR-P9-002's placement rationale).

## 2. Key contracts

### 2.1 FeatureOperation

```kotlin
data class FeatureOperation(
    val operationId: String,        // non-blank; caller-supplied, never minted from wall-clock
    val feature: FeatureId,
    val type: FeatureOperationType, // READ | WRITE | SUBSCRIBE | UNSUBSCRIBE | RESET
    val requestedValue: ConfigurationValue?, // present iff WRITE
    val timeoutMillis: Long?,       // null inherits DEFAULT_TIMEOUT_MILLIS (10_000)
    val sideEffect: SideEffectClass, // READ -> READ_ONLY_SAFE, else SIDE_EFFECTING
)
```

### 2.2 FeatureStateRepository

```kotlin
interface FeatureStateRepository {
    val states: StateFlow<Map<FeatureId, FeatureState>>
    fun stateOf(feature: FeatureId): FeatureState?
    suspend fun update(feature: FeatureId, transition: (FeatureState?) -> FeatureState)
    suspend fun reset()
}
```

`update` runs the transition under a mutex and refuses illegal moves per
`FeatureStateTransitions.isLegal`.

### 2.3 FeatureEngine

```kotlin
class FeatureEngine(
    definitions: Map<FeatureId, FeatureDefinition>,
    port: FeatureProtocolPort,
    repository: FeatureStateRepository = InMemoryFeatureStateRepository(),
    validator: FeatureValidator = FeatureValidator(),
) {
    val states: StateFlow<Map<FeatureId, FeatureState>>
    suspend fun adoptSnapshot(snapshot: CapabilitySnapshot)
    suspend fun read(feature, snapshot, operationId, timeoutMillis = null): OperationOutcome<FeatureState>
    suspend fun write(feature, value, snapshot, operationId, timeoutMillis = null): OperationOutcome<FeatureState>
    suspend fun onDeviceReported(feature, value: ConfigurationValue)
    suspend fun onSessionInvalidated()
    fun observe(feature: FeatureId): Flow<FeatureState?>
    fun lastConfirmedOf(feature: FeatureId): ConfigurationValue?
}
```

### 2.4 FeatureProtocolPort

```kotlin
interface FeatureProtocolPort {
    val supportsRead: Boolean
    val supportsWrite: Boolean
    suspend fun read(feature: FeatureId): OperationOutcome<ConfigurationValue>
    suspend fun write(feature: FeatureId, value: ConfigurationValue): OperationOutcome<Unit>
}
```

No production implementation exists in Phase 9.

## 3. Value shapes

| Shape | Kotlin type | Construction rules |
|---|---|---|
| BOOLEAN | `BooleanValue` | — |
| ENUM | `ModeValue` | non-blank technical + display names |
| INTEGER | `IntValue` | — |
| FLOAT | `FloatValue` | finite; NaN/±∞ refused |
| RANGE | `RangeValue` | finite bounds; min ≤ max |
| STRING | `StringValue` | — |
| STRUCTURED | `StructuredValue` | ≥1 field; unique non-blank names; ≤64 fields |
| BITMASK | `BitmaskValue` | ≥1 flag; non-blank; ≤64 flags |
| CUSTOM | `CustomValue` | non-blank; ≤4096 chars |

## 4. Error mapping (ADR-P9-010)

| FeatureErrorCode | OmniBudsErrorCategory |
|---|---|
| FEATURE_UNKNOWN | INVALID_STATE |
| FEATURE_UNSUPPORTED | UNSUPPORTED_FEATURE |
| FEATURE_UNAVAILABLE | INVALID_STATE |
| FEATURE_READ_ONLY / FEATURE_WRITE_UNSUPPORTED / PROTOCOL_UNAVAILABLE / OPERATION_NOT_IMPLEMENTED | UNSUPPORTED_OPERATION |
| INVALID_VALUE / DEPENDENCY_NOT_SATISFIED / FEATURE_CONFLICT / MALFORMED_RESPONSE | INVALID_STATE |
| TRANSPORT_UNAVAILABLE | TRANSPORT_UNAVAILABLE |
| OPERATION_TIMEOUT | TIMEOUT |
| DEVICE_REJECTED | WRITE_REJECTED |
| DEVICE_STATE_UNKNOWN | READ_FAILED |
| STATE_VERIFICATION_FAILED | VERIFICATION_FAILED |
| UNKNOWN_ERROR | UNKNOWN_FAILURE |

## 5. Invariants

1. A requested value is never the device state (`Pending.requested` ≠ truth).
2. `Confirmed` is written only from read-back equality or a validated device report.
3. A timed-out write is never re-sent; at most one port write per accepted write.
4. Unknown is never written as unsupported; a failed read-back yields `Unknown`, never a guess.
5. Values are refused, never silently clamped.
6. No operation is attempted without passing all 10 validation steps.
7. The engine never caches a capability snapshot; every call takes the current one.
8. Cancellation restores; invalidation unknowns; device reports win.
9. `describeValue` logs shapes and scalar contents only — never raw payload bytes (SEC-LOG-004); error details carry no device identifiers (SEC-LOG-002).

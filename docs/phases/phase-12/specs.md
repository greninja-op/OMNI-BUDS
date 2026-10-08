# Phase 12 — Codec Support & Configuration: Specifications

**Status:** Authoritative for Phase 12.

---

## 1. CodecControlCapability

```kotlin
data class CodecControlCapability(
    val codec: Codec,
    val observable: Boolean,
    val supported: Boolean,
    val selectable: Boolean,
    val configurable: Boolean,
    val verifiable: Boolean,
    val evidence: CodecEvidence,
)
```

- `controllable = selectable || configurable`
- `unknown(codec, evidence)` — all false.

## 2. CodecConfiguration

```kotlin
data class CodecConfiguration(
    val codec: Codec,
    val qualityMode: QualityMode = QualityMode.UNKNOWN,
    val bitrate: CodecBitrate = CodecBitrate.Unknown,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channelMode: ChannelMode = ChannelMode.UNKNOWN,
    val adaptiveMode: Boolean? = null,
)
```

- `init` rejects non-positive `sampleRateHz` / `bitDepth`.
- `empty(codec)` factory.

## 3. CodecOperation

```kotlin
data class CodecOperation(
    val operationId: String,          // caller-supplied, non-blank
    val device: DeviceIdentity,
    val codec: Codec,                 // never UNKNOWN
    val type: CodecOperationType,
    val requestedConfiguration: CodecConfiguration?, // iff CONFIGURE_CODEC
    val expectedTransport: AudioTransportKind,
    val timeoutMillis: Long?,         // null → DEFAULT_TIMEOUT_MILLIS (10s)
    val verificationStrategy: CodecVerificationStrategy,
    val sideEffect: SideEffectClass,  // READ_ONLY_SAFE iff REFRESH_STATE
)
```

Factories: `select()`, `configure()`, `refresh()`.

## 4. CodecOperationResult (sealed)

Success ladder: `Accepted` → `Applied` → `Verified`; `AppliedUnverified`
(when strategy is `NONE`).

Refusals: `Rejected(reason)`, `Unsupported(codec)`, `NotSelectable(codec)`,
`NotConfigurable(codec)`, `NotObservable(codec)`, `PlatformUnavailable(reason)`.

Failures: `DeviceDisconnected`, `VerificationFailed(requested, observed)`,
`TimedOut`, `Failed(OmniBudsError)`.

- `isConfirmed` — true only for `Verified`.
- `isTerminalRefusal` — the five refusals above.

## 5. CodecControlState

```kotlin
data class CodecControlState(
    val requestedCodec: Codec? = null,
    val requestedConfiguration: CodecConfiguration? = null,
    val observedCodec: Codec = Codec.UNKNOWN,
    val observedConfiguration: CodecConfiguration? = null,
    val confirmedCodec: Codec = Codec.UNKNOWN,
    val confirmedConfiguration: CodecConfiguration? = null,
    val previousConfirmedCodec: Codec? = null,
    val previousConfirmedConfiguration: CodecConfiguration? = null,
    val freshness: CodecFreshness = CodecFreshness.UNKNOWN,
    val updatedAtMillis: Long = 0L,
)
```

## 6. Engine

```kotlin
class CodecControlEngine(
    resolver: CodecControlCapabilityResolver,
    adapter: CodecControlAdapter,
    liveness: DeviceLiveness,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    clockMillis: () -> Long = System::currentTimeMillis,
)
```

- `suspend fun execute(operation: CodecOperation): CodecOperationResult`
- `val states: StateFlow<Map<DeviceIdentity, CodecControlState>>`
- `suspend fun invalidate(device: DeviceIdentity)` — marks stale.

## 7. Validation rules (precheck order)

1. Liveness → `DeviceDisconnected`
2. Capability resolve
3. Transport (LC3/LE_AUDIO vs CLASSIC_A2DP) → `Rejected`
4. Per-type support → `Unsupported` / `NotSelectable` / `NotConfigurable`
5. Field support (`CodecFieldSupport`) → `Rejected`
6. Verification strategy vs verifiable → `Rejected` (REFRESH exempt)

## 8. Field support table

| Codec | Supported fields |
|---|---|
| LDAC | `QUALITY_MODE` |
| SBC, AAC, aptX, aptX HD, aptX Adaptive, aptX Lossless, LC3, Opus, UNKNOWN | (none) |

Theoretical; `DEVICE_OBSERVED` constraints may narrow, never widen without evidence.

## 9. Error categories

| Category | Retry | Session |
|---|---|---|
| `CODEC_OPERATION_FAILED` (new) | `SAFE_TO_RETRY` | no invalidation |

Existing `CODEC_NOT_OBSERVABLE`, `CODEC_STATE_STALE`, `CODEC_OBSERVATION_FAILED`
reused where they fit.

## 10. Android surface

| Class | Behavior |
|---|---|
| `AndroidCodecControlAdapter.apply()` | Always `NotAvailable` (no public API) |
| `AndroidCodecControlAdapter.observeAfterApply()` | Delegates to Phase 11 source; null (active codec unobservable) |
| `AndroidCodecControlCapabilityResolver.resolve()` | `selectable=false`, `configurable=false`, `verifiable=false` + `PLATFORM_LIMITATION` evidence |

## 11. Timeouts and cancellation

- Default 10s; per-operation override; `withTimeout` (structured).
- Timeout → `TimedOut`; caller `CancellationException` propagates.
- No `Thread.sleep`; no busy polling; no unbounded retries.

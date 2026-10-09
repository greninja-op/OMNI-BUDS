# Phase 13 — Specifications

## AudioQualityState fields

| Field | Type | Rule |
|---|---|---|
| device | DeviceIdentity | Key |
| transport | AudioTransportKind | UNKNOWN when unobserved |
| routeActive | Boolean? | null = unknown |
| transportConnected | Boolean? | null = unknown |
| negotiationState | NegotiationState | Derived |
| negotiatedCodec | Codec | May differ from active |
| activeCodec | Codec | UNKNOWN when unobservable |
| codecState | CodecState | Rung of the active/negotiated codec |
| supportedSampleRatesHz | List<Int> | Capability |
| activeSampleRateHz | Int? | OBSERVED confidence only |
| supportedBitDepths / activeBitDepth | List<Int> / Int? | Never default 16 |
| supportedBitrates / configured / observed | CodecBitrate | Never from identity |
| adaptiveBitrate | Boolean? | Evidence-gated |
| channelMode | ChannelMode | Never assume stereo |
| configuredQualityMode / observedQualityMode | QualityMode | Separate |
| adaptiveState | AdaptiveState | FIXED/ADAPTIVE/UNKNOWN |
| evidence | CodecEvidence | Provenance on every claim |
| observability | CodecObservability | NOT_OBSERVABLE ≠ UNSUPPORTED |
| freshness | CodecFreshness | CURRENT/STALE/UNKNOWN |
| hasConflict | Boolean | Precedence applied |
| timestampMillis | Long | Resolution time |

## Source precedence

1. VERIFIED_RUNTIME_OBSERVATION (Phase 12 VERIFIED control state)
2. PLATFORM_RUNTIME_METADATA (transport/codec snapshots)
3. VERIFIED_DEVICE_PROTOCOL (reserved — none exist)
4. CAPABILITY_DATABASE (SUPPORTED rung)
5. STATIC_INFERENCE (transport family only)
6. UNKNOWN

## Conflict policy

Current runtime observation beats stale history. Ties flag `hasConflict`.
Never silently overwrite; never delete the losing claim (it stays in
history/timeline).

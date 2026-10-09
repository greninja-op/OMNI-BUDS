# Phase 24 — Specifications

## State models

See `GlobalDeviceState.kt`. Key types: `GlobalDeviceState`,
`IdentityState`, `ConnectionState`, `ProtocolState`, `CapabilityState`,
`FeatureState`, `OperationStatus`, `BatteryState`, `AudioState`,
`ConfigurationState`, `PersistenceState`, `VendorFeatureState`,
`ObservedValue<T>`, `ObservationProvenance`, `Freshness`.

## Identifiers

`GlobalDeviceId`: non-blank string, address-free.

## Event types

`DeviceStateEvent` sealed: IdentityUpdated, ConnectionChanged,
ProtocolResolved, CapabilitiesUpdated, FeatureDesiredChanged,
FeatureOperationChanged, FeatureObserved, BatteryUpdated, AudioChanged,
PersistenceUpdated, VendorFeatureObserved.

## Update contracts

- Events carry `deviceId`, `sessionId?`, `sequence`, `sourceId`.
- Sequence must be ≥ last applied; equal → duplicate; less → stale.
- Non-connection events with a session id differing from the current
  session are rejected (`OldSession`).
- One event produces one immutable snapshot.

## Ordering rules

Per-device monotonic sequences. No comparison across devices or across
unrelated source clocks.

## Freshness policies

| Property | Current | Recent | Stale | Expired |
|---|---|---|---|---|
| Connection | < 30s | < 5m | ≥ 5m | ≥ 1h |
| Battery | < 5m | < 30m | ≥ 30m | ≥ 4h |
| Feature observation | < 5m | < 1h | ≥ 1h | ≥ 24h |
| Identity/firmware | session | session | new session | — |

Thresholds are advisory; the `Freshness` on each observation is authoritative.

## Merge semantics

- Desired: last write wins (by sequence).
- Observed: last valid observation wins; failed/ambiguous operations never
  overwrite.
- Executing: latest status per feature.
- Connection: latest generation wins.

## Error behavior

Typed `IngestResult`: Applied, Rejected(EventRejection), Duplicate.
Rejections recorded as bounded diagnostics.

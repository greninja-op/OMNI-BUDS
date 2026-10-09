# Phase 24 — Design

## Architecture

New package `com.omnibuds.core.globalstate` at layer 5:

- **GlobalDeviceId.kt** — address-free stable device identifier.
- **Observation.kt** — `ObservedValue<T>`, `ObservationProvenance`, `Freshness`.
- **GlobalDeviceState.kt** — immutable typed per-device snapshot with
  submodels (identity, connection, protocol, capabilities, features,
  battery, audio, configuration, persistence, vendor features).
- **DeviceStateEvent.kt** — typed subsystem events with device/session/
  sequence correlation; `IngestResult`, `EventRejection`.
- **DeviceStateAggregator.kt** — per-device event ingestion: validation,
  deduplication, ordering, session checks, atomic snapshot publication,
  bounded diagnostics.
- **GlobalDeviceStateRepository.kt** — one aggregator per device;
  `observeAllDevices` / `observeDevice` / `getDevice` / `removeDeviceState`
  over StateFlow (conflated).
- **DerivedState.kt** — deterministic derived properties.
- **StateConsistencyValidator.kt** — invariant checks.

## State ownership

The engine aggregates; it never invents. Each submodel documents its
source (see state-source-precedence.md). Existing repositories remain
authoritative; the engine consumes their outputs via typed events.

## Aggregation

One event → validate → device check → dedup → ordering → session check →
apply → publish. Mutex per device; atomic snapshot replacement.

## Event ordering

Monotonic sequence numbers per device; stale rejected; duplicates safe;
old-session events rejected; no cross-clock comparison.

## Freshness

Property-specific thresholds (documented in specs.md). Freshness is
per-observation, not per-snapshot.

## Concurrency

Per-device Mutex; structured concurrency; no global scopes; bounded
diagnostics (100); StateFlow conflation for backpressure.

## Persistence boundaries

The engine holds no persistence of its own. Desired config comes from
Phase 17; verification from Phase 18. Recovery reconstructs from sources.

## Failure handling

Source-stream failure affects only its device; typed diagnostics; the
validator reports inconsistencies without rewriting evidence.

# Phase 13 — Test Plan

## State machine (§37)
`NegotiationStateMachineTest`: happy path, failure, disconnect from every
active state, stale, reconnect, invalid transitions rejected, self-transitions.

## Codec transitions (§38)
`AudioQualityEngineTest`: AAC→LDAC produces `CodecChanged`; identical
re-ingestion produces nothing (dedup).

## Parameter transitions (§39)
Resolver + engine: parameter changes detected; UNKNOWN↔value policy
documented (meaningful loss during ACTIVE; initial discovery via
`CodecChanged`).

## Stale state (§40)
Disconnect → DISCONNECTED; `onBecameStale` → STALE with observation
preserved; reconnect with no observation → UNKNOWN (no masquerading).

## Multi-device (§41)
Device A (LDAC) / Device B (AAC): change A, B untouched; disconnect A, B
valid.

## Conflicting sources (§42)
Verified control state (AAC) vs snapshot (LDAC): AAC wins, `hasConflict`
true. Inferred metadata never populates active fields.

## Observability (§43)
`AudioQualityModelTest`: NOT_OBSERVABLE ≠ UNSUPPORTED.

## No false active (§44)
NEGOTIATED + inactive route → NEGOTIATED, never ACTIVE.

## Flow (§45)
Dedup, event timeline, bounded history (32), session lifecycle, no leaks
(scope cancellable).

## Android (§46)
`AndroidAudioQualityBridgeTest`: device-type → transport mapping.

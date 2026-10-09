# Phase 38 — Deferred Hardware Campaigns

All campaigns are defined for Phase 52. Nothing here executes
against physical hardware in Phase 38.

## Campaign A — Device identification

Discovery, manufacturer/model identification, firmware/protocol
version, identity ambiguity, unsupported-device fallback,
compatibility-profile matching.

## Campaign B — Read-only capability discovery

Supported/unsupported/unknown capabilities, battery readings where
exposed, transport/protocol consistency, read-only authorization.

## Campaign C — Connection lifecycle

Establishment, disconnect, reconnection, session cleanup, state
reconciliation, stale events.

## Campaign D — Protocol reliability

Framing, correlation, unexpected responses, timeouts, cancellation,
malformed data, transport interruption. Parser behavior is already
tested offline in Phase 37; device behavior is deferred.

## Campaign E — Configuration and persistence

Setting reads now; writes deferred. Acknowledgement, read-back,
disconnect/reconnect verification, persistence classification per
Phase 18.

## Campaign F — Audio-path non-interference

No media interception, no decode/re-encode, hardware-control
separation from Android audio transport, codec state distinctions.
Acoustic quality is deferred and requires measurement evidence.

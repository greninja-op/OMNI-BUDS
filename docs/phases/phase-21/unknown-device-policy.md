# Phase 21 — Unknown-Device Policy

## What OmniBuds may do

With an unknown device:
- Preserve discovery information.
- Observe OS connection state through public APIs.
- Display safe, available metadata.
- Collect minimal diagnostic information.
- Report unknown identity and capability status.

With a partially identified device:
- Expose evidence-backed metadata and observations.
- Must not use a broad manufacturer match to enable model-specific commands.

With an identified-but-unsupported device:
- Retain identity and observation information.
- Must not pretend vendor-specific control is supported.

## What OmniBuds may not do

- No vendor-specific commands on unknown devices.
- No speculative writes.
- No unsupported capability claims.
- No arbitrary protocol probing.
- No silent resolution of ambiguous identities.
- No promotion of lab schemas/hypotheses to production control.

## Ambiguous identity

Multiple plausible identities → all model-specific writes disabled until
adequate evidence resolves the ambiguity. The resolver preserves ambiguity;
it never picks the first candidate.

## Known protocol, unverified implementation

A schema, parser, or lab fixture does not authorize hardware writes.
`KNOWN_PROTOCOL_UNVERIFIED` is a restricted state.

## Connection is not authorization

A Bluetooth connection or successful pairing authorizes nothing beyond
observation. Proprietary control requires a registered, compatible,
verified protocol *and* per-capability write verification.

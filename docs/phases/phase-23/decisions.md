# Phase 23 — Decisions

## D-23-01: optional capability interfaces
**Decision:** `VendorFeatureExtension` is minimal (descriptor + resolve);
reader/writer are separate optional interfaces.
**Rationale:** Not every vendor supports every operation; forcing a
mega-interface invites stub implementations that lie.

## D-23-02: no sideways imports
**Decision:** `extension` imports only common(0) and state(0). Access-policy
integration is by injection, not import.
**Rationale:** `access` is layer 5; importing it would be sideways. The
established pattern is caller-side wiring.

## D-23-03: ambiguity as a result
**Decision:** Resolution returns all candidates; ambiguity is an explicit
result type, not an exception.
**Rationale:** Matches Phase 21's ambiguity discipline.

## D-23-04: unknown outcome as a result
**Decision:** Post-submission disconnect/timeout → `Unknown`, not assumed
success/failure.
**Rationale:** Honest uncertainty; no auto-retry of non-idempotent ops.

## D-23-05: no transport in the framework
**Decision:** The framework defines contracts; verified adapters execute.
**Rationale:** Keeps the safety boundary intact.

## D-23-06: identifier grammar alignment
**Decision:** `VendorFeatureId` uses the established 3-segment grammar
`vendor.<vendor>.<feature>` (matching `common.FeatureId.ofVendor` and
`feature.VendorFeatureContract`), not the conceptual 4-segment form from
the phase prompt. Product family is descriptor metadata (`featureNamespaces`
+ explicit `namespace` field on definitions), not an identifier segment.
**Rationale:** The 3-segment grammar is enforced in existing code; a second
incompatible grammar would diverge. The phase prompt's format was marked
conceptual; existing conventions take precedence per the prompt's own
guidance to adapt to project conventions.

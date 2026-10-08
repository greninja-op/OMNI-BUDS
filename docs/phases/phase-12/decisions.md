# Phase 12 — Decisions

**Status:** Authoritative.

---

## ADR-P12-001 — Honest unavailability over stubbed control

**Decision:** The Android control adapter reports `NotAvailable` and the
resolver reports `selectable=false` / `configurable=false` / `verifiable=false`
— as the *correct* behavior, not as TODO stubs.

**Rationale:** No public Android API exposes codec control. Manufacturing a
mechanism (hidden API, reflection, guessed vendor commands) would violate the
phase's absolute rule. The architecture is complete; the capabilities are
honestly absent.

**Consequences:** Every control operation on public Android APIs resolves to
`NOT_SELECTABLE` / `NOT_CONFIGURABLE` with `PLATFORM_LIMITATION` evidence.

## ADR-P12-002 — No parallel operation taxonomy

**Decision:** Reuse `SideEffectClass` from the feature engine; move it to
`common` (layer 0) instead of duplicating it in `codec`.

**Rationale:** It is a generic operation concept. Duplication would fork the
retry-safety contract. The move was forced by `DependencyDirectionTest`
(codec is layer 3, feature is layer 5).

## ADR-P12-003 — Rollback re-asserts the last verified state

**Decision:** On verification failure, rollback targets `confirmedCodec` (the
last verified state), not `previousConfirmedCodec`.

**Rationale:** The phase's "previous configuration" means "the configuration
before the failed change" — which is the current confirmed state. Re-selecting
it restores certainty. `previousConfirmed*` remains as history.

## ADR-P12-004 — REFRESH_STATE exempt from the verification gate

**Decision:** The precheck's "non-NONE strategy requires verifiable" rule does
not apply to `REFRESH_STATE`.

**Rationale:** Refresh *is* an observation, not a control operation awaiting
verification. Gating it would make the read-only path unusable.

## ADR-P12-005 — Rollback does not overwrite the recorded request

**Decision:** `runTransaction(isRollback=true)` skips the REQUEST state update.

**Rationale:** Rollback is a recovery action, not a new user request. The
recorded `requestedCodec` must still show what the user asked for.

## ADR-P12-006 — No new codec identities

**Decision:** Phase 12 adds no codec identities; it builds control
architecture over the Phase 1/11 vocabulary.

**Rationale:** The eight required codecs (SBC, AAC, 4× aptX, LDAC, LC3) plus
Opus/UNKNOWN already exist. Control needs capabilities, not new names.

## ADR-P12-007 — `CODEC_OPERATION_FAILED` is SAFE_TO_RETRY

**Decision:** The new error category allows retry only after re-observation.

**Rationale:** Mirrors `PROTO-ERR-002`: a failed write is followed by a read,
never a blind second write. `CODEC_NOT_OBSERVABLE` (NEVER_RETRY) was not
reused because "mechanism failed" differs from "nothing to observe".

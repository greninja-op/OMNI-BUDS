# Phase 11 — Codec Capability Engine: Decisions

## ADR-P11-001 — Extend the Phase 1 codec vocabulary; no parallel taxonomy

**Decision:** Phase 11 reuses `Codec`, `CodecFamily`, `CodecState`, `CodecCapability`
(ADR-P1-005) and extends them with evidence, observability, and metadata fields
(with backward-compatible defaults) instead of creating a parallel model.

**Rationale:** The ladder already solves the "seven states" requirement better
than independent booleans (which permit `active=true, supported=false`). A
second taxonomy would need perpetual synchronization.

## ADR-P11-002 — Add OPUS to the domain codec enum

**Decision:** `Codec.OPUS` added with `CodecFamily.CLASSIC_A2DP`.

**Rationale:** The platform genuinely reports Opus (`SOURCE_CODEC_TYPE_OPUS`,
`CODEC_ID_OPUS`). Dropping a real platform report to keep the enum matching the
prompt's "at least" list would make the mapping dishonest.

## ADR-P11-003 — Active codec is NOT_OBSERVABLE via public Android APIs

**Decision:** The adapter reports active/negotiated codec as UNKNOWN with
NOT_OBSERVABLE and explicit limitations, rather than using hidden APIs or
reflection.

**Rationale:** Verified by android.jar reflection (no `getCodecStatus` on
`BluetoothA2dp`, no codec getter on `BluetoothLeAudio`) and api-versions.xml
(no public codec broadcast). Using hidden APIs would break across OS versions
and violate the "legitimate APIs" principle the control phase depends on.

## ADR-P11-004 — `CodecApi35` isolated for VerifyError safety

**Decision:** API-35 codec code lives in its own class with an init-time
`check()`, loaded only under the `VANILLA_ICE_CREAM` guard (ADR-P10-005 pattern).

**Rationale:** Unconditional `BluetoothCodecType` references risk VerifyError on
older runtimes even when never called.

## ADR-P11-005 — Staleness marks, never deletes; reconnect re-observes

**Decision:** `stop()` marks runtime STALE; capabilities survive; `start()`
replaces with a fresh read.

**Rationale:** "Was LDAC-active, now stale" is honest; silent deletion hides
history and a stale ACTIVE rendering as current is a lie.

## ADR-P11-006 — Three codec error categories; failure ≠ "unsupported"

**Decision:** `CODEC_OBSERVATION_FAILED` (SAFE_TO_RETRY),
`CODEC_NOT_OBSERVABLE` (NEVER_RETRY), `CODEC_STATE_STALE` (SAFE_TO_RETRY);
none invalidates a session.

**Rationale:** A failed read is not a negative claim. The blind-retry set grows
from two to four, all side-effect-free reads.

## ADR-P11-007 — `CodecScopeTest` machine-checks the boundary

**Decision:** Vocabulary bans (switching, interception, android imports,
invented bitrates) are enforced by test, not just by review.

**Rationale:** Phase 10's `PhaseTenScopeTest` precedent: boundaries that matter
are tested.

## ADR-P11-008 — Engine in `com.omnibuds.core.codec` (layer 3)

**Decision:** New `codec` area at layer 3; vocabulary stays in `audio` (layer 2).

**Rationale:** The engine coordinates per-device state (like `session` at
layer 3); the vocabulary remains dependency-free. `DependencyDirectionTest`
enforces the placement deliberately.

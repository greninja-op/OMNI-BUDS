# Phase 9 — Decisions

**Phase:** 9 — Hardware Feature Engine · **Scope id:** P9
**Document status:** accepted

## ADR-P9-001 — Extend ConfigurationValue instead of forking a value hierarchy

**Status:** accepted · **Date:** 2026-10-08

The prompt's §8 wants BOOLEAN/ENUM/INTEGER/FLOAT/RANGE/STRING/STRUCTURED/BITMASK/
CUSTOM value shapes. Two designs were considered: (a) a new `FeatureValue`
sealed hierarchy in the feature area plus a mapping adapter to
`ConfigurationValue` at the protocol seam; (b) extending `ConfigurationValue`
itself with the five missing shapes.

Chosen: (b). A forked hierarchy would let the same value mean two things and
would need a mapping layer that could smuggle semantics — the exact failure
ADR-P8-001 refused when it wrapped `DeviceCapabilities` instead of forking
parallel enums. The protocol seam (`FeatureReadSupport`/`FeatureWriteSupport`)
already speaks `ConfigurationValue`, so one hierarchy means no mapping layer
at all. The new shapes live in the config area beside the existing ones;
`FeatureValueType` in the feature area names the shapes and checks them.

## ADR-P9-002 — The FeatureProtocolPort seam

**Status:** accepted · **Date:** 2026-10-08

The engine reaches the device only through a handed-in `FeatureProtocolPort`
(read/write + supports-flags), mirroring the ADR-P8-005
`CapabilityDiscoverySource` pattern. The alternative — the engine calling
`FeatureReadSupport`/`FeatureWriteSupport` directly — is layer-legal (feature
is layer 5, protocol is layer 4) but would couple the engine to the protocol
layer's shape. The seam keeps the engine import-free of the protocol and
transport layers (enforced by `PhaseNineScopeTest`), makes the engine
unit-testable against a scripted port, and leaves the binding of a port to a
resolved protocol's read/write support to the later phase that owns L3/L4
wiring. Phase 9 ships no production implementation; the only implementations
are test-only scripted ports.

## ADR-P9-003 — Reconciling the prompt's two state vocabularies

**Status:** accepted · **Date:** 2026-10-08

The prompt's §2 rule 3 names REQUESTED / DEVICE_CONFIRMED / FAILED / UNKNOWN
while §9 names UNKNOWN / AVAILABLE / PENDING / CONFIRMED / FAILED /
UNAVAILABLE. Per master §58 the conflict is recorded, not silently resolved:
the §9 six-state model is adopted as the control axis, and the rule-3
vocabulary maps onto it — requested → `Pending.requested` (explicitly not
device state), device-confirmed → `Confirmed`, failed → `Failed`, unknown →
`Unknown`. `AVAILABLE` (capability established, operable) and `UNAVAILABLE`
(supported but momentarily unusable) have no rule-3 counterpart and are kept.

## ADR-P9-004 — EQ bands are values, not identities

**Status:** accepted · **Date:** 2026-10-08

The prompt's §16 lists PRESET_EQ / GRAPHIC_EQ / PARAMETRIC_EQ as potential
forms. `CoreFeature`'s KDoc establishes that "per-band EQ … are feature
*values* discovered per device, not new core identities". The design follows
the established architecture over the prompt's sketch: one `equalization.equalizer`
identity carrying a structured value whose `form` field selects
preset/graphic/parametric/tone, with `EqualizerValues` constructors shaping
valid values. This keeps one addressable control per device instead of three
identities that would need mutual-exclusion relations to avoid contradictory
drives.

## ADR-P9-005 — WRITE_ONLY is reserved vocabulary, never produced

**Status:** accepted · **Date:** 2026-10-08

The prompt's §11 lists WRITE_ONLY access. `FeatureCapability`'s `init` forces
`readable && writable` on every controllable rung, and ARCH-TERM-003 forbids
adding rungs to `CapabilityState`, so no capability record can express
"writable without read-back" today. Rather than weakening the capability
model, `FeatureAccess.WRITE_ONLY` exists as reserved vocabulary with KDoc
stating it is never produced by the Phase 9 derivation; producing it later
requires a capability-model ADR. A feature whose read-back cannot be
established is not offered as a control at all.

## ADR-P9-006 — Conflict activity convention

**Status:** accepted · **Date:** 2026-10-08

`ConflictsWith` needs to know when the *other* feature is "engaged".
`isActiveValue` encodes the documented convention: boolean `true`, a mode
whose technical name is not `"off"`, and a non-zero number are engaged;
anything else confirmed is conservatively engaged. Definitions use the
technical name `"off"` (`INACTIVE_MODE_NAME`) for the inactive mode. This is a
modelling convention, not a hardware claim, and it is documented as one so it
can be argued with. The alternative — blocking on any confirmed value —
would refuse legitimate sequences (e.g. enabling transparency after ANC was
confirmed on, where the hardware itself resolves the transition).

## ADR-P9-007 — Failed reads keep last-confirmed as stale knowledge

**Status:** accepted · **Date:** 2026-10-08

A failed read moves the feature to `Failed` but keeps `lastConfirmed`: a
transient failure does not erase what the device previously reported. This
differs from discovery (Phase 8), where a failed read leaves the feature
`UNKNOWN` — discovery establishes capability, where there is no prior truth
to keep; the feature engine tracks control state, where there is. The
distinction is documented so the two behaviours are not mistaken for
inconsistency.

## ADR-P9-008 — Superseded writes report the newer truth

**Status:** accepted · **Date:** 2026-10-08

When a device-reported update supersedes an in-flight write (generation
mismatch at completion), the engine leaves the device-reported `Confirmed`
state alone and returns it as the outcome rather than claiming the write's
result or failing the operation. Rationale: the state already holds the newer
truth, and the outcome's job is to carry truth, not to litigate attribution.
If the superseding left the feature non-confirmed, the outcome is
`DEVICE_STATE_UNKNOWN`.

## ADR-P9-009 — The standard catalogue declares only safe relations

**Status:** accepted · **Date:** 2026-10-08

The prompt's §19 examples (`ADAPTIVE_ANC requires ANC`, `HEAD_TRACKING requires
SPATIAL_AUDIO`) are explicitly "examples only" and "must not be hardcoded as
universal hardware truths". The catalogue declares `Requires` only where the
relationship is structural to the control (`anc-level` → `anc`,
`transparency-level` → `transparency`, `adaptive-anc` → `anc` per the prompt's
own example, `auto-transparency`/`voice-passthrough` → `transparency`) and the
`anc` ↔ `anc-mode` conflict pair. `head-tracking` carries no requires-edge:
whether it needs spatial audio is device-specific and belongs to discovery,
not to the universal catalogue. The richer relation kinds are proven by tests
against fixture definitions.

## ADR-P9-010 — No new error categories

**Status:** accepted · **Date:** 2026-10-08

Following ADR-P8-004, the prompt's §24 error names map onto existing
`OmniBudsErrorCategory` values instead of minting new ones
(e.g. `INVALID_VALUE` → `INVALID_STATE`, `DEVICE_REJECTED` →
`WRITE_REJECTED`, `DEVICE_STATE_UNKNOWN` → `READ_FAILED`). Cancellation stays
an `OperationOutcome.Cancelled`, not an error (ADR-P1-004). Retry behaviour
derives from the category's `RetryClass`, so a side-effecting command can never
be retried by accident.

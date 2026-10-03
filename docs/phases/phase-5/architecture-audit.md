# Phase 5 — Architecture Audit

**Phase:** 5 · **Owner:** orchestrator (Agent A role)
**Document status:** the pre-execution audit that shaped ADR-P5-001 … 012. Findings were read out of the
committed tree at the Phase 5 start (`36abab0` and its code `a5a177d`'s parent state), not assumed.

## 1. What already existed (reuse, not re-creation)

Phase 1 built the identity vocabulary and then deliberately left it **unfed**:

- `DeviceFingerprint` (layer 2) — structural evidence, an order-stable `identityKey()` that folds the
  address separator out, `NOTHING_KNOWN_KEY`, and `isEntirelyUnobserved`. Grep of every `src/main` tree
  found it constructed only by its own `empty()`: **a model with no producer.**
- `ManufacturerDataEntry` — opaque `(companyId, dataHex)` evidence with no interpretation and
  `isEntirelyUnobserved`.
- `DeviceIdentity` — reported manufacturer/model/displayName/modelId, `mergedWith` fill-only, blank→null.
- `ProtocolIdentification` (`core.protocol`) — the *conclusion* pattern with a `MatchEvidence` ladder
  and `FALLBACK_UNKNOWN`, and the rule that a candidate list is never a verdict. This became the
  structural precedent for `IdentificationResult` and for capping an inferred confidence
  (`ProtocolIdentification.kt` `init` caps `FALLBACK_UNKNOWN` at `INFERRED` — same shape ADR-P5-004
  reuses).

Phase 3 contributed the join (`DeviceObservationKey`, self-redacting, address-free) and the profile/bond/
link axes identity signals read from, and — critically — refused `DEVICE_CLASS` as a primary signal and
refused service discovery (needs a transport).

**Decision (ADR-P5-001):** reuse these; add identification types *into* `com.omnibuds.core.device`
(layer 2) rather than minting a 13th area or a competing fingerprint/identity model. Prompt §2's "do not
create duplicate identity models or competing device registries" is satisfied by construction and by
`PhaseFiveScopeTest.phaseFiveAddedNoNewSourceArea`.

## 2. Layer arithmetic and dependency direction

`common/state` L0 → `transport/platform` L1 → `device/capability/audio/config/diagnostics` L2 →
`session/persistence` L3 → `protocol` L4. Phase 5's new types are all L2 (`device`); the session
integration touches L3 (`session`) and imports *downward* into L2 only. `DependencyDirectionTest` and the
17 architecture checks stayed green unchanged — Phase 5 needed no relaxation of the layer map, which is the
point of putting identification in `device` and the enrichment call in `session`. No L2 source imports
`transport`, `persistence`, `protocol`, or a platform control seam
(`PhaseFiveScopeTest.theIdentificationSourcesReachNoControlTransportOrPersistenceSeam`).

## 3. How identity integrates with sessions (the audit's central question)

The audit's finding was **what not to merge**. Phase 4 made `DeviceState.connection` the single
authoritative connection fact and kept `DeviceSession.identity` as reported facts only. Identification is a
*conclusion*, so it was given its own field (`TrackedDeviceSession.productIdentity`) beside the report,
never folded in (ADR-P5-009). The safe update policy is a method (`enrichIdentity`) whose signature makes
the four prompt §14 guarantees structural: it takes an engine `sessionId` (cannot re-attribute by name),
returns a refusal for an unknown id (cannot mint), writes only `productIdentity` + `fingerprint`
(cannot touch `connection`), and moves no `DeviceState.revision` (cannot beat a real transition). The
eighth event keeps the edge visible without reusing `SessionIdentityChanged`, whose meaning is reported-field
counts.

## 4. What Phase 5 can actually observe (ADR-P5-003/007)

Cross-checking prompt §6's fourteen signal categories against the platform research the repo already
holds: passive `BluetoothDevice` reads (name, alias, type, class, bond, cached `getUuids()`) are
reachable under the one already-declared `BLUETOOTH_CONNECT`; advertisement manufacturer data needs a
scan; characteristic UUIDs need a GATT open; `getMetadata(int)` is `@SystemApi`/`BLUETOOTH_PRIVILEGED`.
The user's standing directive ("build the product, skip the phone") removed the scan and the collector
from scope, so `MANUFACTURER_DATA`/`CHARACTERISTIC_UUID` are `UNAVAILABLE` kinds and metadata has no kind.
The audit recorded the corresponding over-claim: the enum carried `DEVICE_DISCOVERY_SCAN authorizedInPhase
= 5` while Phase 5 opens no scanner; corrected to 6 (ADR-P5-012), the same kind of fix ADR-P3-018 made.

## 5. Open items the audit escalated rather than silently filled

- **`ADR-P0-018` was `proposed` but relied upon.** Phase 1 code (`ManufacturerDataEntry.kt`,
  `DeviceFingerprint.kt`) cited it as a decision while `docs/decisions/README.md` rule 3 forbids relying
  on a `proposed` ADR. Settled as `enumerate → identify` on the user's explicit deferral, recorded in
  ADR-P5-002, and the index/`decisions/README.md` and `MASTER-CONTEXT.md` entries updated to `accepted`.
- **No evidence-backed rules exist.** Prompt §11 forbids inventing signatures; ADR-P1-013 is the
  precedent for shipping the registry empty. The audit accepted an empty registry as correct rather than
  padding it, and left the first real rule to arrive with a citation and a diff.
- **The collector seam is unbuilt by design.** Signal factories and the `UNAVAILABLE` qualities are the
  contract the future `platform/android/.../identity` collector targets; not authorizing it here is a
  decision (RISK-095), not an oversight.

## 6. Conformance summary

Reuse over duplication (ADR-P5-001); conclusion separated from report (ADR-P5-009); the passively-observable
boundary kept reversible and honestly tagged (ADR-P5-007/012); emptiness shipped as a tested decision
(ADR-P5-006); ambiguity and unknown preserved as results (ADR-P5-004/008); address-free and local and
unpersisted (ADR-P5-010). The layer map and architecture checks were not widened to admit any of it.

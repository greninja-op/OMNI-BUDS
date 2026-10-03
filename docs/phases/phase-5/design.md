# Phase 5 — Design

**Phase:** 5 — Device Fingerprinting & Identification · **Owner:** orchestrator
**Document status:** describes the tree at `a5a177d`. Decisions are cited from `decisions.md`
(ADR-P5-001 … ADR-P5-012) by id and not restated. Requirements are `requirements.md`
`OB-P5-REQ-001 … 030`.

## 1. What the phase is, in one honest sentence

Phase 5 builds the *machinery of identification* — signals in, a versioned fingerprint and a
confidence-tagged result out — and ships it pointed at an **empty registry**, because no device
signature has been documented and inventing one is the failure the phase's own governance refuses
(ADR-P5-006). Every identification today answers `Unknown`, and that is the correct answer, not a gap.
The pipeline prompt §5 sketches is implemented end to end except its final arrow: protocol resolution is
future work and is not reachable from anything here (prompt §17).

## 2. The pipeline, mapped onto the existing architecture

```text
ConnectedDeviceSession (Phase 4)
        |        \
        |         +-- reported DeviceIdentity  (what the platform SAID)   [separate field]
        v
IdentitySignal*        <- typed evidence, quality-stated   (core.device, L2)
        |
        v
IdentityNormalizer     <- trim / collapse / case-fold, version 1   (ADR-P5-005)
        |
        v
IdentityEngine.buildFingerprint -> DeviceFingerprint   (Phase 1's type, reused — ADR-P5-001)
        |
        v
IdentityEngine.identify(signals, DeviceIdentityRegistry) -> IdentificationResult  (sealed, 7 cases)
        |
        +--> DeviceSessionEngine.enrichIdentity(sessionId, fingerprint, result)
                     |
                     v
             TrackedDeviceSession.productIdentity   (a conclusion, kept beside the report)

        (protocol resolution — prompt §5's final step — is NOT implemented; Phase 6/7)
```

Everything the phase added lives in `com.omnibuds.core.device` (layer 2) except the three session
integration points in `com.omnibuds.core.session` (layer 3), which import downward into device and
never sideways. No 13th source area was created (ADR-P5-001), and `PhaseFiveScopeTest` asserts it.

## 3. The signal model (ADR-P5-003, ADR-P5-010)

An `IdentitySignal` is `(kind, quality, value?, source, reliability, observedAt?)`. Nine kinds exist,
and the set is **closed by what this phase can actually obtain passively**:

| Kind | Reachable in Phase 5? | Why |
|---|---|---|
| `REPORTED_NAME` | yes, `OBSERVED` | platform name read |
| `USER_ALIAS` | yes, `OBSERVED` | user-set alias; least trustworthy of names |
| `DEVICE_CLASS` | yes, `OBSERVED`, corroborating only | Phase 3 documented it as unreliable for services |
| `DEVICE_TYPE` | yes, `OBSERVED` | classic/LE/dual; kept as a signal, **not** promoted to a transport |
| `BOND_STATE` | yes, `OBSERVED` | from the bond axis |
| `OBSERVED_PROFILE` | yes, `OBSERVED` | a profile that reported a link |
| `SERVICE_UUID` | yes, from the platform cache; empty cache is `UNKNOWN` | `getUuids()` starts no discovery |
| `MANUFACTURER_DATA` | **no — `UNAVAILABLE`** | advertisement data needs a scan this phase declines (ADR-P5-007) |
| `CHARACTERISTIC_UUID` | **no — `UNAVAILABLE`** | needs the GATT discovery prompt §17 forbids |

Platform metadata (`getMetadata(int)`) gets **no kind at all** because it is `@SystemApi` behind
`BLUETOOTH_PRIVILEGED` and is not callable from this module; a missing kind is the honest shape for an
unreachable read, whereas an `UNAVAILABLE` kind is a question the phase could ask about in principle but
declined to (scan) or cannot reach without a forbidden transport (GATT).

`SignalQuality` is ADR-P0-016's tiered unknown widened to identity: `OBSERVED`, `DERIVED`, `INFERRED`,
`UNKNOWN` (not read this round), `UNAVAILABLE` (this phase cannot ask), `INVALID` (a value arrived and was
rejected). The factory is where untrusted text stops being input: blank collapses to `UNKNOWN` with a
null value, oversized or control-character-bearing text becomes `INVALID` with the text discarded, so a
hostile value is never turned into either a crash or an absence.

No kind holds a Bluetooth address in its value or its text, and no identity type declares an address
member. Attribution — "the same device as that earlier report" — remains `DeviceObservationKey`'s job
alone (ADR-P5-010, SEC-ID-003).

## 4. Normalization (ADR-P5-005)

`IdentityNormalizer` is the whole of prompt §7's "must not destroy meaningful identity distinctions"
argument. v1 does three things — trim, collapse internal whitespace runs, case-fold — and the version
travels with every derived value through `normalizeVersioned`, because a rule written against v1 must not
silently match evidence read as v2. What is **absent on purpose**: transliteration/accent folding
(`:core` forbids `java.text.Normalizer`, and folding vendor-chosen text locale-sensitively is the very
destruction §7 names), brand-token stripping, camelCase splitting, punctuation removal, and any fuzzy or
edit-distance match (ADR-P5-004 refuses scores with no calibrated meaning). `WF-1000XM3` and `XM4`, and
`LinkBuds` and `LinkBuds S`, therefore stay distinct — tested.

## 5. Fingerprinting (ADR-P5-001, ADR-P5-007)

`buildFingerprint` reuses Phase 1's `DeviceFingerprint` and fills **only** the dimensions with a producer:
service UUIDs (upper-cased) and device class. Manufacturer data, characteristic UUIDs, transport
candidates and protocol candidates stay empty. The deliberate choice worth stating: a `DEVICE_TYPE` read
is *kept as a signal* but is **not** translated into `transportCandidates`, because a passive type read
does not establish which channel reaches a device and prompt §17 keeps transport/protocol resolution ahead
of this phase. `isEntirelyUnobserved` and `NOTHING_KNOWN_KEY` keep "nobody looked" a distinct, recognisable
value from "a sparse but real device", and the byte-array/content equality Phase 1 built still holds.

## 6. The registry (ADR-P5-006)

`DeviceIdentityRegistry` is a versioned value: `registryVersion`, `ruleSetVersion`, manufacturers, rules.
`ManufacturerIdentity` is a canonical slug plus display name plus normalized aliases. An
`IdentificationRule` carries the full prompt §11 field set — id, target, `MatchCondition`s, requested
confidence, rule version, evidence source, citation, known limitations — and its constructor refuses four
things: `ASSIGNED_INTERNALLY` evidence (the shape a fabricated signature takes), an empty condition list
(would match every device), `VERIFIED` confidence (needs hardware), and a blank citation. Conditions are
data, not closures, so a rule can be printed and audited. `MatchCondition.signalKind` feeds the
independence arithmetic below.

`empty()` is the shipped production instance; `builtIn(rules = ...)` exists so a test — or the future
phase that lands real citations — supplies rules explicitly rather than overriding a hidden default list.
`PhaseFiveRegistryTest` asserts the emptiness and scans every `src/main` file so the first real rule has
to arrive with a citation and a diff.

## 7. Matching and confidence (ADR-P5-004, ADR-P5-008)

`IdentityEngine.identify` is pure and total (never throws). Its decision order is itself the honesty
policy:

1. any `INVALID` signal → `InvalidEvidence` (rejected data is decided before absence, so a data-quality
   fact is not folded into "unknown device");
2. empty registry → `Unknown` (the shipped state);
3. no usable signal → `InsufficientEvidence` (the engine refuses to read absence as a negative);
4. rules whose conditions all hold on usable signals survive; none survive → `Unknown`;
5. survivors over distinct manufacturer/model targets → `Ambiguous`, carrying candidates and **no**
   single-manufacturer field, so there is nothing for a caller to take an arbitrary winner from;
6. one target → the winner is ordered by specificity, then rule version, then id, and the **granted**
   confidence is `min(asked rung, what the corroborating independent kinds support)`: `HIGH` needs two
   independent kinds, a single kind caps at `MODERATE`, and the downgrade is the reported confidence, not
   a hidden one. A model plus a granted `HIGH` is `Exact`; a model below `HIGH` is `Likely`; a
   model-less rule is `ManufacturerOnly`.

Confidence is categorical (`VERIFIED/HIGH/MODERATE/LOW/UNKNOWN`), each rung with a written
`independentKindsRequired` and `minimumEvidence`; `VERIFIED` is declared for the phases that will reach it
and is unreachable from Phase 5 code, guarded by construction. "Manufacturer identity without a model" is
the most a name-only phase can honestly reach, and the type says so.

## 8. Session integration (ADR-P5-009)

`TrackedDeviceSession` gains one field, `productIdentity: IdentificationResult?`, and one method,
`enrichedWith(...)`, which merges *reported* identity via `DeviceSession.withEvidence` (fill-only, never a
rewrite), replaces the fingerprint snapshot, and attaches the conclusion — leaving session id, key, basis,
timeline, `connection` and `DeviceState.revision` untouched. `DeviceSessionEngine.enrichIdentity(sessionId,
fingerprint, result)` takes the engine's own id, not a device key, so it can only enrich a session that
already exists; an unknown id is a refusal, never a create. The reported `DeviceIdentity` and the inferred
`IdentificationResult` are siblings, never merged: a matched manufacturer cannot overwrite a reported one,
and a report cannot launder itself into a `HIGH`. The engine publishes the eighth event,
`SessionIdentityEnriched` (id, confidence, identified-boolean, time) — deliberately not folded into
`SessionIdentityChanged`, which counts *reported* fields, and never a `SessionUpdated`, because
identification is not a transition.

## 9. What this design refuses to be

Not a support detector (identification ≠ capability ≠ protocol compatibility — `maySupportProtocolResolution`
is a statement that a result may inform a *later* decision, and is false for five of seven outcomes).
Not a store (nothing persisted; no per-device identity history). Not a network client (local,
deterministic). Not a scanner (ADR-P5-007, and ADR-P5-012 moves the scan tag off this phase). Not a
population of known devices — it is the engine, correctly reporting that it does not yet know.

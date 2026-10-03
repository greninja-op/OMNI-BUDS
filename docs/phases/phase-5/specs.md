# Phase 5 — Specs

**Phase:** 5 · **Owner:** orchestrator
**Document status:** normative contracts for the tree at `a5a177d`. Where a contract is enforced by a
constructor or a test, that is named; prose that a check already holds is not repeated as hope. Cites
`decisions.md` (ADR-P5-001 … 012) by id.

## 1. Identity signal contract (`IdentitySignal`, `core.device`)

`IdentitySignal(kind, quality, rawValue?, source, reliability, observedAtEpochMillis?)`.

**`IdentitySignalKind`** (closed by Phase 5 reach, ADR-P5-007): `REPORTED_NAME`, `USER_ALIAS`,
`DEVICE_CLASS`, `DEVICE_TYPE`, `BOND_STATE`, `OBSERVED_PROFILE`, `MANUFACTURER_DATA`, `SERVICE_UUID`,
`CHARACTERISTIC_UUID`. `MANUFACTURER_DATA` and `CHARACTERISTIC_UUID` are always `UNAVAILABLE` in this
phase. Platform metadata (`getMetadata`) has **no kind** — it is `@SystemApi`/`BLUETOOTH_PRIVILEGED` and
unreachable from `:core`.

**`SignalQuality`**: `OBSERVED` (platform stated it now), `DERIVED` (a versioned rule computed it),
`INFERRED` (evidence leans this way without establishing it), `UNKNOWN` (not read this round),
`UNAVAILABLE` (this phase cannot ask), `INVALID` (a value arrived and was rejected). No two collapse.

**`SignalSource`**: `PLATFORM`, `PAIRING_RECORD`, `NORMALIZED`, `OBSERVATION`.
**`SignalReliability`**: `VENDOR_REPORTED_TEXT`, `PLATFORM_ATTRIBUTE`, `PAIRING_FACT`,
`PROFILE_OBSERVATION`, `USER_CHOSEN_TEXT`, `NONE`.

**Invariants (constructor-enforced in `observed(...)`):**
- `MAX_VALUE_LENGTH = 128`; longer input → `INVALID`, `rawValue = null` (not truncated).
- any ISO-control character in the trimmed value → `INVALID`, `rawValue = null`.
- null or blank-after-trim → `UNKNOWN`, `rawValue = null` (never `""`).
- only `OBSERVED` retains a value and the caller's reliability; every other quality carries `NONE`.
- `isUsable` is true for exactly `OBSERVED`, `DERIVED`, `INFERRED`.

**Prohibited:** a Bluetooth address as a value or in any value text (SEC-ID-003); a `""` standing for a
missing fact; a `UNAVAILABLE`/`UNKNOWN` read being recorded as "the device does not have X".

## 2. Normalization contract (`IdentityNormalizer`, ADR-P5-005)

`NORMALIZATION_VERSION = 1`. `normalize(raw)` = trim → collapse internal whitespace runs to a single
space → `lowercase()` (Kotlin's, locale-independent). Blank/`null` → `null`.
`normalizeVersioned(raw)` → `NormalizedText(value, normalizationVersion = 1)` or `null`.

**MUST NOT:** transliterate or fold accents; strip brand tokens; split camelCase; remove punctuation;
apply fuzzy/edit-distance logic. `WF-1000XM3` ≠ `WF-1000XM4` and `LinkBuds` ≠ `LinkBuds S` after v1.
A rule's stored comparison target is normalized text tagged with the version that produced it.

## 3. Fingerprint format (`DeviceFingerprint`, reused from Phase 1; ADR-P5-001/007)

`buildFingerprint(signals)` populates only: `serviceUuids` (usable `SERVICE_UUID` values, upper-cased, as
a set) and `deviceClass` (first usable `DEVICE_CLASS` value parsed as `Int`, else `null`).
`manufacturerData`, `characteristicUuids`, `transportCandidates`, `protocolCandidates`, `firmware` stay
at their empty/`null` defaults because Phase 5 produces none.

`identityKey()` (Phase 1's) is a deterministic, order-stable string of the form
`omnibuds-fingerprint/v1;<sections>` over structural evidence only; sets and lists are sorted; every token
has the address separator `':'` folded to `'-'`, so no MAC can appear and free-text fields cannot smuggle
one in. `DeviceFingerprint.NOTHING_KNOWN_KEY` (`=nothing-known`) is returned verbatim when
`isEntirelyUnobserved`. Byte-content equality (not reference) governs equivalence; a different reading
order of the same evidence yields the same key. Firmware/connection/battery/timestamps are out of the key
by design.

## 4. Confidence semantics (`IdentificationConfidence`, ADR-P5-004)

Categorical, never numeric. Each rung declares `independentKindsRequired`, `minimumEvidence`
(a `VerificationLevel`), `reachableInPhaseFive`, and a derived `maySupportProtocolResolution`.

| Rung | independent kinds | minimum evidence | reachable in P5 | may inform protocol |
|---|---|---|---|---|
| `VERIFIED` | 3 | `HARDWARE_VERIFIED` | **no** | yes |
| `HIGH` | 2 | `IMPLEMENTED` | yes | yes |
| `MODERATE` | 1 | `IMPLEMENTED` | yes | yes |
| `LOW` | 0 | `INFERRED` | yes | no |
| `UNKNOWN` | 0 | `INFERRED` | yes | no |

A "kind" is a distinct `IdentitySignalKind` family: reported name + user alias + normalized name is one
kind, not three, and cannot reach `HIGH`. The engine grants at most `min(asked, kinds-supported)`; an
over-claiming rule is capped and the capped value is what is reported.

## 5. Identification result contract (`IdentificationResult`, sealed)

Common fields on every outcome: `confidence`, `matchedRuleIds`, `evidence` (the signals read, as
references), `registryVersion`, `ruleSetVersion`, `limitations`; derived `isIdentified` and
`maySupportProtocolResolution`.

| Outcome | when | `isIdentified` |
|---|---|---|
| `Exact(manufacturer, model, …)` | model rule fired, granted `HIGH` (2 independent kinds) | yes |
| `Likely(manufacturer, model, …)` | model rule fired, granted below `HIGH` | yes |
| `ManufacturerOnly(manufacturer, …)` | model-less rule fired | yes |
| `Ambiguous(candidates, …)` | survivors over distinct targets | **no** — no single manufacturer/model field exists |
| `Unknown(…)` | registry empty, or no rule fits | no |
| `InsufficientEvidence(missingKinds, …)` | signals present but none usable | no |
| `InvalidEvidence(rejectedKinds, …)` | any signal was `INVALID` | no |

`maySupportProtocolResolution` is `isIdentified && confidence.maySupportProtocolResolution` — false for
`Ambiguous`, `Unknown`, `InsufficientEvidence`, `InvalidEvidence` always, and for identified results below
`MODERATE`. It states a result may be *read as evidence toward* a Phase 7 decision; nothing here resolves a
protocol or asserts a capability.

## 6. Matching rules (ADR-P5-004/005/008)

A rule fires iff **every** `MatchCondition` has a usable signal of its `signalKind` that satisfies it.
Condition satisfaction: `NormalizedNameEquals/Prefixed` compare `normalize(signal.rawValue)`;
`CachedServiceUuidEquals` compares upper-cased values (an empty cache satisfies nothing);
`DeviceClassBetween` parses and range-checks; `DeviceTypeEquals` is case-insensitive equality. Winner
ordering is specificity (distinct kinds), then `ruleVersion`, then id, and applies only when all survivors
share one target — ordering decides presentation, never which disagreeing candidate wins.
Decision order: invalid → empty-registry-unknown → no-usable-signal-insufficient → survivors →
(distinct targets ? ambiguous : resolve-winner).

## 7. Registry versioning and emptiness (ADR-P5-006)

`DeviceIdentityRegistry(registryVersion, ruleSetVersion, manufacturers, rules)`, positive versions,
distinct canonical ids, distinct rule ids (all constructor-`require`d). `empty()` = version 1,
manufacturers `[UNKNOWN]`, **zero rules** — the shipped production instance. `builtIn(...)` prepends the
built-in `UNKNOWN` and rejects redeclaring it. `IdentificationRule` refuses: `ASSIGNED_INTERNALLY`
evidence, empty conditions, `VERIFIED` confidence, blank citation, blank id/version. No `src/main` source
constructs a rule or a name `MatchCondition` (parenthesized call sites), enforced by
`PhaseFiveRegistryTest.noMainSourceDeclaresARuleOrAMatchCondition`.

## 8. Ambiguity and unknown handling (ADR-P5-008; prompt §13)

Ambiguity is a result with candidates in it, never a chosen winner; the `Ambiguous` type exposes no
singular manufacturer/model, so "take the first" is not a thing a caller can do. Unknown is first-class and
not a failure. The five unknown shapes are kept distinct: unknown manufacturer (`Unknown`), known
manufacturer / unknown model (`ManufacturerOnly`), ambiguous model (`Ambiguous`), insufficient evidence
(`InsufficientEvidence`), invalid metadata (`InvalidEvidence`). For any unknown the engine preserves the
signals, returns an explicit result, and selects no protocol and no controls.

## 9. Identity/session separation (ADR-P5-009)

`TrackedDeviceSession.productIdentity: IdentificationResult?` is held **beside** the reported
`DeviceIdentity`, never merged. `enrichIdentity(sessionId, fingerprint, result)` cannot mint a session
(unknown id → `Failure(INVALID_STATE)`), cannot change `connection`, and does not move
`DeviceState.revision`. Fingerprint replaces the prior snapshot (two passes are never welded). A changed
display name re-attributes nothing. Session identity (which instance) and product identity (which model)
are separate fields with separate producers.

## 10. Privacy rules (ADR-P5-010)

No Bluetooth address in any signal kind, value, key, or printed output. Nothing persisted; no
unidentified-device history; no raw advertising capture. Names, aliases and manufacturer data are
untrusted input validated at the signal factory. Identification is local and deterministic with no network
dependency; `:core` stays Android-independent (the architecture test holds it).

## 11. Known platform limitations

- Advertisement `MANUFACTURER_DATA` and `CHARACTERISTIC_UUID` are unreachable without a scan or a GATT
  open, both out of scope; they read `UNAVAILABLE`, which is a statement about the phase, not the device.
- `SERVICE_UUID` comes from the platform's local cache, which is empty until someone has queried the
  device; an empty cache is `UNKNOWN`, never a services list.
- `DEVICE_CLASS` is documented by the platform as not reliably describing supported services; it is
  corroborating-only and never a sole condition.
- `USER_ALIAS` is user-chosen Settings text and is never vendor evidence.
- The registry is empty on evidence grounds, not scope grounds; identification capability is therefore
  `IMPLEMENTED` at best and no device is claimed identified. Physical-device identification is **deferred**
  (ADR-P3-014, standing directive) and marked `NOT RUN`, never skipped.

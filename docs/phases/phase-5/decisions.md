# Phase 5 — Architecture Decision Records

Phase: 5 — Device Fingerprinting & Identification.
Authority order: `docs/MASTER-CONTEXT.md`, then accepted Phase 0–4 ADRs, then this file.

**What this phase inherits, stated before any decision.** Phase 1 did not leave the identity model
unbuilt — it built `DeviceFingerprint`, `ManufacturerDataEntry`, `DeviceIdentity`,
`ProtocolIdentification` and `VendorExtension`, and then deliberately gave them **no producer**:
grep over every `src/main` tree finds `DeviceFingerprint(` constructed nowhere except its own
`empty()`. Phase 3 then refused the two signals that would have filled it, on evidence —
class-of-device is documented as a hint that "does not reliably describe which profiles or services
are actually supported", and service discovery needs a transport, which is Phase 6. And the user
settled the remaining question at the start of this phase: **no scanning, no phone work** — so
advertisement data and service UUIDs have no producer here either. Phase 5 therefore builds the
engine that consumes identity evidence, the schema that holds the rules, and the one evidence source
Android does provide without asking the phone to do anything; it does not, and cannot, ship a
population of identified devices.

**`ADR-P0-018` is settled by this phase, with the user's explicit deferral.** It has been
`proposed` since Phase 0 — "research ladder ordering: enumeration precedes identification" — and the
code has behaved as though it were accepted for four phases: `ManufacturerDataEntry.kt:12` cites it
as a decision, and `DeviceFingerprint.kt:13` cites it as the reason a fingerprint is not an answer.
Asked, the user had no preference and directed that the phone work be skipped entirely; the ordering
the code already assumes is therefore the one recorded. The audit found the cost of leaving it open was
no longer theoretical: `docs/decisions/README.md:129` rule 3 forbids implementation relying on a
`proposed` ADR, while Phase 1's code has relied on this one since `ManufacturerDataEntry.kt:12` and
`DeviceFingerprint.kt:13` cited it as a decision.

---

### ADR-P5-001 — Phase 5 consumes Phase 1's fingerprint model; no parallel identity type is created, and no 13th area
**Status.** accepted
**Context.** Prompt §4/§7 sketch `DeviceFingerprint`, `IdentitySignal`, `ManufacturerIdentity`,
`ModelIdentity`, `IdentificationConfidence`, `IdentityEvidence`, `MatchResult` and
`UnknownDeviceIdentity` as if none existed. Five of them do: `core/device/DeviceFingerprint.kt` is a
173-line record with a deterministic, address-free `identityKey()` and an explicit
`omnibuds-fingerprint/v1` version prefix; `ManufacturerDataEntry` is opaque observed evidence;
`DeviceIdentity` is descriptive identity that refuses to hold an address;
`ProtocolIdentification` already models "how was this matched, and may it be trusted" with a
`FALLBACK_UNKNOWN` that cannot carry confidence. Prompt §2 forbids recreating an existing
implementation, and a second fingerprint type beside this one would mean two answers to "what did we
see?" — the Phase 1 §24 failure in the one place it would do most damage.
**Decision.** Everything Phase 5 adds lives in `com.omnibuds.core.device` (layer 2) as new files, not
a new area and not a sub-package: `IdentitySignal.kt`, `IdentityNormalizer.kt`,
`ManufacturerIdentity.kt`, `IdentificationConfidence.kt`, `IdentificationRule.kt`,
`DeviceIdentityRegistry.kt`, `IdentificationResult.kt`, `IdentityEngine.kt`. `DeviceFingerprint`,
`DeviceIdentity` and `ManufacturerDataEntry` are reused unchanged and become **produced for the first
time** by this phase. `ProtocolIdentification` is not touched: protocol resolution is Phase 7, and the
only link Phase 5 leaves is the candidate list `DeviceFingerprint` already carries. ADR-P3-003's test
applies — areas are earned by different dependency shapes, and this is the same shape (`device` →
`platform`, `common`, `state`) as the files already in it.
**Alternatives considered.** A `core/identity` area — rejected: identical dependency shape, and a 13th
area would make the layer map a list of phases rather than of strata. A richer Phase 5 fingerprint that
supersedes Phase 1's — rejected: two fingerprints means every future reader has to know which one the
session carries. Having the session engine build fingerprints itself — rejected: `session` is layer 3
and may import `device`, not the reverse; identity is the thing sessions consume.
**Consequences.** Phase 1's careful `identityKey()` rules — sorted tokens, escaped separators, `':'`
folded out so an address cannot be smuggled through a free-text field, `NOTHING_KNOWN_KEY` for an
empty pass — become load-bearing instead of decorative, and are tested from the producer side for the
first time. The four prompt-§7 fields with no producer in a no-scan phase (`manufacturerData`,
`serviceUuids`, `characteristicUuids`, `firmware`) stay exactly as Phase 1 documented them: empty or
null, meaning *not observed*, which `isEntirelyUnobserved` already distinguishes from *absent*.

### ADR-P5-002 — The ladder runs enumerate → identify; a fingerprint is evidence and an identification is a conclusion, in different types
**Status.** accepted — settles `ADR-P0-018`, which had been `proposed` since Phase 0; the user was asked, expressed no preference, and directed that phone work stay skipped
**Context.** `ADR-P0-018` asks whether enumeration precedes identification or the execution prompt's
original order (`identify → discover`) does. Four phases have assumed the first: an enumeration read
happens, its evidence is recorded, and a separate step decides what the device *is*. The alternative
would have Phase 5 reach for discovery to obtain identity — which is the scanning the user has just
declined, and would also mean identification precedes observation with nothing to identify from.
**Decision.** Accepted as `enumerate → identify`, and it is enforced structurally rather than left as
a slogan: `DeviceFingerprint` records what was seen and has no field for a conclusion;
`IdentificationResult` records the conclusion and cites the fingerprint sections that justify it; and
no function in `IdentityEngine` returns a manufacturer or model without naming the rules that
produced it. Where the two types meet, the direction is one-way — a result can be traced back to
evidence, and evidence cannot be rewritten to match a result.
**Alternatives considered.** Keep it `proposed` — rejected: a fifth phase would then be built on an
unratified authority while citing it as authority, which is how Phase 2's documentation debt started.
Retire it as moot — rejected: the code has already made the choice, and an ADR that records a decision
made five times in four phases is cheaper than a decision rediscovered in Phase 20.
**Consequences.** The master's status banner and `docs/decisions/README.md` move it from `proposed` to
`accepted`, with the date and the fact that the user deferred it to engineering. The ladder's next
consumer is Phase 20 (research/comparison), which now inherits a settled ordering rather than an
open item, and the remaining unverified rung of that ladder — real hardware — is unchanged and still
deferred.

### ADR-P5-003 — Signals are typed by quality, not by a boolean: observed, derived, inferred, unknown, unavailable, invalid
**Status.** accepted — extends ADR-P0-016's three-tier unknown to the identity layer
**Context.** Prompt §6 requires each signal to distinguish six states, and the trap it is guarding
against is specific: an absent signal read as a negative is how a device ends up reported as *not
having* something nobody looked for. Phase 3 met the same trap on the observation axis and solved it
with three separate axes rather than one nullable aggregate; identity needs the same discipline
across six qualities.
**Decision.** `IdentitySignal` is a value carrying `kind`, `quality: SignalQuality`, the raw text (or
null), a `source`, and an `observedAtEpochMillis` that is never fabricated. The six qualities are
distinct and none is a synonym for another: `OBSERVED` came from the platform; `DERIVED` was computed
from an observed signal by a named, versioned rule (a normalised name is the only one Phase 5 has);
`INFERRED` is a candidate that evidence supports but does not establish (transport candidates from
device type — see ADR-P5-006); `UNKNOWN` is "no reading"; `UNAVAILABLE` is "this platform cannot
answer this question here" (advertisement data, service UUIDs — a *statement about the phase*, not
about the device); `INVALID` is "a value arrived and was rejected by validation", which keeps
malformed vendor text from silently becoming an absent signal (prompt §15's crash-resistance
requirement). `quality` is carried on every signal and the matcher's rules name the qualities they
accept, so a rule cannot quietly treat a `DERIVED` normalisation as an `OBSERVED` fact.
**Alternatives considered.** A nullable value plus a boolean `trusted` — rejected: two of the six
states collapse into "null and not trusted" and the distinction is exactly what later phases need.
Six signal subtypes — rejected: the payload shape is identical and the differences are qualities of
the same measurement. Reusing Phase 3's `DeviceAvailability` — rejected: it describes a device's
reportability, not a signal's epistemic status, and conflating them would make a scanner-less field
look like a hardware absence.
**Consequences.** `UNAVAILABLE` is the honest home for everything the no-scan directive removes, and
the register can then say precisely which identification power the phase gave up rather than leaving a
reader to infer it from silence. `INVALID` gives prompt §15's "malformed metadata must not crash the
matcher" somewhere to go other than a swallowed exception.

### ADR-P5-004 — `VERIFIED` confidence exists in the type and is unreachable from Phase 5's code
**Status.** accepted — same shape as ADR-P4-003's refusal of `CONTROL_SESSION`
**Context.** Prompt §9 lists `VERIFIED · HIGH · MODERATE · LOW · UNKNOWN`. `VerificationLevel` in
`core/state` already ranks the ladder `INFERRED → IMPLEMENTED → LAB_TESTED → HARDWARE_VERIFIED →
PERSISTENCE_VERIFIED`, and the project's rule is that a JVM-only test caps at `IMPLEMENTED`. A
`VERIFIED` identification would therefore claim something no evidence in this repository supports,
and prompt §17 forbids claiming a device is supported because its model was identified.
**Decision.** All five confidence categories are declared, each with a written evidence requirement,
and `IdentificationConfidence.VERIFIED` has **no construction path**: reaching it requires
`HARDWARE_VERIFIED` evidence about a specific device, which arrives with the deferred device session.
The matcher's own ceiling is `HIGH`, and `HIGH` requires two independent observed signals of
different kinds agreeing (ADR-P5-005's independence rule) — a name plus a class is not two
independent signals about a manufacturer, because both come from one display string in the common
case.
**Alternatives considered.** Omit `VERIFIED` until it is needed — rejected: prompt §9 asks for the
category set, and an enum with a member a later phase will legitimately reach is better than a
missing member a later phase will invent. Numeric scores — refused by prompt §9 outright, and by
anything with a ladder in it: a number invites averaging where a category forbids it.
**Consequences.** Confidence cannot inflate silently through a data-class `copy`, because the rule
that caps it is a test on the matcher's reachable results, mirroring `reachableStates` in Phase 4.
The gap is named rather than hidden: on a phone, most identifications will be `MODERATE` at best,
because the strongest passive signal is a name the device chose itself.

### ADR-P5-005 — A name is evidence of nothing except a name: the normalization rules, and what they may never do
**Status.** accepted — honours prompt §3's "never claim model identity from a display name alone"
**Context.** The device-reported name is the only identification signal Phase 5 gets that carries
vendor meaning, and it is also the least trustworthy: it is user-editable on Android, chosen by the
device's owner, and per `docs/phases/phase-3/connection-observation-research.md` §5.2 it is a cache
read that a name-change broadcast can make stale. Prompt §9's first example is that a user-editable
name alone must not establish verified model identity; the FINAL PRINCIPLE is that a device name is
not proof of identity.
**Decision.** `IdentityNormalizer` is one object, versioned (`NORMALIZATION_VERSION = 1`, published
into every fingerprint-derived key), and deliberately near-useless: trim; collapse internal
whitespace runs to a single space; case-fold with Kotlin's locale-independent `lowercase()`; and
nothing else. It does not strip brand tokens, does not remove model-number suffixes, does not
transliterate or fold accents (a `java.text` dependency is forbidden in `:core` by
`coreMainSourcesDoNotImportJvmOnlyLibraries`, and locale-sensitive folding is precisely the "destroys
meaningful distinctions" failure prompt §7 names), does not drop emoji, and does not merge near-variants.
Matching against a rule's expected name is *exact against the normalised form* or it is nothing;
substring and fuzzy matching are absent from Phase 5 by decision, not by omission.
**Alternatives considered.** Strip trailing model digits, split camelCase, fold punctuation — rejected:
each one destroys a distinction a real product line carries (WF-1000XM3 versus WF-1000XM4 differ only
in what such a rule erases). Levenshtein or token-overlap scoring — rejected: it produces a number
with no calibrated meaning (prompt §9) and a fuzzy match cannot be audited to a reason. Fuzzy-match
then downgrade confidence — rejected: the confidence would be measuring the algorithm's guesswork
rather than the evidence's strength.
**Consequences.** Two devices that differ by one character are two identities, which is the correct
outcome and the visible cost. Every normalised signal carries `DERIVED` quality and the version, so
when the rules change the fingerprints that were keyed on them are stale *by construction* —
`identityKey()` embeds `omnibuds-fingerprint/v1` and Phase 5 adds the normalisation version to the
derived token, so a rule-set bump cannot be confused with the same evidence.

### ADR-P5-006 — The registry ships with no vendor rules, and a test asserts the emptiness
**Status.** accepted — the ADR-P1-013 precedent applied to identification
**Context.** Prompt §11 requires each rule to document a manufacturer, a candidate model, signal
requirements, match conditions, confidence, a rule version, an **evidence source**, known
limitations and a verification status — and then forbids inventing device signatures and forbids
adding models to grow the registry. `DeviceFingerprint` and `ManufacturerDataEntry` have had no
producer for four phases; the same discipline that kept `ProtocolRegistry` empty (ADR-P1-013: "ships
empty, with a test asserting emptiness") is available here, and it is the only option that satisfies
both halves: prompt §3 asks for an engine capable of matching, not for a populated database.
**Decision.** `DeviceIdentityRegistry` is a versioned value: canonical manufacturers (id, display
name, documented aliases) and rules over normalised signals, each carrying the ten documented fields,
with `ManufacturerEntry`/`IdentificationRule` as the schema. The **production instance is empty** —
`DeviceIdentityRegistry.empty()` — and `PhaseFiveRegistryTest` asserts that emptiness, asserts that no
`src/main` file declares a rule, and asserts that every rule in the test fixtures carries an
`EvidenceSource` that is not `ASSIGNED_BY_THIS_PROJECT`. Rules are data, not code: adding one is a
diff to a registry file with an evidence citation attached, never a branch in the matcher.
`DeviceIdentityRegistry.builtIn(vararg rules)` exists for tests and for the phase that lands the first
documented rule, and its parameter is what makes an empty production registry impossible to
accidentally populate.
**Alternatives considered.** Ship a handful of well-known brand name patterns — rejected: the values
would be recalled rather than cited, which is inventing signatures with extra steps, and Phase 5
would then hand Phase 7 a registry nobody can audit. Ship Bluetooth SIG company-identifier
mappings — rejected *for now*, on the same ground: a company id is a manufacturer identifier and its
list is published, but Phase 5 cannot observe a company id without scanning (ADR-P5-007), so the
mapping would be unused data with an unverifiable transcription. Seeding rules and marking them
`LOW` confidence — rejected: `LOW` is still a claim that the pattern is real.
**Consequences.** Today the engine answers `UNKNOWN_DEVICE` or `INSUFFICIENT_EVIDENCE` for every
device, and that is the honest result of a no-scan phase with a rule set nobody has documented yet —
recorded as `RISK-` (registry carries no evidence-backed rules), not smoothed. What Phase 5 does ship
is the thing that makes a rule addable safely: the schema with its mandatory evidence fields, the
precedence and independence rules, the ambiguity type, and a test that fails if someone adds a rule
without a source. The prompt's own line — "unknown identification is not an application failure" — is
what the empty registry demonstrates rather than what it lacks.

### ADR-P5-007 — No scan, no advertisement, no service discovery: the passive boundary is the user's decision and the seam keeps it reversible
**Status.** accepted — records the user's directive and its cost
**Context.** Asked at the start of the phase whether identification should stay with signals Android
already holds or declare `BLUETOOTH_SCAN` and capture advertisements, the user directed that the phone
side be skipped entirely: "we were just building the product and not features… no need to connect to
the phone and do anything". That settles the strongest evidence question in the phase: a company id —
the identifier a manufacturer record is read from — is observable only in advertisement data, so
without a scan there is no company id, and prompt §17 forbids the GATT lookup that would otherwise
cache service lists. What the phase keeps instead is whatever the phone already holds: the audit
enumerated it as "name, alias, address, device type, class, bond state and cached UUIDs … no air traffic" (`connection-observation-research.md:584-585`), all of it covered by the one declared permission.
**Decision.** Phase 5 reads only what a phone already knows about a device it has been paired or
connected with, which is more than it first appears: the reported name, the platform alias (API 30+,
held as user-chosen text because it silently falls back to the name), the device type, the
class-of-device integer, the bond state, the profiles Phase 3 reports, and the **cached**
service-UUID list — a local read the platform documents as *not* starting a discovery procedure, so
not the GATT lookup prompt §17 forbids. An empty cached list is `UNKNOWN`, never `UNAVAILABLE` and
never "the device exposes no services": research §5.1 says a cold cache is the normal case for an app
that does not scan. `MANUFACTURER_DATA` and `CHARACTERISTIC_UUID` are `UNAVAILABLE`; platform
metadata gets no signal kind at all, because `getMetadata(int)` is `@SystemApi` with
`BLUETOOTH_PRIVILEGED` and unreachable from this module — an absent kind being the honest shape for an
unreachable read. The collector sits
behind the same one-way seam Phase 3 used (an interface, a scripted implementation in tests, an
Android implementation of raw reads), so an advertisement source is an added producer, not an engine
change. `BLUETOOTH_SCAN` stays undeclared and `DEVICE_DISCOVERY_SCAN` keeps `authorizedInPhase = 5`
as *authorised and unexercised*, which ADR-P3-018 already anticipated.
**Alternatives considered.** Declare the permission but never call it — rejected: an undeclared
permission cannot be exercised, and a declared unused one is a fabricated capability with a runtime
prompt attached, which ADR-P2-011 refused in the other direction. Build the matcher to expect
advertisement data and leave it dead — rejected: the matcher's rules are shaped by which signals are
producible; `HIGH` confidence's "two independent observed signals" clause is written for what
passive evidence can actually be.
**Consequences.** Identification strength is capped by this decision and the cap is quantified in the
risk register rather than discovered later: with no company id, model identification rests on a
self-reported name plus a cached UUID list that is frequently empty, which prompt §3 forbids trusting
alone. The class
signal is also the one Phase 3 refused for a documented reason ("does not reliably describe which
profiles or services are actually supported"); Phase 5 uses it as a `LOW`/`MODERATE` corroborating
signal and never as a primary one, and says so in the rule precedence rather than in a comment.

### ADR-P5-008 — Ambiguity is a result with candidates in it, never an arbitrary winner
**Status.** accepted — prompt §8's prohibition made structural
**Context.** Prompt §8: "Do not assign a model when several candidates remain indistinguishable. Do
not convert an ambiguous result into an arbitrary winner." The failure mode is concrete — two rules
match, one says WF-1000XM4, one says WF-1000XM5, and the first `when` branch in a matcher decides
which one a future protocol selection acts on.
**Decision.** `IdentificationResult.Ambiguous` carries every surviving candidate with the rule ids
and signal references that kept it alive, and has **no** `manufacturer`/`model` field to read — the
type offers no way to ask "which one did you pick". Rule precedence is explicit and total: rules are
ordered by `(specificity, ruleVersion, ruleId)` and ties are *reported as ambiguity* rather than
broken by the ordering, because a tie broken by id sort is a decision made by a string comparison.
Conflicts reduce confidence or become ambiguity; `AMBIGUOUS` is not a lower confidence, it is a
different answer.
**Alternatives considered.** Highest-confidence-wins with ties broken by rule id — rejected as the
exact thing prompt §8 names. First-match-wins like `ProtocolRegistry`'s candidate order — rejected:
candidates there are lists to be *confirmed*, and Phase 1's own comment says so; a terminal answer
must not inherit a candidate list's tie-breaking. Averaging confidences — rejected: no numbers exist
to average (ADR-P5-004).
**Consequences.** Downstream phases get a result they cannot misuse: `isProtocolResolutionReady` is
false for `Ambiguous`, `Unknown`, `InsufficientEvidence` and `InvalidEvidence` by construction, and
only `Exact`/`Likely`/`ManufacturerOnly` can be read as evidence toward anything. The cost is that
`ManufacturerOnly` is often the correct terminal answer and a UI will want a model anyway; prompt §13
answers that — "unknown devices must be first-class results".

### ADR-P5-009 — Identification enriches a session; it never recreates one, never touches connection state, and stays a separate concept
**Status.** accepted — prompt §14's requirements, and Phase 1's known-issue-5 association honoured
**Context.** Prompt §14 asks for an identity-update policy that is safe: enrichment must not recreate
sessions, model identification must not change Bluetooth connection state, session identity and
product identity must stay separate concepts, and a changed display name must not spawn a new
physical device. Phase 4's engine owns session state and holds `DeviceSession` (identity,
classification) plus `DeviceState` (connection) with a construction-time invariant that their
identities agree.
**Decision.** `DeviceSessionEngine.enrichIdentity(sessionId, identity, fingerprint, result)` is the
only identity entry point, and its body is three existing operations: `DeviceSession.withEvidence`
(fill-only merge, replace-fingerprint), `DeviceState.withIdentity` (which deliberately does not move
`revision`), and a new `identification` field on `TrackedDeviceSession`. It cannot mint or end a
session, cannot change `connection`, and publishes `SESSION_IDENTITY_CHANGED` only when the known
field count actually grows. Product identity is carried as a **result beside** the session, never
merged into `DeviceIdentity`: `identification` is Phase 5's conclusion with its own rule and registry
versions, while `identity` remains descriptive evidence, so the two cannot be conflated by reading
one field. A name change that the merge cannot adopt (Phase 1's rule: known fields are never
restated) leaves the earlier name and records no conflict — and prompt §14's "conflicting identity
evidence must be represented" is met by a `signalConflicts` count on the result rather than by
silently preferring the newer string.
**Alternatives considered.** Reconciling identification inside the engine's snapshot fold — rejected:
that would make matching run on every projection round and let a matcher failure look like an
observation failure. Replacing the session when identity changes — rejected: prompt §14's first rule,
and it would destroy the grace window Phase 4 just built. Merging the model string into
`DeviceIdentity.model` — rejected: that is the phase-boundary blur the separation requirement exists
to prevent; `model` is for a reported value, not for a conclusion.
**Consequences.** Session identity (which device this session is about, by platform key) and product
identity (which model we believe it is) answer different questions and have different versions, and
a test pins the three things enrichment must never do: change `sessionId`, move `connection`, or
bump `revision`. The residue is recorded, not hidden: `DeviceSession.fingerprint` was always null for
four phases and is now populated from passive evidence only, so its `identityKey()` is sparse, and
`TrackedDeviceSession`'s identity invariant means enrichment must update both records in one step —
which is what the existing `withMergedIdentity` already guarantees.

### ADR-P5-010 — An address is never an identity signal, and identity data is never a store
**Status.** accepted — SEC-ID-001/003/004/006, ADR-P3-010, prompt §15
**Context.** Prompt §6 lists "Platform device identifier" and "Bluetooth address, where accessible"
as candidate signals, and §7 forbids a raw address as the sole fingerprint; §15 asks for minimal
retention and redacted diagnostics. Phase 3 already solved the hard half of this: `DeviceObservationKey`
holds the address privately, prints kind and length, and is the only join.
**Decision.** No identity signal kind carries an address, in the type or in the text: the address stays
inside `DeviceObservationKey`, which is *attribution* — "same device as that earlier report" — and
Phase 5 consumes it for nothing except correlating a result to a session. `IdentitySignal.rawValue`
is the vendor-supplied text or number and validation caps its length and rejects control characters
before a rule ever sees it (prompt §15's malformed-metadata requirement, and the reason `INVALID`
exists as a quality). Every printed identity — signal, rule, result, registry — is checked by a test
against an address-shaped pattern, the same form Phase 3 and Phase 4 used. Nothing is persisted:
`:device` cannot import `persistence` (layer 2 vs 3), the registry is an immutable value built by its
owner, and no identity result survives the process.
**Alternatives considered.** Hash the address into a stable device id — rejected: a hash is the
identifier for correlation purposes, and prompt §7's concern is not that the bytes look like a MAC.
Cache identification results keyed by address for speed — rejected: prompt §16's "avoid unbounded
caches" and §15's retention rule point the same way, and matching over an empty registry is not a
performance problem. Redact at the logging layer instead — rejected: ADR-P1-019's redaction duty
still has no owner before Phase 36, so the only available enforcement is the type.
**Consequences.** The privacy statement in `validation.md` is checkable rather than aspirational, and
the phase's data-minimisation claim has a mechanism behind it. Prompt §15's "no cloud" and "no
external service" requirements are satisfied by the same fact as Phase 4's: `:core` can reach nothing,
and this phase adds no network code to the platform module.

### ADR-P5-011 — Matching is synchronous pure logic in the caller's thread, with the boundary stated rather than hidden
**Status.** accepted — prompt §16, Phase 0 `specs.md` rules 5.1/5.2
**Context.** Prompt §16 wants deterministic matching, no main-thread blocking, thread-safe registry
access, no unbounded caches, cancellation support, and no machine-learning dependency for what is
explicitly a rule-matching problem.
**Decision.** `IdentityEngine.identify(signals)` is a pure function over immutable inputs and an
immutable registry: no coroutine, no clock read, no allocation growth, no cache. Because it never
suspends, it cannot block a caller on I/O it does not perform, and determinism is testable directly
rather than through a scheduler. Registry thread-safety is by immutability — the registry is a value
swapped wholesale, never mutated in place. The one place `suspend` appears is signal *collection*
(the seam that reads the platform), whose dispatcher is the caller's, exactly as Phase 3's source
takes one. Cancellation is therefore inherited, not implemented.
**Alternatives considered.** A `Flow<IdentificationResult>` of results — rejected: identification is
an answer to a question, not a stream of state, and rule 5.6 says state flows are not for commands or
answers. A cached memo keyed by `identityKey()` — rejected: prompt §16 forbids unbounded caches and
nothing measured asks for one; if a profile ever shows matching cost, the cache is an ADR with a
bounded eviction policy, not a `by lazy`. A scoring model to rank candidates — rejected by prompt §16
outright (no ML for deterministic matching) and by ADR-P5-004's refusal of uncatalogued numbers.
**Consequences.** The engine is testable with no dispatcher and no faked clock, which is what makes
the determinism claims in `validation.md` cheap to hold. It also means Phase 5's cost is bounded and
its failure modes are total functions returning `InvalidEvidence`/`InsufficientEvidence` rather than
exceptions — and the registry-empty phase means all that machinery currently answers "unknown", which
is stated as the phase's condition rather than presented as identification capability.

---

### ADR-P5-012 — The discovery-scan authorization tag moves off Phase 5, which opens no scanner
**Status.** accepted — prompt §17, ADR-P5-007, the standing "build the product before the phone" directive
**Context.** `BluetoothOperation.DEVICE_DISCOVERY_SCAN` carried `authorizedInPhase = 5`. Phase 3 set it
there (ADR-P3-018 moved it off 3) on the reasoning that Phase 5's advertisement data would be the first
thing needing a scan. Phase 5 then decided — in ADR-P5-007, and on the user's explicit instruction to
skip the phone — to build no scanner: `MANUFACTURER_DATA` is modelled as a kind whose quality is
`UNAVAILABLE` precisely because nothing queries it, and `IdentityEngine` reaches no `startDiscovery` or
adapter. The tag now asserted that a phase which cannot and does not scan had authorized one.
**Decision.** `DEVICE_DISCOVERY_SCAN.authorizedInPhase` is set to `6`, the first phase whose transport
work can actually reach a scanner. The change is guarded by `PhaseFiveScopeTest.nothingNewIsAuthorisedAtPhaseFive`,
which asserts the set of operations authorized at 5 equals the set authorized at 4 and that the scan is
false at 5 — the same "data must not contradict the phase it names" rule that moved it off 3.
**Alternatives considered.** Leave it at 5 and rely on scope tests to show the code never scans —
rejected: that is the exact state ADR-P3-018 called "worse than a gap." Pull it to a later phase than 6 —
rejected without evidence: Phase 6 owns transport selection and a scan is a transport-adjacent read, and
inventing a further gap would be guessing at a phase that has not been prompted.
**Consequences.** No executed phase authorizes a scan, which matches the tree: nothing in `src/main`
starts discovery. The tag is a claim about a future phase, not a runtime gate (Phase 0 forbids an
ambient current-phase value), so this correction is data hygiene enforced by a check rather than a
capability change — and it keeps the deferred physical-device list honest about the one signal Android
would otherwise let Phase 5 read.

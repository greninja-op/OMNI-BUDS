# Phase 4 — Architecture Decision Records

Phase: 4 — Device Session & Lifecycle Management.
Authority order: `docs/MASTER-CONTEXT.md`, then accepted Phase 0/1/2/3 ADRs, then this file.

**What this phase inherits, stated before any decision.** Phase 1 already authored the session
*vocabulary* — `core/session/DeviceState.kt`, `core/device/DeviceSession.kt`,
`core/state/ConnectionState.kt` with its transition table, `SessionClassification`,
`VerificationLevel` — and deliberately implemented no engine. Phase 3 already authored the
observation the engine consumes and refused to translate it (`ADR-P3-001`'s consequences:
"Translating these three axes into a session `ConnectionState` is Phase 4's work"). So Phase 4
creates no new state vocabulary and no new source area: it creates the one thing between the two,
and every decision below is partly a decision about what it is *not* allowed to invent.

**One conflict is recorded rather than resolved silently.** Phase 1's records assign the same
session obligations to three different phases: `phase-1/validation.md:138` defers
"restrict `DeviceState` mutation to one owner" to **Phase 24**, `:142` (known issue 5) assigns the
`DeviceSession`↔`DeviceState` association to **Phase 4**, and `domain-model-review.md:143,146,148`
(D-7, D-10, D-12) says **Phase 2** for the same cluster. `MASTER-CONTEXT.md:1649` names Phase 4
"Device Session & Lifecycle Management" and `:1709` names Phase 24 "Global Device State Engine", so
the two are different phases with different jobs. ADR-P4-001 draws the line: Phase 4 carries the
association and owns *session* state; the global single-mutation-owner confinement stays Phase 24's
and is not claimed here.

---

### ADR-P4-001 — The session engine lives in `core/session`, consumes the Phase 3 projection, and owns exactly one thing
**Status.** accepted
**Context.** Prompt §5 demands one authoritative source of active session state and forbids competing
active-device lists. Prompt §8 demands the engine consume Phase 3's observation and not create another
observer. `docs/phases/phase-0/specs.md:147` (rule 5.7) already says who owns what: "the session/state
engine owns device state … Other components read, never write." The layer map says where it lives —
`session` is layer 3 and may import `platform` (1), `device` (2), `state` (0) and `common` (0), and may
not import `persistence` (3, same layer) or `protocol` (4).
**Decision.** One new type, `DeviceSessionEngine`, in `com.omnibuds.core.session`. It consumes
`ConnectedDeviceSnapshot` values produced by Phase 3 and owns the map of live sessions. It creates no
observation, touches no `ConnectedDeviceSource`, imports no framework type, and registers no 13th area
under ADR-P3-003's rule that areas are earned by different dependency shapes rather than by different
nouns. Phase 1's types keep their roles unchanged: `DeviceState` remains the only holder of
`ConnectionState` (`DeviceState.kt:9-15`), `DeviceSession` remains identity-plus-classification with no
connection field, and `ConnectionStateTransitions` remains the sole arbiter of legal moves. What Phase
4 adds is the association Phase 1 known issue 5 asked for — a `TrackedDeviceSession` that carries both
by construction so a state value cannot be paired with the wrong session.
**Alternatives considered.** A new `core/device/session` sub-package or a 13th area — rejected under
ADR-P3-003, and it would have made `session`'s layer-3 position look accidental. The engine owning a
`ConnectedDeviceObserver` and pulling from it — rejected: that gives the session layer a handle on a
platform seam, so session state would depend on observation *control* rather than on its output, and
`refresh()` would arrive with it (ADR-P4-011 refuses that too). Reusing `DeviceState` as the store's
only record and dropping `DeviceSession` — rejected: `DeviceSession` is where classification lives, and
the two are deliberately different lifetimes.
**Consequences.** The projection is now consumed by exactly one owner, and the `platform` area acquires
its first real downstream user, which is the integration proof Phase 3's audit section 5 was built for.
`DeviceState`'s copy-bypass limitation (`DeviceState.kt:30-36`) is **not** closed by this phase: the
engine is the only writer by convention and by the fact that nothing else is handed the map, but a
`copy(connection = …)` elsewhere still compiles. That obligation stays with Phase 24, named here so a
later reader does not mistake this engine for the confinement work. One objection had to be answered
before `TrackedDeviceSession` could exist at all: `domain-model-review.md:148` (D-12) rejected "a
composite owning both" as "the singleton ADR-P1-009 forbids". Read against ADR-P1-009's actual text
(`phase-1/decisions.md:72` — "no mutable singleton is introduced … state is passed as values"), what
was refused is a *global mutable* composite, not a per-device value: `TrackedDeviceSession` is an
immutable value held inside the engine's own list, reached only through that one owner, and no ambient
registry can produce a second one. The distinction is recorded rather than assumed, because Phase 1's
own review row points at this phase as the place it would be decided, and a reader who meets D-12
without ADR-P1-009 would reasonably conclude the type had already been rejected.

### ADR-P4-002 — A session carries no second lifecycle axis
**Status.** accepted — extends Phase 1's rule that `DeviceSession` holds no connection state
**Context.** Prompt §6 sketches a `DeviceSession` with both `connectionState` and `lifecycleState`, and
prompt §7 sketches CREATED → OBSERVING → ACTIVE → … → ENDED. Phase 1 already put connection progress in
`ConnectionState`, gave it a transition table, and then *deleted* the duplicate field from
`DeviceSession` because two values disagreeing about one device is the failure its prompt section 24
forbids (`DeviceSession.kt:9-15`). A second "lifecycle" enum would re-open exactly that hole with a
plausible name: `ACTIVE` versus `CONNECTED`, `ENDED` versus `DISCONNECTED`, `TEMPORARILY_UNAVAILABLE`
in both.
**Decision.** The engine holds one `TrackedDeviceSession` per session, composed of Phase 1's types plus
the facts that are genuinely neither connection state nor identity:
`sessionId` (opaque, engine-minted), `identityBasis` (keyed or ambiguous), `timeline`
(`startedAt`/`lastObservedAt`/`disconnectedAt` epoch millis, each nullable and never zero-filled), and
`termination: SessionTermination?` (why a session ended, null while it has not). `SessionTermination`
is a fact about the *engine's decision*, not a device state, and it is deliberately not a
`ConnectionState`. No type in Phase 4 can express "the device is ACTIVE" without also having moved
`DeviceState.connection`, so the two cannot disagree.
**Alternatives considered.** Adopt prompt §7's names as a `SessionLifecycleState` enum — rejected, for
the reason in Context; it would have needed a documented mapping table proving it never contradicts
`ConnectionState`, which is a second source of truth wearing a reconciliation sheet. Put the timeline on
`DeviceState` — rejected: `DeviceState` is revision-carrying device truth, and "when the engine gave up
on this session" is engine bookkeeping.
**Consequences.** The state model has one axis with one owner. `TrackedDeviceSession` is what prompt
§6's illustrative record actually maps onto, and the divergence from that sketch is this file, not a
silently doubled field. Timestamp semantics are documented per field and read epoch millis from the
injected `TimeProvider` only (ADR-P1-012), so `:core` still compiles for a non-JVM target.

### ADR-P4-003 — Phase 4's reachable `ConnectionState` subset is five states plus ERROR, and the other four are pinned unreachable
**Status.** accepted
**Context.** `ConnectionState` has eleven members. Four of them describe work Phase 4 is forbidden to do:
`IDENTIFYING` is fingerprinting (Phase 5), `CAPABILITY_DISCOVERY` is the capability engine (Phase 8),
`READY` and `CONTROL_SESSION` are an open transport (Phase 6) — and prompt §6 says a session "must not
imply vendor support", "must not imply control readiness", "must not imply that hardware capabilities
have been discovered". A `StateFlow` of sessions is precisely the surface a future UI reads, so an
unreachable-but-constructible state is a claim waiting to be made.
**Decision.** The engine moves sessions only among `UNKNOWN`, `DISCOVERED`, `PAIRED`, `CONNECTED`,
`TEMPORARILY_UNAVAILABLE`, `DISCONNECTED` and `ERROR`. The mapping is ADR-P4-004's table, and every move
goes through `DeviceState.attemptConnection`, which refuses an illegal move as
`Failure(INVALID_STATE)` rather than performing it (`DeviceState.kt:96-116`). `SessionStatePurityTest` pins
the four excluded states as never emitted by any engine path, and the transition the table permits but
this phase declines to use — `CONNECTED → IDENTIFYING` and onward — is named in the test rather than
left to reading.
**Alternatives considered.** Mint a Phase-4-only state enum with five members — rejected: it is a second
vocabulary for one axis, and the day Phase 5 arrives there would be two `CONNECTED`s. Allow `READY` when
a device is observed connected — rejected outright: `READY` means "a control session could start", which
is a hardware claim this phase has no evidence for.
**Consequences.** The engine cannot overclaim through a field, which is the point of the honesty ladder
in `state/VerificationLevel.kt`. Phase 5/6/8 each inherit a machine check that says "this state was
already reachable and you did not produce it", so promoting a session into `IDENTIFYING` becomes a
decision with a diff rather than a rename.

### ADR-P4-004 — The observation-to-session mapping is one table, and absence only becomes a disconnect when the union answered
**Status.** accepted — consumes ADR-P3-001, ADR-P3-015 rule 4, ADR-P3-017, ADR-P0-016
**Context.** Phase 3 hands over three independent axes per device plus the census, and forbids reading
any one of them as another. The engine must still produce one `ConnectionState` per session. The trap is
`UNKNOWN`: `DeviceConnectionState.UNKNOWN` means "no report was obtained", and mapping it to
`DISCONNECTED` would invent a negative, which ADR-P0-016 prohibits and which prompt §9's "prevent stale
active status" does *not* license.
**Decision.** Per device, in this order:

| Projection evidence | Session state | Why not something stronger |
|---|---|---|
| `availability` observable and `link == CONNECTED` | `CONNECTED` | the one positive report of a live transport |
| `link == CONNECTING` | `DISCOVERED` | a link coming up is not a link; the platform's own promise that this state is ever *delivered* is U-4/U-9 |
| `link == DISCONNECTING` | `TEMPORARILY_UNAVAILABLE` | a live link going down has not been proven down |
| `link == DISCONNECTED` | `DISCONNECTED` | a positive report of no link |
| `link == UNKNOWN`, `availability` not observable | `TEMPORARILY_UNAVAILABLE` for a tracked device, no session created otherwise | the phone has evidence about itself and none about the device |
| `link == UNKNOWN`, `availability` observable | `UNKNOWN` | ADR-P0-016: absence of a reading is not a reading of absence |
| `bond == BONDED` and the projection did not report a live link | `PAIRED` | the bond axis is a positive report; the census says nothing about a link (ADR-P3-017) |
| absent from a round the projection called a **complete union** | `DISCONNECTED` | ADR-P3-015 rule 4 — absence is evidence only when every profile answered |
| absent from a round that refused or went unanswered | no change | ADR-P3-009: a refusal restates nothing |
| `observedProfiles` non-empty | recorded as evidence, never mapped | profiles are *which service*, not *what capability* (ADR-P3-002) |

Sessions are created only from projection records. A device that appears **only** in the paired census
gets no session, because creating one would be reading the bond list as a connection — the inference
prompt §6 forbids and ADR-P3-017 made structurally impossible.
**Alternatives considered.** Map `DISCONNECTING` to `DISCONNECTED` — rejected: it announces an end the
platform has not reported, and a UI would show a disconnect that never happened. Map `bond == BONDED` to
`PAIRED` for census-only devices and keep them as sessions — rejected: prompt §10's collection B is a
list of pairings, not a list of sessions, and Phase 4's store is the latter. Treat `availability ==
UNAVAILABLE` as `DISCONNECTED` — rejected: that is the drawer case, and the drawer is not a disconnect
this app witnessed.
**Consequences.** Every cell of the table is a named test in `DeviceSessionEngineTest`, so a later phase
editing one cell edits a decision. The two weakest rows (`CONNECTING`, `DISCONNECTING`) are the ones a
handset may never deliver at all — research U-4/U-9 — so the engine is written to be right if they never
occur and wrong in no way if they do. The table is also the first place where a `DeviceObservation`'s
`arrival` (SNAPSHOT or EVENT) changes what is *believed*: an event that disagrees with the last completed
census is applied as a state move but cannot re-classify a device's identity basis.

### ADR-P4-005 — `sessionId` is minted, opaque and never derived from the device; ambiguity is a state, never a merge
**Status.** accepted — honours SEC-ID-001/003/004, ADR-P3-010, prompt §8, §12
**Context.** Prompt §6's example gives `sessionId: String` and `deviceKey: String`. Phase 3's only
attributable key is a Bluetooth address wrapped in `DeviceObservationKey`, which redacts itself because
an address is "a rotating handle on a person's belongings" (`DeviceIdentity.kt:16-19`, SEC-ID-003), and
`DeviceIdentity` deliberately holds no address. A string `deviceKey` would put the address straight back
into the model as printable text, through the front door prompt §12 tells us to keep closed.
**Decision.** The engine mints `sessionId` values from a monotonic counter — `session-1`, `session-2` —
with no device content in them at all, and hands them out on creation; they are stable for the session's
lifetime (prompt §6's requirement) and stop meaning anything once the session is dropped. The join to a
device is held as the typed `DeviceObservationKey`, never as its text, so nothing in the session model
can print an address. Where the platform reported a device with **no** key, the session carries
`identityBasis = AMBIGUOUS`: it is published, it is counted, it is never merged with another ambiguous
session and never matched to a future round — the same rule Phase 3 already applies to unkeyed records
(ADR-P3-015 rule 6), now applied one layer up where the temptation to guess is stronger.
**Alternatives considered.** Derive `sessionId` from the address (prompt §6's literal reading) —
rejected: it puts a durable identifier of the user's belongings into a printable field of a state object
that a future UI will render into a log line. Derive it from the display name — rejected twice over:
prompt §12 forbids merging or keying on names, and names collide. Hash the address — rejected: a hash of
an identifier is still that identifier for correlation purposes, and no requirement needs it.
**Consequences.** `TrackedDeviceSession.toString()` can be printed in a test failure without leaking a
device, which is the property Phase 3 built its key type to have. Ambiguous sessions are visible in the
snapshot and expire like any other, so "we are not sure this is one device" is answerable instead of
being resolved by a guess. The one cost is honest and recorded: two rounds of the *same* unkeyed device
look like two ambiguous sessions, and prompt §12's "do not merge two devices because they share a name"
is satisfied by not merging rather than by being clever about matching.

### ADR-P4-006 — Reconnection resumes within a session and mints a new id after termination; the grace is one round, not a timer
**Status.** accepted — resolves prompt §9's named policy question
**Context.** Prompt §9 asks for a documented, testable answer to whether a reconnect creates a new
session or resumes one. Phase 1 already settled half of it in prose: `DeviceSession.sessionId` is
"stable for the lifetime of a session… not stable across reconnects" (`DeviceSession.kt:27-28`). The
other half is what "lifetime" means, and any time-based answer would need a clock and a scheduler —
prompt §14 forbids unbounded polling, and a coroutine delay in a state engine is a source of
non-determinism in tests.
**Decision.** Three rules. (1) A device that goes `TEMPORARILY_UNAVAILABLE` and comes back **resumes the
same session** — a blip is not an ending, the id is unchanged, and `timeline.disconnectedAt` is never
set for it. (2) A device that reaches `DISCONNECTED` — by positive report or by absence from a complete
union — keeps its session, with `disconnectedAt` recorded, for exactly **one further published round**;
that is the grace in which downstream consumers see the transition (prompt §9's "preserve a transition
long enough"). If the next round is a complete union that still omits it, the session is `ENDED` and
removed. If it reappears inside the grace, it resumes the same id and publishes `SESSION_RECONNECTED`.
(3) After removal, reappearance mints a **new** sessionId and publishes `SESSION_CREATED`, with no
lineage pointer retained. Termination reasons are an enum (`ABSENT_FROM_COMPLETE_UNION`,
`PROVEN_DISCONNECT_PAST_GRACE`, `OBSERVATION_STOPPED`, `ENGINE_CANCELLED`), so "why did this session
end" is answerable without inferring it from an absence.
**Alternatives considered.** One session id per device forever, reused across reconnects — rejected: it
is a permanent device record wearing a session name, which is prompt §10's forbidden history and
SEC-ID-006's "scanning is not saving". Time-window grace (resume within 30 s) — rejected: a timer makes
the engine's behaviour depend on wall-clock scheduling rather than on observations, and no requirement
needs it. Keep ended sessions in the list with a `ENDED` flag — rejected: `activeSessions` would then be
a filter over history, which is prompt §5's competing list in disguise.
**Consequences.** The policy is deterministic and drives entirely off published rounds, so a test can
prove all three rules without a clock. The cost is stated: a device that disconnects and reconnects
across the grace boundary gets a new session id, and anything that had keyed itself to the old one must
re-key — which is correct, because the alternative is a history. The removal on `ENGINE_CANCELLED` is
deliberate: SEC-ID-005's active-session row says "cleared with the session; no durable record", so a
stopped engine publishes an empty authoritative list *with a reason attached*, not a frozen room.

### ADR-P4-007 — A refused round never empties the session list, and the projection now carries the reason for its own refusal
**Status.** accepted — amends `ConnectedDeviceSnapshot` to remove an asymmetry ADR-P3-017 created
**Context.** Prompt §12 lists six distinct things the state must not conflate: observed-zero,
unavailable, permission-denied, bluetooth-disabled, failed, stopped. Phase 3's projection answers the
first two honestly — `restsOnCompletedRound` plus `isUnionComplete` — and it carries `pairedRoundRefusal`
as an `OmniBudsError?` for the bond half, but the *link* round's refusal is present only as a boolean
with no reason. An error type exists as a value at exactly one of two symmetric places, so a consumer
can say "why is the list empty" for pairings and not for connections.
**Decision.** Two moves. (1) `ConnectedDeviceSnapshot` gains `deviceRoundRefusal: OmniBudsError?`,
set on a refused round and cleared on a completed one, mirroring `pairedRoundRefusal` exactly;
`restsOnCompletedRound` keeps its meaning. (2) The session engine treats a refused round as **no new
information**: sessions keep their state, `activeSessions` is not recomputed as empty, and the published
snapshot gains `SessionObservationStatus.Refused(error)` whose category is Phase 3's
(`PERMISSION_DENIED`, `BLUETOOTH_DISABLED`, `ADAPTER_UNAVAILABLE`, `RESOURCE_UNAVAILABLE`,
`PLATFORM_EXCEPTION`) — Phase 4 mints no new category and adds none to the 23-member set (ADR-P3-006).
"Empty and healthy" is only ever published when the round completed and the union answered
(`isCensusComplete`), which is the same two-boolean discipline Phase 3 needed for the identical reason
(ADR-P3-015 rule 9).
**Alternatives considered.** Clear sessions on refusal — rejected: it is prompt §12's forbidden
representation, and it would make a revoked permission look like everybody going home. Wrap Phase 3's
error in a Phase 4 `SessionObservationError` — rejected: a second error type for one failure adds a
mapping to maintain and says nothing the category did not already say.
**Consequences.** The asymmetry Phase 3 left is closed by its own mirror, in the file that already
owned the other half. Any future consumer of `ConnectedDeviceSnapshot` gets the reason without
subscribing to the round flow, and the session engine's input is one type instead of two.

### ADR-P4-008 — Events are notifications with a bounded buffer, and `SESSION_ACTIVATED` is refused
**Status.** accepted — honours `docs/phases/phase-0/specs.md:146-147` rules 5.5/5.6
**Decision.** `DeviceSessionEvent` carries `SESSION_CREATED`, `SESSION_UPDATED`, `SESSION_DISCONNECTED`,
`SESSION_RECONNECTED`, `SESSION_ENDED`, `SESSION_IDENTITY_CHANGED` and `SESSION_OBSERVATION_FAILED` —
each one emitted only where a real move in `DeviceState.connection` or in the session's identity
happened, never as a restatement. Published through a `MutableSharedFlow` with `replay = 0`,
`extraBufferCapacity = 64`, `BufferOverflow.DROP_OLDEST`, all three declared in code with the reason
beside them, because rule 5.5 requires every flow to state its buffering and overflow rather than
inherit a default. The `StateFlow` is the authoritative value and events are edges: a slow collector
that misses an event reads the state and is not wrong (`specs.md:146` rule 5.6), and no event history is
retained anywhere (prompt §13's "no permanent event-history database"), so `replay = 0` is a decision
rather than a shortcut. Event payloads carry `sessionId`, the redacted key form and counts — never a
display name, which is user-chosen text, and never an address.
**Alternatives considered.** `SESSION_ACTIVATED` from the prompt §13 list — rejected: activation implies
a device became *usable*, which is exactly the claim ADR-P4-003 makes unreachable; `CONNECTED` after a
disconnect is already `SESSION_RECONNECTED`, and first arrival is already `SESSION_CREATED`. A
`Channel.UNLIMITED` event buffer — rejected: an unbounded queue behind a callback-fed producer is the
"bounded window wearing a domain name" failure ADR-P3-015 rule 8 refused. `replay = 1` so a late UI
collector sees the last event — rejected: it duplicates the StateFlow and creates two histories that can
disagree.
**Consequences.** Duplicate suppression is defined at the edge, not in the buffer: an update that changes
neither connection state nor identity publishes nothing, so a flapping profile cannot generate an event
storm. The DROP_OLDEST choice is observable — a collector that lags loses the oldest edge and can
reconcile from state, which is stated in the type's KDoc so nobody reads a missing event as a device
that did not change.

### ADR-P4-009 — One mutex, one revision, and a documented rule for a delayed observation
**Status.** accepted — reuses the Phase 2/3 single-owner pattern; answers prompt §14
**Context.** Prompt §14 requires one authoritative state owner, atomic reconciliation, no leaked
collectors, and behaviour defined for rapid `CONNECTED / DISCONNECTED / CONNECTED / DISCONNECTED`
sequences, and it forbids an old event overwriting newer state without a documented rule. Phase 2 and
Phase 3 already settled this shape: a `Mutex` slot, publication of the new value inside the lock, and
teardown in a `finally` that also runs on cancellation.
**Decision.** The engine holds a `Mutex` around the whole reconcile-and-publish step, so a snapshot is
never seen half-applied. Ordering across rounds is carried by `DeviceState.revision`, already monotonic
and already guarded by `applyIfNewer` (`DeviceState.kt:125-126`): a candidate that is not newer for that
session is discarded, not failed. The documented stale-input rule is a hierarchy of *authority*, not of
timestamps — a completed census outranks a single-device event even if the event carries a later
`observedAtEpochMillis`, because the census is a restatement of the whole union while an event is one
service reporting one moment; within one class, the newer reading wins. The engine starts nothing and
stops nothing on its own: `consume(Flow<ConnectedDeviceSnapshot>)` runs in the **caller's** scope, so
there is no `GlobalScope`, no owned `CoroutineScope` and no dispatcher to close (rule 5.1), and
cancellation of the caller's job is the only shutdown — on which every session is ended with
`ENGINE_CANCELLED` and the authoritative list becomes empty-with-a-reason.
**Alternatives considered.** An actor/channel-confined owner — rejected: it buys the same serialisation
at the cost of a mailbox to leak and a scope to own. Lock-free `update` loops — rejected: reconciliation
reads the previous sessions to decide the next ones, so retrying an `update` block means re-running
decision logic, and a state machine that runs twice is not deterministic. Timestamp-as-authority —
rejected, for the reason in the table above: a handset's event timestamps are unverified (U-4) and a
device's own clock reading is not evidence about the union.
**Consequences.** Rapid connect/disconnect sequences are provably consistent: each round is applied
wholly, the transitions are checked against `ConnectionStateTransitions`, and an illegal move is recorded
in the snapshot as a refused transition rather than applied — the same shape Phase 3 uses, so a test can
count refusals. Multi-threaded contention between `consume` and a future command path remains unproven
and is recorded as a risk rather than claimed solved (ADR-P4-012's honest limit).

### ADR-P4-010 — Temporary is the only classification the engine can produce, and nothing in Phase 4 reaches storage
**Status.** accepted — honours SEC-ID-005/006, ADR-P0-004, prompt §10, §17
**Context.** `persistence/DeviceRepository.kt:8-28` is the saved-device seam, and its own KDoc names the
failure: "an implementation that keeps a 'recently seen' table behind this interface has broken
ADR-P0-004 no matter what this interface's method names say." Prompt §10 forbids implementing the
saved-device feature here; prompt §17 forbids a persistent database. The layer map independently forbids
`session` (3) importing `persistence` (3).
**Decision.** The engine creates every session as `SessionClassification.TEMPORARY` and never calls
`DeviceSession.save()`. `save()` stays where Phase 1 put it — a method a *user action* invokes through
some future layer — and the engine has no path to it. Phase 4 imports nothing from `persistence`,
declares no store, and adds no implementation of `DeviceRepository` (the guard at
`DependencyDirectionTest.kt:237-251` already refuses production implementations of it). Sessions are
dropped when they end, so the store's lifetime is the engine's lifetime, which is SEC-ID-005's active
session row: "cleared with the session; no durable record".
**Alternatives considered.** Name the type `DeviceSessionRepository` as prompt §12 sketches — rejected,
and recorded separately as ADR-P4-011 below, because `Repository` already means the storage contract.
Keep ended sessions for a "recently disconnected" UI affordance — rejected: that is the transiently
observed lifecycle SEC-ID-005 says is "not retained at all", and prompt §9 forbids deleting a *future*
saved profile precisely because the two must not blur.
**Consequences.** The separation prompt §10 asks for is enforced by the dependency rules rather than by
discipline, which is the only kind of enforcement this project trusts. When the saved-device feature
arrives it arrives under `persistence` with its own contract and its own ADR (ADR-P3-004's consequence
said exactly this), and until then `isSaved` is false for every session this engine can build.

### ADR-P4-011 — `refresh()` stays uncalled in Phase 4, and the reason is the engine's input contract
**Status.** accepted — discharges RISK-058's ownership question without taking the ownership
**Context.** Phase 3's register assigned `refresh()`'s missing caller to "Phase 4 / Phase 49"
(`phase-3/risk-register.md:132`), and prompt §8 requires the session engine to consume the existing
observation rather than create another one. `refresh()` is a method on `ConnectedDeviceObserver`, so
calling it means holding the observer.
**Decision.** The engine consumes `Flow<ConnectedDeviceSnapshot>` and knows nothing about the observer
that produces it. Consequences drawn honestly: a permission granted while the engine is idle is picked up
when the observer next publishes — which is any device announcement — and if nothing announces anything,
the projection stays refused and visibly refused (`SessionObservationStatus.Refused`, ADR-P4-007), which
is the correct user-facing answer rather than a silent gap. A polling caller is what prompt §14 forbids
("no unbounded polling") and what Phase 2's permission work refused to build. RISK-058 is re-scored in
Phase 4's register with this decision as its new shape, not marked closed.
**Alternatives considered.** Inject the observer and call `refresh()` on a refused status — rejected: it
couples session state to observation control, and a refused-then-retry loop without a delay is a hot loop
while a delayed one is the polling the prompt forbids. A `recovery: suspend () -> Unit` seam — rejected:
a function-typed hole in the constructor that only ever contains `refresh()` is that method with extra
steps and no test boundary.
**Consequences.** The engine is testable against a fake flow of snapshots, which is what makes every
reconciliation rule in this phase a T1 check. What remains open is named: a handset with a stable radio
and no announcements will not re-read after a grant, and only a device session or a UI-driven pull can
settle whether that is a real user-visible problem.

### ADR-P4-012 — Phase 4's evidence ceiling is `IMPLEMENTED`, and a session is not a claim about a device
**Status.** accepted — T1 tier ceiling (`testing-governance.md:39`), TST-MOCK-001, ADR-P3-014
**Decision.** Every Phase 4 test is a JVM unit test against a scripted flow of `ConnectedDeviceSnapshot`
values, which is tier T1 and earns `IMPLEMENTED` and nothing above it: the state machine, the mapping
table, the reconnection policy and the concurrency rules are all pure logic over a faked input, so none
of them touches the radio. TST-MOCK-001 caps them further down the ladder — no mocked run reaches
`HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED` — and TST-REC-005 means the deferred device session is
recorded as `NOT RUN` rather than skipped. The word "session" is defined in the type's KDoc as
*observed application state*, never as proof of support: a session asserts that the platform reported a
device, and nothing about what that device can do (prompt's final principle; TST-PHB-003's ban on
unqualified "supports"/"works" claims).
**What this phase therefore cannot say.** That a real earbud produces a session; that a real disconnect
arrives as `DISCONNECTED` rather than as silence; that the grace of one round is the right window on a
phone whose announcements are slow (U-4); that `CONNECTING`/`DISCONNECTING` are ever delivered (U-9);
that a session survives process death (nothing is persisted, by ADR-P4-010, so the answer is no, and it
is a design fact rather than a gap). Each is in `risk-register.md` with the phase that can settle it.
**Alternatives considered.** Testing against Robolectric or an emulated adapter — rejected: it would
raise the apparent tier without raising the real one, and Phase 2 rejected the same idea for the same
reason. Claiming `LAB_TESTED` because the input is a *faked platform seam* — rejected: the tier table
puts T3 with that description, and T3 requires the faked platform-Bluetooth boundary behind the
transport abstraction, which Phase 4 does not touch at all; claiming it would be inflating a level by
re-describing the harness (TST-FAIL-001 row 3).
**Consequences.** The report reads: state machine implemented and tested, nothing run on hardware, and
`HARDWARE_VERIFIED` appearing nowhere. The one thing Phase 4 does add to the evidence base is that the
mapping from Phase 3's axes to `ConnectionState` now has a machine check on both sides — Phase 3 proved
the projection cannot lie about a device, Phase 4 proves the session layer cannot read it as one.

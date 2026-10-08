# Phase 10 — Audio Transport Engine: Decisions

## ADR-P10-001 — Observation-only engine; Android owns the media path

**Decision:** The engine observes transport state and never captures, decodes, re-encodes,
intercepts, routes, or modifies audio. There is no code path — present or stubbed — that
could move the media stream.

**Rationale:** OmniBuds is a hardware-control app, not an audio app. The moment the engine
can touch the stream, every state reading becomes suspect (did we observe it or cause it?).

**Consequences:** `PhaseTenScopeTest` machine-checks the vocabulary ban; the handle seam is
read-only by construction.

## ADR-P10-002 — Reuse `AudioTransportKind`, extend with HSP

**Decision:** Phase 10 uses the existing `AudioTransportKind` (adding `HSP`) instead of
creating a parallel `AudioTransportType`.

**Rationale:** Same concept ("which audio transport"), same package, same layer. A second
taxonomy would need perpetual synchronization and would eventually disagree.

## ADR-P10-003 — Reconciler is a pure function; conflicts become UNKNOWN + diagnostics

**Decision:** `AudioReconciler.reconcile` takes all inputs (including timestamps) as parameters;
no clock, no I/O, no randomness. Disagreements preserve UNKNOWN and record a diagnostic —
never a "more plausible" guess.

**Rationale:** Plausibility is how fabrications start. A pure function is fully testable with
scripted contradictions and cannot hide state.

## ADR-P10-004 — HSP reported UNKNOWN on Android

**Decision:** Android serves HSP through the HEADSET proxy with no HSP-specific state, so the
adapter reports HSP as `UNAVAILABLE→UNKNOWN` rather than copying the HFP reading.

**Rationale:** Copying HFP state into HSP would claim knowledge the platform never gave. The
domain supports HSP for platforms that do distinguish it.

## ADR-P10-005 — `LeAudioApi33` isolated; never loaded below API 33

**Decision:** All `BluetoothLeAudio` references live in one class, instantiated only when
`leAudioSupport(apiLevel) == SUPPORTED`, with an `init` check as backstop.

**Rationale:** Unconditional references risk `VerifyError` on API < 33 even when never called.
The absence of an instance *is* the "LE Audio unavailable" answer.

## ADR-P10-006 — `AutoCloseable` for the audio callback registration

**Decision:** `openAudioDeviceChanges` returns `AutoCloseable`, not `PlatformRegistration`.

**Rationale:** The registration is consumed in `callbackFlow.awaitClose`, whose cleanup block
cannot suspend. `unregisterAudioDeviceCallback` is synchronous; the type reflects that.
Idempotent close keeps racing teardown harmless.

## ADR-P10-007 — Three error categories; observation never invalidates a session

**Decision:** Added `AUDIO_OBSERVATION_FAILED` (SAFE_TO_RETRY — reads are side-effect-free),
`LE_AUDIO_UNAVAILABLE` (NEVER_RETRY), `AUDIO_STATE_CONFLICT` (NEVER_RETRY). All three have
`invalidatesSession = false`.

**Rationale:** Observation is read-only, so it cannot leave device state uncertain. The
`SAFE_TO_RETRY` widening is deliberate and documented (a failed read is not a failed write).

## ADR-P10-008 — Engine dispatcher injected

**Decision:** `AudioTransportEngine` takes a `CoroutineDispatcher` (default `Dispatchers.Default`).

**Rationale:** Deterministic tests need the engine's observation work on the test scheduler.
Hardcoded dispatchers are untestable by construction.

## ADR-P10-009 — Adapters live under `bluetooth.audio`

**Decision:** The Android audio adapters are in `com.omnibuds.android.bluetooth.audio`, not a
new top-level `com.omnibuds.android.audio` package.

**Rationale:** `DependencyDirectionTest` restricts platform code to the packages Phase 2 opened;
a new top-level package is a boundary change needing an ADR. The audio code *is* Bluetooth
code, so the existing boundary fits without ceremony.

## ADR-P10-010 — SCO devices not attributed to HFP or HSP

**Decision:** `AudioReconciler.deviceTypeToProfile(BLUETOOTH_SCO)` returns null.

**Rationale:** SCO is the shared HFP/HSP call path; attributing it to one profile would be the
guess that reconciliation rule 2 forbids.

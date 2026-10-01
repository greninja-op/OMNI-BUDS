# Phase 0 — Risk Register

Scale: probability and impact are `low | medium | high`; exposure = probability × impact, used to decide which risks get a dedicated phase. These are governance-level entries: no device, phone or protocol has been tested, so every probability here is an expectation, not a measurement.

Mitigation ids reference rules in `docs/phases/phase-0/*-governance.md`. Owner uses the role names from `sub-agent-orchestration.md`.

---

### RISK-001 — Android OEM Bluetooth differences
**Probability.** high · **Impact.** high · **Exposure.** severe
**Description.** Samsung/Xiaomi/OnePlus/Pixel stacks differ in codec exposure, background limits, permission behavior and reconnect semantics; a feature that works on one OEM is invisible on another.
**Mitigation.** Platform behavior is modelled as an input to capability state, never assumed; cross-phone test tier is mandatory before any support claim (`testing-governance.md`); no claim of "Android supports X" without naming the version/OEM tested.
**Detection.** Cross-phone matrix (Phase 32) plus diagnostics that record OEM, Android version and API level.
**Fallback.** Feature reported as unavailable on that platform, with the reason surfaced rather than hidden.
**Owner.** Bluetooth Agent / Testing Agent.

### RISK-002 — Vendor protocol changes
**Probability.** high · **Impact.** high · **Exposure.** severe
**Description.** Firmware updates and model revisions change command sets, response framing or persistence semantics without notice.
**Mitigation.** Protocol records bound commands to firmware ranges and record `ProtocolConfidence`; a response failing to parse is `ProtocolMismatch`, not a guessed default.
**Detection.** Per-model regression runs; parser rejection counters in diagnostics.
**Fallback.** Downgrade that model's capability states to `UNKNOWN` and disable the affected controls.
**Owner.** Protocol Research Agent.

### RISK-003 — Unknown proprietary protocols
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** Many vendors expose control channels with no public documentation.
**Mitigation.** Read-only default for unknown devices (ADR-P0-006); laboratory workflow gated by verification levels; protocol database records provenance and confidence.
**Detection.** Fingerprint with no protocol match; matched-but-INFERRED records.
**Fallback.** Report device as partially supported with an explicit capability table; battery and standard metadata still available.
**Owner.** Protocol Research Agent.

### RISK-004 — Authentication / encryption on control channels
**Probability.** medium · **Impact.** high · **Exposure.** high
**Description.** Some vendor channels require pairing-derived secrets, bonded transport, or app-specific handshake; unauthorized or improvised authentication attempts are both unsafe and a legal/ethical problem.
**Mitigation.** No attempt to derive or replay authentication material; bonding state is a documented write precondition (`SEC-WRITE-001`); pairing keys, link keys and identity-resolving material may not be stored at all (`SEC-ID-008`); research is limited to authorized devices and software (`SEC-RES-001`, `SEC-RES-005`); a handshake failure is reported as a structured error, never answered with an improvised sequence. **Gap recorded:** no dedicated rule set for authenticated/encrypted control-channel handling exists yet — see `validation.md` Issues; it must be authored by the phase that first encounters a protected channel.
**Detection.** Handshake failure with an unidentified channel; capability discovery succeeding while reads are rejected.
**Fallback.** Device remains in read-only standard-profile mode; the unsupported control path is documented as a known limitation.
**Owner.** Security Agent.

### RISK-005 — Codec control restrictions
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** Android generally does not let a third-party app force LDAC/aptX; availability is jointly decided by OS, stack, OEM, phone hardware and headset (master §17).
**Mitigation.** Six-state codec model that separates `ENABLED` from `NEGOTIATED` and `ACTIVE` (ADR-P0-015); no UI affordance presented as a control when the OS exposes read-only; no claim to force a codec.
**Detection.** Attempted configuration returning `CodecUnavailable`/read-only; negotiated-but-not-active transitions observed in the field.
**Fallback.** Display actual state with a short explanation and, where useful, a pointer to the system setting the user must change themselves.
**Owner.** Audio Agent.

### RISK-006 — LE Audio platform differences
**Probability.** high · **Impact.** high · **Exposure.** severe
**Description.** LE Audio availability, LC3 configuration and ISO transport behavior vary widely by Android version, OEM and headset, and coexist confusingly with classic A2DP.
**Mitigation.** LE Audio modelled as a distinct transport family, never as an A2DP codec (master §20); `audio-governance.md` keeps classic and LE Audio state separate in the data model.
**Detection.** Devices appearing with both classic and LE Audio identities; capability divergence between them.
**Fallback.** Treat the two as separate sessions with separate capability sets and say so in the UI.
**Owner.** Audio Agent.

### RISK-007 — Background execution restrictions
**Probability.** high · **Impact.** high · **Exposure.** severe
**Description.** Reconnect detection, battery refresh and Quick Settings behavior depend on Android background limits that change per release and per OEM aggressor policy.
**Mitigation.** Compliant patterns only (master §35, §32); platform forbiddance reported honestly rather than worked around; every background behavior tied to a documented permission/foreground condition.
**Detection.** Field reports of missed reconnects; OEM battery-optimization state in diagnostics.
**Fallback.** Manual re-entry path: user opens the app to reattach; the app never pretends the state is live.
**Owner.** Bluetooth Agent.

### RISK-008 — Firmware incompatibility
**Probability.** medium · **Impact.** high · **Exposure.** high
**Description.** A command valid on firmware N may be invalid or differently-meaning on N+1.
**Mitigation.** Firmware recorded in the fingerprint and bound to protocol records; `FirmwareMismatch` is a first-class error; OmniBuds does not perform firmware updates (excluded by policy, `SEC-FW`).
**Detection.** Response parsing failures correlated with firmware version fields.
**Fallback.** Capability states demoted to `UNKNOWN` for that firmware range.
**Owner.** Protocol Research Agent.

### RISK-009 — Persistence assumptions
**Probability.** medium · **Impact.** high · **Exposure.** high
**Description.** Treating an accepted write as durable produces settings that silently revert, which users experience as the app lying.
**Mitigation.** Eight-step verification ladder before `PERSISTENCE_VERIFIED` (ADR-P0-017); UI communicates volatility where established.
**Detection.** Post-reconnect read differing from last written state.
**Fallback.** Mark `SUPPORTED_VOLATILE`, re-apply on connect only with user consent, and label the behavior.
**Owner.** Core Agent / Testing Agent.

### RISK-010 — Protocol write safety
**Probability.** medium · **Impact.** high · **Exposure.** high
**Description.** A malformed or undocumented write can misconfigure, hard-reset, or brick user hardware.
**Mitigation.** Write preconditions (identity known, protocol known, command understood, recorded in protocol database, reversible or user-confirmed); absolute ban on blind writes, fuzzing and undocumented commands; no automatic retry of a timed-out write.
**Detection.** Any code path that can reach a write with an unverified command definition; write attempts on unidentified devices.
**Fallback.** Read-only mode enforced for the device family.
**Owner.** Security Agent / Protocol Research Agent.

### RISK-011 — Cross-device behavior differences
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** The same protocol produces different semantics across models (levels vs modes, case battery absent, gesture sets differing).
**Mitigation.** Capability negotiation per device rather than per brand; per-model support tables in the protocol database; master §55 report format is the only allowed support claim.
**Detection.** Cross-device test tier failures (Phase 31).
**Fallback.** Per-device capability state; no brand-level marketing claims in code or docs.
**Owner.** Testing Agent.

### RISK-012 — Cross-phone behavior differences
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** A capability that depends on phone-side stack support may be present on one phone and absent on another with the same headset.
**Mitigation.** Capability state includes the phone-side prerequisite; dependency engine records availability as a function of both endpoints (master §29).
**Detection.** Cross-phone matrix (Phase 32) results recorded per phone model.
**Fallback.** Feature reported unavailable *on this phone*, not unsupported by the device.
**Owner.** Testing Agent.

### RISK-013 — Apple / AirPods proprietary restrictions
**Probability.** high · **Impact.** high · **Exposure.** severe
**Description.** Apple's control surface is private and platform-restricted; attempting parity on Android risks fabricating capabilities or relying on undocumented channels.
**Mitigation.** Phase 42 research gated behind the same authorization and verification rules; expectation is honest partial support; no feature inferred from iOS screenshots or from a vendor app's UI.
**Detection.** A claimed Apple capability with evidence level below `HARDWARE_VERIFIED`.
**Fallback.** Standard-profile information only (battery, codec where exposed); Apple-specific features reported as not established.
**Owner.** Protocol Research Agent.

### RISK-014 — Future desktop Bluetooth API differences
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** Windows/macOS/Linux expose very different Bluetooth surfaces; a core that quietly absorbed Android assumptions will not port.
**Mitigation.** Android types confined to platform boundaries now (ADR-P0-008); shared-core rulebook in `architecture-governance.md`; portability checks are design constraints on Phases 1 and 46.
**Detection.** Phase 46 audit: any Android import reachable from shared code.
**Fallback.** Platform-specific capability degradation, declared per platform.
**Owner.** Architecture Agent.

### RISK-015 — Governance not yet enforced by tooling or version control
**Probability.** high · **Impact.** medium · **Exposure.** high
**Description.** This workspace is not a git repository, has no CI, no hooks and no enforced style gates, so every Phase 0 rule is currently held only by convention and review (see ADR-P0-021).
**Mitigation.** Rules written as checkable statements with IDs; `git-workflow.md` specifies the enforcement that Phase 1 will make real; review checklist in `sub-agent-orchestration.md` is executed by the orchestrator each phase.
**Detection.** Phase review; Phase 1 acceptance requires the repository, ignore rules and at least one automated check to exist.
**Fallback.** Continue manual review; do not claim automated enforcement that does not exist.
**Owner.** Orchestrator / Git-Workflow Agent.

---

## Register summary

| Risk | Exposure | Dedicated phase(s) that must address it |
|---|---|---|
| RISK-001, 007, 012 | severe/high | 2, 3, 28, 32 |
| RISK-002, 003, 004, 008, 010, 013 | severe/high | 5, 7, 20, 21, 22, 35, 42, 45 |
| RISK-005, 006, 011 | high | 10–14, 31, 33 |
| RISK-009 | high | 17, 18, 30 |
| RISK-014 | high | 1, 46, 47 |
| RISK-015 | high | 1 |

No risk in this register is closed. Several are, by their nature, permanent properties of the domain rather than problems to finish; they are managed through the verification ladder and the honesty rules, and each later phase must re-read this register before designing around it.

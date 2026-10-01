<!--
TEMPLATE: risk-register.md — copy to docs/phases/phase-<N>/risk-register.md (create it where the phase carries real risk; Phase 0 §6 lists it as required for phase 0)
DERIVES: docs/phases/phase-0/execution-prompt.md §14 (the fourteen seed risks + required fields), §23;
         docs/MASTER-CONTEXT.md §40 (risk-register.md "where useful"), §24, §25, §27, §29, §30, §32, §35, §53, §54.
COMPLETION RULES:
  R1 ID grammar RISK-<NNN>, project-wide and permanent; a phase adds RISK-<NNN> rows, it never renumbers or deletes one.
  R2 The fourteen seed descriptions below are fixed. Probability / Impact / Exposure / Mitigation / Detection / Fallback stay EMPTY until the phase that
      owns the risk assesses it — an unassessed risk is not "low", it is UNKNOWN.
  R3 Exposure = Probability x Impact on the scale in section 2; band drives whether the mitigation must exist before implementation starts.
  R4 Every mitigation that depends on unproven behaviour states the evidence level it needs; no risk may be closed on assumption (MASTER-CONTEXT §53).
-->

# Phase `<N>` — Risk Register

**Phase:** `<N>` — `<phase title>` · **Owner:** orchestrator · **Last assessed:** `<YYYY-MM-DD or UNKNOWN>`

## 1. Risk record (repeat per risk)

```text
RISK-<NNN>
Description:          <stable risk statement, unchanged once published>
Probability:          <1 very unlikely | 2 unlikely | 3 possible | 4 likely | 5 near certain>
Impact:               <1 cosmetic | 2 feature degraded | 3 feature unavailable | 4 false capability claim | 5 device/data harm>
Exposure:             <Probability x Impact — band: 1-6 monitor | 7-12 mitigate before implementation | 13-25 blocking>
Mitigation:           <the concrete rule, design choice, or test that reduces it — cite master § or ADR id>
Detection strategy:   <TEST-<SCOPE>-<NNN> id / diagnostic / log signal that proves the risk materialised; observable, not a feeling>
Fallback:             <what OmniBuds does when mitigation fails — normally downgrade to UNKNOWN / READ_ONLY / SUPPORTED_VOLATILE, never to a fake feature>
Owner:                <agent>
Status:               <open | mitigated | detected | closed | accepted>
```

## 2. Illustrative example (MASTER-CONTEXT §24 / §30 content — reference shape only; it does NOT fill the seed row below)

```text
RISK-009  (illustrative assessment, not the phase's own)
Description:          Persistence assumptions
Probability:          4 likely — the master explicitly forbids assuming persistence (§24)
Impact:               4 false capability claim — a volatile setting reported as device-persistent breaks hardware truth (§2)
Exposure:             16 — blocking band; mitigation required before implementation
Mitigation:           No PERSISTENCE_VERIFIED without READ → WRITE → READ BACK → VERIFY → DISCONNECT → RECONNECT → READ AGAIN → VERIFY (§24)
Detection strategy:   persistence-tier test that reconnects and re-reads the setting; mismatch raises VerificationFailed (§30)
Fallback:             classify SUPPORTED_VOLATILE, keep the control, label it as non-persistent; never claim device storage
Owner:                testing
Status:               open
```

## 3. Seed register (Phase 0 §14) — descriptions fixed, assessments to be filled by the owning phase

| RISK id | Description | Probability | Impact | Exposure | Mitigation | Detection strategy | Fallback | Owner | Status |
|---|---|---|---|---|---|---|---|---|---|
| RISK-001 | Android OEM Bluetooth differences | | | | | | | | open |
| RISK-002 | Vendor protocol changes | | | | | | | | open |
| RISK-003 | Unknown proprietary protocols | | | | | | | | open |
| RISK-004 | Authentication/encryption | | | | | | | | open |
| RISK-005 | Codec control restrictions | | | | | | | | open |
| RISK-006 | LE Audio platform differences | | | | | | | | open |
| RISK-007 | Background execution restrictions | | | | | | | | open |
| RISK-008 | Firmware incompatibility | | | | | | | | open |
| RISK-009 | Persistence assumptions | | | | | | | | open |
| RISK-010 | Protocol write safety | | | | | | | | open |
| RISK-011 | Cross-device behavior differences | | | | | | | | open |
| RISK-012 | Cross-phone behavior differences | | | | | | | | open |
| RISK-013 | Apple/AirPods proprietary restrictions | | | | | | | | open |
| RISK-014 | Future desktop Bluetooth API differences | | | | | | | | open |

## 4. Risks added by this phase

| RISK id | Description | Probability | Impact | Exposure | Mitigation | Detection strategy | Fallback | Owner | Status |
|---|---|---|---|---|---|---|---|---|---|
| `RISK-<NNN>` | `<new risk discovered this phase>` | `<1-5>` | `<1-5>` | `<P x I>` | `<rule/ADR id>` | `TEST-<SCOPE>-<NNN>` | `<UNKNOWN / READ_ONLY / …>` | `<agent>` | `open` |

**Blocking-band risks unmitigated:** `<n>` — must be `0` before `validation.md` says Ready: YES.

## 5. Phase 0 governance references

- `docs/phases/phase-0/execution-prompt.md` — §14 risk list and required fields (probability, impact, mitigation, detection strategy, fallback, owner), §6 directory structure, §21 review checklist, §23 failure conditions.
- `docs/MASTER-CONTEXT.md` — §40 per-phase doc set, §24 persistence verification, §25 unknown device safety, §27 protocol database and confidence, §29 feature dependencies, §30 error engine and write-retry rule, §32/§35 platform and background restrictions, §53 unknown remains unknown, §54 hardware verification standard.
- `docs/phases/phase-0/risk-register.md` — Phase 0 instance of this template.
- `docs/templates/validation-template.md`, `docs/templates/test-plan-template.md` — where detection strategies and residual risk are reported.

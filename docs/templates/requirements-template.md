<!--
TEMPLATE: requirements.md — copy to docs/phases/phase-<N>/requirements.md
DERIVES: docs/MASTER-CONTEXT.md §40 (per-phase doc set), §41 (requirement fields + REQ-BT-001..005 example), §53 (unknown stays unknown);
         docs/phases/phase-0/execution-prompt.md §7 (Title + Priority fields), §21, §22, §23 ("requirements are vague" = phase failure).
COMPLETION RULES:
  R1 ID grammar REQ-<SCOPE>-<NNN>. <SCOPE> = 2-6 uppercase letters fixed for the phase (P0, BT, CAP, AUDIO, ...). Never renumber or reuse a released ID.
  R2 No field left blank: write NONE, or UNKNOWN plus the reason. Never default to false, 0, or "unsupported" (MASTER-CONTEXT §53).
  R3 A requirement is accepted only when specific, testable, and traced to >=1 TASK and >=1 TEST in section 4.
  R4 Capability wording uses a CapabilityState value, never `supported = true`.
  R5 Hardware claims are capped at the VerificationLevel actually reached at phase close; unverified = UNKNOWN.
-->

# Phase `<N>` — Requirements

**Phase:** `<N>` — `<phase title>` · **Scope id:** `<SCOPE>` · **Owner agent:** `<agent>`
**Document status:** `<draft | reviewed | accepted>`
**Master sections implemented by this phase:** `<e.g. §8, §10, §24>`

## 1. Specificity gate (tick all before any record is written)

- [ ] One observable obligation per record, in `SHALL` / `SHALL NOT` form.
- [ ] Actor and object named (`The application SHALL …`, `The system SHALL NOT …`).
- [ ] Every acceptance criterion is pass/fail decidable without interpretation.
- [ ] Verification method is runnable at this phase's access level (documentation / unit / protocol / hardware-in-the-loop).
- [ ] No invented vendor facts, no assumed protocol bytes, no assumed UUID purpose.
- [ ] Vague wording is rejected. Bad example (MASTER-CONTEXT §41): `- Build Bluetooth support.`
- [ ] Good example (MASTER-CONTEXT §41): `REQ-BT-001 The application SHALL detect the Bluetooth adapter state.`

## 2. Requirement record (repeat per requirement)

```text
REQ-<SCOPE>-<NNN>
Title:                <short noun phrase>
Description:          <normative statement: "The <actor> SHALL <observable behaviour>." One obligation.>
Rationale:            <why; cite MASTER-CONTEXT §<n> or Phase 0 §<n>>
Dependencies:         <REQ ids | NONE | UNKNOWN — with reason>
Acceptance Criteria:  <numbered, observable AC-1 / AC-2 …>
Verification Method:  <documentation review | unit test | protocol test | integration test |
                       hardware-in-the-loop | persistence test> mapped to TEST-<SCOPE>-<NNN>
Priority:             <MUST | SHOULD | COULD>
Status:               <draft | specified | implemented | tested | verified-on-hardware | deferred>
```

## 3. Illustrative example (from MASTER-CONTEXT §41 — reference shape only, not a Phase `<N>` requirement)

```text
REQ-BT-005
Title:                Unknown values are not fabricated
Description:          The system SHALL represent unavailable information as unknown rather than fabricating values.
Rationale:            MASTER-CONTEXT §53; unsupported defaults would create fake capability reporting (§2).
Dependencies:         NONE
Acceptance Criteria:  AC-1 a field that was never read serialises as UNKNOWN, not false, 0, or "UNSUPPORTED".
Verification Method:  unit test -> TEST-BT-005 (level: IMPLEMENTED until hardware-in-the-loop proves otherwise)
Priority:             MUST
Status:               specified
```

Parent obligations of the same shape, all from MASTER-CONTEXT §41: `REQ-BT-001` adapter state detection, `REQ-BT-002` eligible already-connected devices, `REQ-BT-003` paired vs connected, `REQ-BT-004` no second user pairing operation unless the platform requires an association step.

## 4. Traceability table (every REQ needs a row; no empty cells)

| REQ id | Design section (`design.md`) | Spec section (`specs.md`) | TASK id(s) | TEST id(s) | Verification level | Status |
|---|---|---|---|---|---|---|
| `REQ-BT-005` | `<§2 Modules>` | `<§3 Enums>` | `TASK-BT-005` | `TEST-BT-005` | `IMPLEMENTED` | `<…>` |
| `REQ-<SCOPE>-<NNN>` | `<…>` | `<…>` | `<…>` | `<…>` | `UNKNOWN` | `<…>` |

## 5. Requirement summary

**Total:** `<n>` · **MUST:** `<n>` · **SHOULD:** `<n>` · **COULD:** `<n>` · **Deferred to later phase:** `<n>` (each deferral needs an ADR or a `validation.md` line) · **Untraced REQs:** must be `0`.

## 6. Phase 0 governance references

- `docs/MASTER-CONTEXT.md` — §40 phase structure, §41 requirement fields and good/bad contrast, §53 unknown remains unknown, §54 hardware verification standard, §55 no false feature parity.
- `docs/phases/phase-0/execution-prompt.md` — §7 requirement record shape, §22 acceptance criteria, §23 what counts as failure.
- `docs/phases/phase-0/requirements.md` — Phase 0 instance (`REQ-P0-001`…`REQ-P0-015`); keep scope id and field order consistent with it.
- `docs/templates/requirements-template.md` — this file; other phase templates: `design-`, `specs-`, `task-list-`, `test-plan-`, `validation-`, `decisions-`, `risk-register-template.md` in the same folder.

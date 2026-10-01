<!--
TEMPLATE: test-plan.md — copy to docs/phases/phase-<N>/test-plan.md
DERIVES: docs/MASTER-CONTEXT.md §45 (mandatory test fields), §36 (testing tiers), §54 (hardware verification standard), §55 (no false feature parity),
         §24 (persistence verification), §27 (protocol confidence);
         docs/phases/phase-0/execution-prompt.md §11 (documentation/governance tests), §16 (levels must never be conflated), §22, §23.
COMPLETION RULES:
  R1 ID grammar TEST-<SCOPE>-<NNN>; every test links to at least one REQ-<SCOPE>-<NNN>. Unlinked tests are filler.
  R2 All eight mandatory fields are present and concrete; "expected result" is observable, never "works correctly".
  R3 A mock / simulation / lab result can NEVER justify a hardware-verified claim; LAB_TESTED and HARDWARE_VERIFIED are distinct levels and are never conflated.
  R4 Absent evidence = UNKNOWN. A skipped or unrunnable test is recorded as not run, not as passed.
  R5 No test executes against physical hardware unless the phase prompt authorises hardware work.
-->

# Phase `<N>` — Test Plan

**Phase:** `<N>` — `<phase title>` · **Scope id:** `<SCOPE>` · **Owner agent:** `testing` / integration owner

## 1. Test record (repeat per test)

```text
TEST-<SCOPE>-<NNN>
Requirement linkage:  <REQ-<SCOPE>-<NNN>, ... ; TASK-<SCOPE>-<NNN>> — mandatory
Purpose:              <which obligation or failure mode this proves>
Tier:                 <unit | protocol | integration | hardware-in-the-loop | persistence | cross-device | cross-phone | regression>
Setup:                <preconditions, fake/real device required, adapter state, permissions, saved/active session>
Input:                <exact stimulus: values, command, event, sequence>
Expected result:      <observable outcome incl. states, e.g. CapabilityState = UNKNOWN rather than UNSUPPORTED>
Failure condition:    <the specific observation that means the requirement is not met>
Automation possibility: <automated | partially automated (state which step is manual) | manual only — reason>
Hardware requirement: <none | device type + firmware/protocol version needed | phone OEM variation needed>
Evidence level earned: <INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED>
Result:               <pass | fail | not run | blocked — with date and build/commit>
```

## 2. Illustrative example (built on the MASTER-CONTEXT §41 `REQ-BT-001` obligation; reference shape only)

```text
TEST-BT-001
Requirement linkage:  REQ-BT-001 / TASK-BT-002
Purpose:              Adapter state is observed and surfaced, not assumed
Tier:                 integration
Setup:                adapter enabled and disabled states driven by a controllable platform stub; no real radio
Input:                enable → disable → enable transitions, plus a transition while a session is active
Expected result:      each transition surfaces the matching adapter state; no capability is exposed while disabled
Failure condition:    a state is reported without an observed transition, or a control remains available while disabled
Automation possibility: partially automated — radio-driven transitions stay manual
Hardware requirement: none for this tier; hardware-in-the-loop tier still required before any hardware claim
Evidence level earned: IMPLEMENTED
Result:               not run
```

## 3. Tier coverage table (every tier needs a row or an explicit "not applicable — reason")

| Tier | Proves | Master reference | May this phase run it? | Tests | Highest level it can earn |
|---|---|---|---|---|---|
| unit | parsers, encoders, decoders, state machines, capability/codec/persistence logic | §36 | `<yes/no>` | `TEST-<SCOPE>-<NNN>` | `LAB_TESTED` |
| protocol | packet, command, response correctness, error handling | §36, §27 | `<yes/no>` | `<…>` | `LAB_TESTED` |
| integration | Bluetooth lifecycle, connection, reconnect, capability discovery | §36 | `<yes/no>` | `<…>` | `LAB_TESTED` |
| hardware-in-the-loop | real earbuds respond as specified | §36, §54 | `<yes/no>` | `<…>` | `HARDWARE_VERIFIED` |
| persistence | real disconnect/reconnect survival | §36, §24 | `<yes/no>` | `<…>` | `PERSISTENCE_VERIFIED` |
| cross-device | different models behave per spec | §36 | `<yes/no>` | `<…>` | `HARDWARE_VERIFIED` |
| cross-phone | different Android manufacturers | §36, §4 (never promise what the platform/OEM does not permit) | `<yes/no>` | `<…>` | `HARDWARE_VERIFIED` |
| regression | previously verified behaviour still holds | §49 step 14 | `<yes/no>` | `<…>` | `<level>` |

## 4. Evidence rules

- Feature parity is reported per device and per capability (verified / not verified / unsupported), never as "OmniBuds supports `<vendor>`" (MASTER-CONTEXT §55).
- `HARDWARE_VERIFIED` requires all eight conditions of MASTER-CONTEXT §54: known device identity, correct known protocol, understood command, command sent to real hardware, correct device response, state read back, result matches expectation, reconnect tested when persistence matters.
- Codec claims are capped by `CodecState`: `SUPPORTED`/`AVAILABLE`/`ENABLED`/`NEGOTIATED` evidence never licenses an `ACTIVE` claim (MASTER-CONTEXT §15, §18).
- Governance/documentation phases test documentation itself (Phase 0 §11 style): presence, consistency, and enforceability of mandatory rules.

## 5. Phase 0 governance references

- `docs/MASTER-CONTEXT.md` — §45 test fields, §36 testing philosophy, §54 hardware verification standard, §55 no false feature parity, §24 persistence verification, §15 codec states, §27 protocol confidence.
- `docs/phases/phase-0/execution-prompt.md` — §11 test-plan examples, §16 verification levels must never be conflated, §22 acceptance criteria, §23 failure conditions.
- `docs/phases/phase-0/test-plan.md` — Phase 0 instance; Phase 0 uses `TEST-P0-<NNN>` for the §11 test set.
- `docs/templates/requirements-template.md`, `docs/templates/validation-template.md` — REQ traceability and result reporting.

# Phase 7 — Task List

**Phase:** 7 · **Owner:** orchestrator
**Document status:** the tasks the phase ran, mapped to the code commit (`d1fe1a0`) and the test that proves
each. A task is `done` only where a `src/test` case passes at that commit.

| id | task | deliverable | requirement | status |
|---|---|---|---|---|
| P7-T-001 | Pre-execution audit of the existing protocol model + the §8/§11 vocabulary discrepancies | `architecture-audit.md` | REQ-001, REQ-005, REQ-014 | done |
| P7-T-002 | Settle ADR-P7-001 … ADR-P7-010 | `decisions.md` | all | done |
| P7-T-003 | Protocol lifecycle machine (third axis) | `ProtocolState.kt` | REQ-006…008 | done — `ProtocolStateTest` (6) |
| P7-T-004 | Runtime command/response contracts | `ProtocolCommand.kt` | REQ-013…015 | done — `ProtocolCommandTest` (7) |
| P7-T-005 | Protocol event model (bounded, requested≠confirmed) | `ProtocolEvent.kt` | REQ-016, REQ-017 | done — `ProtocolSessionTest` event case |
| P7-T-006 | Session + transport-adapter contracts (no Android, no fake impl) | `ProtocolSession.kt` | REQ-002, REQ-003, REQ-007, REQ-014 | done — `ProtocolSessionTest` (6), `PhaseSevenScopeTest` |
| P7-T-007 | Resolver + six-outcome resolution over the existing registry | `ProtocolResolver.kt` | REQ-009…012 | done — `ProtocolResolverTest` (8) |
| P7-T-008 | Reuse decision recorded: no new descriptor/registry/manager; VendorExtension reused | ADR-P7-001/009, no code | REQ-001, REQ-018 | done |
| P7-T-009 | Scope/architecture guard (no Android import, no prod session, resolver connects nothing, empty registry) | `PhaseSevenScopeTest.kt` | REQ-002, REQ-003, REQ-012, REQ-019 | done (4) |
| P7-T-010 | Full gate green (core JVM, platform unit, androidTest compile, lint) | `validation.md` | acceptance | done — 580 core + 102 android, lint clean |

## Explicitly not run (out of authorized scope)

- **No vendor protocol, opcode, GATT UUID, or packet layout** — the registry ships empty; a script lives only
  in test source (prompt §16/§17; ADR-P7-010).
- **No capability discovery** (that is Phase 8; `EarbudProtocol.discoverCapabilities` is left where Phase 1
  put it and is not pulled into the session).
- **No production session/adapter implementation**, no auto-connect, no automatic pairing, no command executed
  during resolution.
- **No UI, persistence, codec, ANC/EQ/gesture/battery/firmware.**
- **Physical-device protocol verification** — deferred to the end-of-project session (ADR-P3-014); `NOT RUN`,
  never `SKIPPED`; ceiling `IMPLEMENTED`.

# Phase 39 — Decisions

## D-39-01: no new vendor adapter
**Decision:** Report the Phase 19 blocker; implement no adapter.
**Rationale:** The stop condition forbids choosing an arbitrary
vendor. Inventing a protocol would violate the evidence rules.

## D-39-02: harden the framework
**Decision:** Add matching-semantics tests with scripted adapters.
**Rationale:** The framework is real and testable; hardening it is
justified by existing evidence.

## D-39-03: device items BLOCKED, not failed
**Decision:** Readiness matrix uses BLOCKED for hardware-dependent
items.
**Rationale:** A blocker is information, not a product failure.

## D-39-04: no production code changes
**Decision:** Phase 39 adds tests and docs only.
**Rationale:** The framework code is already correct; the gap is
evidence, not implementation.

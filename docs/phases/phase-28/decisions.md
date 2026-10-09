# Phase 28 — Decisions

## D-28-01: no foreground service
**Decision:** No service created; eligibility analysis documents why.
**Rationale:** No legitimate continuous user-visible task exists.

## D-28-02: process phase separate from device state
**Decision:** LifecyclePhase models the process; device readiness stays
in the session/state engines.
**Rationale:** A device may be ready while the app is background-restricted.

## D-28-03: invalidate by default
**Decision:** Sessions without live proof are invalidated on recovery.
**Rationale:** Persisted CONNECTED is not proof of connection.

## D-28-04: never replay interrupted operations
**Decision:** MarkInterrupted; explicit user action required to retry.
**Rationale:** Replaying a hardware write blindly is unsafe.

## D-28-05: bounded reconnect
**Decision:** Max 3 attempts, exponential backoff to 60s, eligible
triggers only.
**Rationale:** Battery and platform compliance.

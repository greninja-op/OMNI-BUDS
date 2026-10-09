# Phase 26 — Risk Register

## R-26-01: Stale action dispatched to wrong device/session
**Likelihood:** Low | **Impact:** High
**Mitigation:** Device+session revalidation at execution; stale → reject;
no redirection.

## R-26-02: PendingIntent forgery/redelivery
**Likelihood:** Low | **Impact:** High
**Mitigation:** exported=false, immutable flags, nonce window, full
revalidation. Intents treated as untrusted.

## R-26-03: Notification implies unverified capability
**Likelihood:** Medium | **Impact:** High
**Mitigation:** Actions only for verified+authorized+fresh states;
honest UNKNOWN states.

## R-26-04: Permission denial breaks core function
**Likelihood:** Low | **Impact:** High
**Mitigation:** Notifications degrade gracefully; architecture correct
without them. Tested.

## R-26-05: Lock-screen data leakage
**Likelihood:** Low | **Impact:** Medium
**Mitigation:** PRIVATE visibility default; generic labels; documented policy.

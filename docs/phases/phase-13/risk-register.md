# Phase 13 — Risk Register

## R-13-01: Derived negotiation may mislead consumers
**Likelihood:** Medium | **Impact:** Medium
Consumers might read NEGOTIATING as protocol truth.
**Mitigation:** Documented as derived; evidence carries INFERRED confidence;
no protocol vocabulary in event names.

## R-13-02: Active codec largely unobservable
**Likelihood:** High | **Impact:** Low
Most Android versions expose no active-codec API; states stay UNKNOWN.
**Mitigation:** UNKNOWN is the honest answer; the engine never fills the gap.

## R-13-03: Device correlation gaps on Android
**Likelihood:** Medium | **Impact:** Low
`ObservedAudioDevice` → `DeviceIdentity` correlation may fail.
**Mitigation:** Uncorrelated devices keep UNKNOWN codec fields; no guessing.

## R-13-04: Event buffer overflow under pathological input
**Likelihood:** Low | **Impact:** Low
64-slot buffer + 32-event timeline bound the blast radius.
**Mitigation:** `tryEmit` drops (never suspends) on overflow; timeline is the
authoritative record.

## R-13-05: Stale misinterpretation
**Likelihood:** Low | **Impact:** Medium
A consumer might treat STALE as current.
**Mitigation:** `isCurrent` is false unless freshness is CURRENT; documented.

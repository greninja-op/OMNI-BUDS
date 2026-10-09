# Phase 26 — Test Plan

## Unit (`NotificationStateMapperTest`, 13 tests)
Hidden/disconnected/unidentified/discovering/ready/pending/failure/
incompatible states; battery honesty; stale battery; action cap.

## Unit (`NotificationActionDispatcherTest`, 13 tests)
Valid dispatch + mode cycling; malformed; nonce replay; unknown device;
stale session; unsupported; stale state; mode-set mismatch; access
denied; duplicate in-flight; completion; executor refusal.

## Unit (`NotificationCoordinatorTest`, 8 tests)
No devices; ready; dedup; disconnect cancel; permission denial; multi-
device ambiguity; explicit selection; failure surfacing.

## Security (`NotificationSecurityTest`, 6 tests)
Stable IDs; namespaced action; no payload extras; label privacy;
channel config.

## Integration
Coordinator + repository; shared contracts with Quick Settings
(documented; same state model and command interfaces).

## Regression
Full suite: core + android, 0 failures. Manifest test earns the receiver.

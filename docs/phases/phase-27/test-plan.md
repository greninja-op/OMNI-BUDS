# Phase 27 — Test Plan

## Unit (`WidgetStateMapperTest`, 13 tests)
Unavailable/disconnected/unidentified/discovering/ready/pending/failure/
incompatible states; battery honesty; stale battery; action cap.

## Unit (`WidgetActionDispatcherTest`, 13 tests)
Valid dispatch + mode cycling; malformed; unknown widget; target
mismatch (no redirection); nonce replay; stale session; unsupported;
stale state; access denied; duplicate in-flight; completion; executor
refusal.

## Unit (`WidgetTargetResolverTest` in WidgetTests, 7 tests)
None/single/ambiguous/selected/bound/re-resolved/no-connected.

## Unit (`WidgetCoordinatorTest` in WidgetTests, 5 tests)
Unavailable; controls; dedup; unregister cleanup; instance isolation.

## Security (`WidgetSecurityTest` in WidgetTests, 3 tests)
Namespaced action; no payload extras; label length.

## Integration
Coordinator + repository; shared contracts with Quick Settings and
notifications (same state model and command interfaces).

## Regression
Full suite: core + android, 0 failures. Manifest test earns the provider.

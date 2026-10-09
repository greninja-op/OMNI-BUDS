# Phase 25 — Test Plan

## Unit (`TileStateMapperTest`, 11 tests)
Null/disconnected/unidentified/discovering/ready/pending/battery/
failure/incompatible states; missing battery ≠ 0%.

## Unit (`TileTargetResolverTest`, 7 tests)
No devices; single eligible; ambiguous; explicit selection; stale
selection; unknown selection; none eligible.

## Unit (`TileActionDispatcherTest`, 9 tests)
Valid toggle + mode cycling; unknown/stale/read-only/access-denied/
disconnected refused; duplicate clicks; mode-set mismatch.

## Security (`TileSecurityTest`, 3 tests)
No identifiers in labels; typed refusals; dependencies must be installed.

## Integration
Coordinator + repository: state events update the tile model (covered by
mapper tests over engine states).

## Regression
Full suite: core + android, 0 failures. Manifest test updated for the
single earned TileService.

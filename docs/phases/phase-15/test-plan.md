# Phase 15 — Test Plan

## Resolver
`ProcessingDomainResolverTest`: unknown evidence → UNKNOWN; verified
protocol → hardware; missing protocol → ambiguous; read-only denied;
domain mismatch denied; matching domain allowed.

## Scope
`ProcessingScopeTest`: no DSP vocabulary, no protocol literals, no hidden
APIs.

## Regression
Full suite: 987 core + 139 android, 0 failures.

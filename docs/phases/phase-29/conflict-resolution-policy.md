# Phase 29 — Conflict Resolution Policy

## Hard conflicts

- Source: HARDWARE_VERIFIED or PERSISTENCE_VERIFIED rules.
- Effect: configuration invalid; plan rejected; execution blocked.
- Never suppressed by feature priority.

## Advisory warnings

- Source: INFERRED/IMPLEMENTED/LAB_TESTED rules, or uncertain state.
- Effect: explainable warning; execution may proceed if the caller
  accepts; never silently upgraded.

## Unresolved

- Unknown/stale prerequisites or missing information.
- Plans rejected until resolved; no false validity claims.

## No automatic fixes

The engine never enables/disables another feature to resolve a
conflict. Resolution is the caller's decision, validated before
execution.

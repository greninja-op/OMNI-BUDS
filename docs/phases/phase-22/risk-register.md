# Phase 22 — Risk Register

## R-22-01: Knowledge mistaken for authorization
**Likelihood:** Medium | **Impact:** High
A documented operation may be read as permission to execute it.
**Mitigation:** No write APIs in the package; import never registers
adapters; docs state the boundary explicitly.

## R-22-02: Stale knowledge driving decisions
**Likelihood:** Medium | **Impact:** Medium
Outdated compatibility records could mislead resolution.
**Mitigation:** Lifecycle states, deprecation, change events, re-evaluation
hooks.

## R-22-03: Malicious packages
**Likelihood:** Low | **Impact:** Medium
Crafted imports could inject false knowledge.
**Mitigation:** Full validation before application; size/nesting limits;
dry-run; trusted-import boundary documented.

## R-22-04: Reference rot
**Likelihood:** Medium | **Impact:** Low
Deleted records leave dangling references.
**Mitigation:** Import-time reference validation; orphan detection tests.

## R-22-05: Scale
**Likelihood:** Low | **Impact:** Low
In-memory scans won't scale to thousands of records efficiently.
**Mitigation:** Bounded queries, pagination, deterministic ordering; SQL
backend is a future option with the same domain API.

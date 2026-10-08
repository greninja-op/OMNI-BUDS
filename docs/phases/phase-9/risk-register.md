# Phase 9 — Risk register

**Phase:** 9 — Hardware Feature Engine · **Scope id:** P9
**Document status:** accepted

| ID | Risk | Likelihood | Impact | Mitigation | Status |
|---|---|---|---|---|---|
| RISK-P9-001 | The `isActiveValue` convention misjudges a device's "engaged" value, causing a spurious conflict refusal or a missed one | Medium | Medium | The convention is documented in ADR-P9-006 as modelling, not hardware truth; conflicts only bite when both features are established on one device; verdicts carry explanations so a refusal is diagnosable | accepted |
| RISK-P9-002 | `WRITE_ONLY` hardware (control without read-back) cannot be represented until the capability model changes | Low | Medium | Reserved vocabulary exists (`FeatureAccess.WRITE_ONLY`); ADR-P9-005 records the gap and the required future ADR; such features are not offered as controls rather than misrepresented | accepted |
| RISK-P9-003 | A definition's declared relations are wrong for some device (e.g. a requires-edge that doesn't hold) | Medium | Medium | Relations are definition-level and overridable per protocol via `CapabilityDependency`; the standard catalogue declares only structural relations (ADR-P9-009); discovery-time edges take precedence at runtime | accepted |
| RISK-P9-004 | The mandatory read-back doubles command traffic on constrained links | Low | Low | Read-back is one bounded read per accepted write; no polling is introduced; a later phase may negotiate read-back suppression per protocol with an ADR | accepted |
| RISK-P9-005 | Per-feature serialization delays a second write behind a stuck first write | Low | Low | Every attempt is bounded by its timeout; cancellation restores; the stuck attempt cannot strand the feature in `Pending` | accepted |
| RISK-P9-006 | A vendor's value semantics don't fit the nine shapes | Low | Low | `CUSTOM` carries opaque payloads length-bounded; the vendor's adapter interprets them behind the port seam; the generic engine never parses them | accepted |
| RISK-P9-007 | Tests against the scripted port drift from real protocol behaviour | Medium | Medium | The port is the documented seam; the binding to `FeatureReadSupport`/`FeatureWriteSupport` in a later phase must come with contract tests proving the binding preserves the engine's guarantees (recorded as a Phase 10+ obligation) | open |
| RISK-P9-008 | Deferred hardware verification (user directive) means the engine's guarantees are proven against seams only | High | Medium | Stated plainly in validation.md; the ceiling stays `IMPLEMENTED`; RISK-044/RISK-045 carry the obligation that deferred claims stay visibly unverified | accepted |

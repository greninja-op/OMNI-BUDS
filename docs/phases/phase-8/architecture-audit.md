# Phase 8 — Architecture Audit

**Phase:** 8 — Capability Discovery Engine · **Owner:** orchestrator (Agent A role)
**Document status:** the pre-execution inspection that shaped ADR-P8-001 … 0nn, read out of the committed
tree at the Phase 8 start (`caea118`, Phase 7's head). Every claim below was verified by opening the file.

## 1. The headline, for the third phase running: the model exists; the engine does not

`core.capability` and `core.state` already hold almost all of prompt §4 Agent B's data model and the entire
capability *vocabulary*. Grep confirms **no** discovery engine, evidence model, dependency validator,
snapshot-with-metadata, or discovery lifecycle exists. So Phase 8 — like Phases 6 and 7 — *reuses the
vocabulary and adds the machinery*, and prompt §2's "reuse valid existing models / do not duplicate capability
registries" governs the whole phase.

| Prompt §4/§… asks for | Already exists | Phase 8 stance |
|---|---|---|
| CapabilityId (§6) | `common.FeatureId` (namespaced `qualifiedName`), `CoreFeature` catalogue | **Reuse**; do not mint `CapabilityId` |
| CapabilityCategory (§6) | `FeatureCategory` | **Reuse** |
| CapabilityDescriptor / vocabulary (§6) | `CapabilityDefinition` (feature/displayName/category/valueHint) — explicitly "a definition existing is never evidence of support" | **Reuse**; add no device facts to it |
| Support / Access / Verification (§7) | `CapabilityState` (the evidence ladder) + `FeatureCapability.readable/writable/verification` — cross-constrained in `init` | **Reuse**; do NOT fork four parallel enums (ADR-P7-003's lesson) |
| Verification levels (§7) | the master `VerificationLevel` ladder | **Reuse**; `FeatureCapability.verification` already uses it |
| CapabilitySet / snapshot (§13) | `DeviceCapabilities` — immutable per-device map, deterministic `mergedWith` on the evidence ladder, `unknown`/`unsupported`/`controllable`/`readable` views, `stateOf` returns UNKNOWN on a miss | **Wrap it**; the snapshot adds discovery metadata *around* it, not a second container |
| Vendor extensions (§12) | `VendorExtension`, `VendorFeatureMetadata` (namespaced, versioned) | **Reuse**; add unknown-extension preservation only if missing |
| Discovery operations (§9) | `EarbudProtocol.discoverCapabilities(): OperationOutcome<DeviceCapabilities>` (protocol/EarbudProtocol.kt:80) — the seam Phase 7 left unimplemented | **Adapt later, not here**; the engine drives reads through a handed-in L2 `CapabilityDiscoverySource`, and binding it to that L4 seam is L3/L4 wiring (ADR-P8-005), deferred because no protocol ships |
| Transport/version constraints (§3) | `FeatureCapability.transport/protocolId/requiresConnection`; `ProtocolDefinition.version` | **Reuse** as the constraint inputs |

The genuinely-new Phase 8 artefacts (grep-verified absent): `CapabilityEvidence` (provenance),
`CapabilityAvailability` (the one §7 dimension the existing ladder does *not* encode — see §3),
`CapabilityDependency` + `DependencyValidator` (cycles, missing prerequisites), `CapabilitySnapshot`
(discovery metadata + `DeviceCapabilities`), `DiscoveryState` + transitions, and the
`CapabilityDiscoveryEngine` coordinator.

## 2. Where the prompt's vocabulary collides with the tree, and how Phase 8 reconciles it

**§7's four dimensions are three-already-plus-one.** `FeatureCapability` already separates *support*
(`state`: `UNKNOWN`/`UNSUPPORTED`/positive rungs), *access* (`readable`/`writable`, and `init` refuses any
combination the state does not license), and *verification* (`verification: VerificationLevel`). Adding a
`CapabilitySupport`/`CapabilityAccess`/`CapabilityVerification` enum would create a **competing model** —
the exact thing §7 itself forbids ("do not create competing verification systems") and the Phase 7 lesson
(ADR-P7-003/004). **Resolution:** reuse them. The one dimension genuinely absent is *momentary availability*
—"supported but currently unavailable" is orthogonal to the evidence ladder (a `SUPPORTED_PERSISTENT`
feature can be unavailable right now). So Phase 8 adds **one** new enum, `CapabilityAvailability`, carried in
the snapshot alongside (not inside) `FeatureCapability`, and documents §7's support/access/verification as
*already modelled* — reconciliation-by-completion, not forking.

**§15's error categories map onto `OmniBudsErrorCategory`, which already has `RETRY_AFTER_REREAD`/
`NEVER_RETRY` classes.** `SESSION_UNAVAILABLE`→`CONNECTION_UNAVAILABLE`, `PROTOCOL_UNRESOLVED`→
`PROTOCOL_MISMATCH`, `DISCOVERY_UNSUPPORTED`→`UNSUPPORTED_OPERATION`, `DISCOVERY_TIMEOUT`→`TIMEOUT`,
`MALFORMED_CAPABILITY_RESPONSE`→`INVALID_STATE` (the engine's `MALFORMED_RESPONSE_CATEGORY`),
`TRANSPORT_UNAVAILABLE` and `PERMISSION_DENIED` verbatim, `CANCELLED`→`OperationOutcome.Cancelled`,
`UNKNOWN_ERROR`→`UNKNOWN_FAILURE`. `CAPABILITY_CONFLICT` is *not* a category — it is the snapshot field
`UnresolvedConflict`; `DEPENDENCY_INVALID` is *not* a category either — it is a `DependencyStatus`
(`CYCLE`/`MISSING_PREREQUISITE`). **Resolution (ADR-P8-004):** reuse, no new category (ADR-P3-006 / ADR-P6-006
precedent); a session-gone or unresolved-protocol pass surfaces as `DiscoveryState.FAILED` because every read
failed and nothing was established.

## 3. The one semantic gap worth stating: availability is not on the ladder

`CapabilityState` conflates support, access and durability but says nothing about *now*. §7 is right that
"supported but temporarily unavailable" is a distinct fact, and `requiresConnection` only hints at it. So
`CapabilityAvailability{UNKNOWN, AVAILABLE, UNAVAILABLE, TEMPORARILY_UNAVAILABLE}` is a real addition — but it
lives in the discovery result and snapshot, never written into `FeatureCapability` (prompt §2: "do not
silently rewrite earlier phase contracts"). A capability whose availability is unknown is *not* marked
unavailable, and an unavailable capability is *not* marked unsupported — the same unknown≠negative rule
`DeviceCapabilities.stateOf` enforces for support.

## 4. Layer arithmetic and dependency direction

`capability` is L2; `state`/`common` L0; `transport`/`platform` L1; `protocol` L4; `session` L3. Phase 8's
new types sit in `core.capability` (L2) and import only downward: `common`/`state` (L0) and, for the engine,
`protocol` (L4) — **wait: L2 cannot import L4.** The discovery engine must therefore *not* depend on
`EarbudProtocol`/`ProtocolSession` (L4) directly. **Resolution (ADR-P8):** the engine takes a *narrow L2
input* — a `CapabilityDiscoverySource` abstraction it is *handed*, or it consumes `DeviceCapabilities`
results — rather than importing the protocol layer upward; the wiring that binds a real protocol to the
engine lives at L3/L4 (session/DI), where an upward-consumed callback is legitimate. This is the same
constraint Phase 6 hit with the transport resolver (transport L1 could not import device L2, so the resolver
took transport-local inputs) and it must be honoured, not worked around by relaxing the layer map.

## 5. Reuse decisions locked before any code

1. Reuse `FeatureId`/`FeatureCategory`/`CapabilityDefinition`/`CoreFeature`; add no `CapabilityId`.
2. Reuse `CapabilityState`+`FeatureCapability` for support/access/verification; add only `CapabilityAvailability`.
3. Reuse `DeviceCapabilities` inside the snapshot; no second capability container/registry.
4. Reuse `VendorExtension`; no new vendor model.
5. Reuse `VerificationLevel` and `OmniBudsErrorCategory`; no forks.
6. New: evidence, dependency+validator, snapshot-metadata, discovery lifecycle, engine — and the engine
   stays at L2 consuming a handed-in read-only source, never importing the protocol layer upward.
7. `EarbudProtocol.discoverCapabilities` is the integration seam Phase 7 left for Phase 8; the empty
   registry means real discovery yields nothing, so the shipped engine answers `UNKNOWN`/empty, ceiling
   `IMPLEMENTED` (ADR-P1-013/5-006/7-010 precedent).

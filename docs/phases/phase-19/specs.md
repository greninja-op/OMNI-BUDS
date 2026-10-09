# Phase 19 — Specifications

## VendorAdapter
- adapterId: String (non-blank, e.g. `bose.bmap`)
- displayName: String (presentation only)
- match(fingerprint: DeviceFingerprint): MatchResult
- protocol: ProtocolDefinition
- supportedFirmware: Set<String>? (null = firmware-independent)

## MatchResult
- Matched(adapterId: String, evidence: String)
- NotMatched
- Ambiguous(reason: String) — writes must remain disabled.

## VendorRegistry
- VendorRegistry(adapters: Collection<VendorAdapter> = emptyList())
- register(adapter): VendorRegistry (immutable)
- adapterIds(): List<String>
- resolve(fingerprint): VendorAdapter? (suspend, Mutex-guarded)
- isAmbiguous(fingerprint): Boolean (suspend)

## Resolution semantics
| Matched count | Ambiguous? | Result |
|---|---|---|
| 1 | no | the adapter |
| 0 | no | null (unknown) |
| ≥1 | yes | null (safe) |
| >1 | no | null (conflicting) |

## NullVendorAdapter
- adapterId: `omnibuds.null`
- match(): always NotMatched
- protocol: empty commands/responses/mappings, INFERRED confidence
- transport: UNKNOWN

## Layer
`core.vendor` at layer 5. Imports: common (0), device (2), protocol (4),
state (0). No feature (5) imports.

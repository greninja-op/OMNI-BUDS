# Phase 44 — Design: Protocol Versioning & Compatibility Architecture

---

## 1. Component Architecture

The protocol versioning subsystem is structured within `com.omnibuds.core.protocol.version`:

```
com.omnibuds.core.protocol.version/
├── ProtocolVersion.kt           # Typed version domain & schemes (Semantic, Integer, Named, Unknown)
├── ProtocolSchemaVersion.kt     # Protocol metadata schema versioning
├── ProtocolIdentity.kt          # Canonical protocol identity model
├── VersionConstraint.kt         # Typed constraint model (Exact, Range, Set, AnyCompatible)
├── CompatibilityOutcome.kt      # Machine-readable compatibility outcomes
├── CompatibilityResolver.kt     # Deterministic compatibility evaluator
├── ProtocolVersionRegistry.kt   # Multi-revision implementation registry
├── ProtocolSchemaMigration.kt   # Transactional, evidence-preserving schema migrations
└── VersionedMessageCodec.kt     # Version-scoped message serialization and parsing
```

---

## 2. Layer Placement & Governance

- **Package:** `com.omnibuds.core.protocol.version`
- **Architecture Layer:** Layer 4 (`"protocol"`) in `:core`.
- **Downstream Dependents:** Layer 5 (`vendor`, `knowledge`), Layer 6 (`sdk`).
- **Dependencies Permitted:** Layer 0 (`common`, `state`), Layer 1 (`transport`, `security`), Layer 2 (`device`, `capability`).
- **Forbidden Imports:** Zero Android framework imports, zero Java I/O in main sources, no circular sideways dependencies.

---

## 3. Resolution Pipeline Flow

```
[Device Fingerprint + Observed Version / Firmware]
                    │
                    ▼
      [CompatibilityResolver]
     ┌──────────────┴──────────────┐
     ▼                             ▼
[Evaluates Constraints]    [Checks Policy & Transports]
     │                             │
     └──────────────┬──────────────┘
                    ▼
          [CompatibilityOutcome]
    ├── COMPATIBLE
    ├── COMPATIBLE_WITH_LIMITATIONS
    ├── INCOMPATIBLE
    ├── UNKNOWN_VERSION
    ├── AMBIGUOUS
    ├── INSUFFICIENT_EVIDENCE
    ├── UNSUPPORTED_SCHEMA
    └── BLOCKED_BY_POLICY
                    │
                    ▼
     [Safe Execution Pipeline: Denies writes if not COMPATIBLE]
```

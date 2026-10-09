# Phase 44 — Technical Specifications: Models & Interfaces

---

## 1. Type Models

### `ProtocolVersion`
```kotlin
sealed interface ProtocolVersion : Comparable<ProtocolVersion> {
    val rawValue: String

    data class Semantic(val major: Int, val minor: Int, val patch: Int) : ProtocolVersion {
        override val rawValue: String = "$major.$minor.$patch"
    }

    data class IntegerRevision(val revision: Int) : ProtocolVersion {
        override val rawValue: String = revision.toString()
    }

    data class VendorDefined(val identifier: String) : ProtocolVersion {
        override val rawValue: String = identifier
    }

    data object Unknown : ProtocolVersion {
        override val rawValue: String = "unknown"
    }
}
```

### `VersionConstraint`
```kotlin
sealed interface VersionConstraint {
    fun isSatisfiedBy(version: ProtocolVersion): Boolean

    data class Exact(val expected: ProtocolVersion) : VersionConstraint
    data class SemanticRange(val min: ProtocolVersion.Semantic, val maxInclusive: ProtocolVersion.Semantic) : VersionConstraint
    data class IntegerRange(val minRevision: Int, val maxRevision: Int) : VersionConstraint
    data class Enumerated(val allowed: Set<ProtocolVersion>) : VersionConstraint
    data object AnyKnown : VersionConstraint
    data object None : VersionConstraint
}
```

### `CompatibilityOutcome`
```kotlin
enum class CompatibilityStatus {
    COMPATIBLE,
    COMPATIBLE_WITH_LIMITATIONS,
    INCOMPATIBLE,
    UNKNOWN_VERSION,
    AMBIGUOUS,
    INSUFFICIENT_EVIDENCE,
    UNSUPPORTED_SCHEMA,
    BLOCKED_BY_POLICY,
}

data class CompatibilityResolution(
    val status: CompatibilityStatus,
    val selectedProtocol: ProtocolIdentity?,
    val candidateProtocols: List<ProtocolIdentity>,
    val machineReason: String,
    val humanDescription: String,
    val limitations: List<String> = emptyList(),
) {
    val canAuthorizeMutatingOperations: Boolean
        get() = status == CompatibilityStatus.COMPATIBLE
}
```

### `ProtocolIdentity`
```kotlin
data class ProtocolIdentity(
    val protocolId: String,
    val vendorNamespace: String?,
    val version: ProtocolVersion,
    val schemaVersion: ProtocolSchemaVersion,
    val transport: TransportKind,
    val supportedModels: Set<String> = emptySet(),
    val firmwareConstraints: Set<String>? = null,
    val confidence: VerificationLevel,
)
```

package com.omnibuds.core.extension

import com.omnibuds.core.state.VerificationLevel

/**
 * Trust levels for extension definitions.
 *
 * Phase 23 (OB-P23-REQ-020): descriptive metadata is not an executable
 * implementation. Each level is a separate, explicit promotion.
 */
enum class ExtensionTrustLevel {
    /** Descriptive metadata only; nothing executable. */
    DESCRIPTIVE,

    /** Registered definition; structural validation passed. */
    REGISTERED,

    /** An executable implementation is available. */
    IMPLEMENTATION_AVAILABLE,

    /** The implementation is compatible with the target device. */
    COMPATIBLE,

    /** Approved for a specific operation on a specific device. */
    OPERATION_APPROVED,

    /** The implementation has appropriate verification evidence. */
    VERIFIED,
}

/**
 * Extension lifecycle states.
 */
enum class ExtensionLifecycle {
    DRAFT,
    ACTIVE,
    DEPRECATED,
    DISABLED,
}

/**
 * A vendor extension descriptor.
 *
 * Phase 23 (OB-P23-REQ-003): stable identity, compatibility surface,
 * verification status, limitations. Never uses a display name as the
 * identifier — stable IDs from Phase 22 are referenced where available.
 */
data class VendorExtensionDescriptor(
    val id: VendorExtensionId,
    /** Manufacturer stable ID (Phase 22 knowledge ID). */
    val manufacturerId: String,
    /** Protocol family reference, e.g. "acme-rfcomm". */
    val protocolFamily: String,
    val version: String,
    /** Compatible device-model stable IDs. Empty = none; never "all". */
    val compatibleModelIds: Set<String> = emptySet(),
    /** Hardware-revision constraints, e.g. "rev B+". Empty = unknown. */
    val hardwareRevisions: Set<String> = emptySet(),
    /** Firmware compatibility rules. Empty = unknown. */
    val firmwareRules: List<String> = emptyList(),
    /** Compatible protocol stable IDs and versions. */
    val compatibleProtocols: Set<String> = emptySet(),
    /** Feature namespaces this extension provides. */
    val featureNamespaces: Set<String> = emptySet(),
    /** Required transport capabilities. */
    val requiredTransports: Set<String> = emptySet(),
    /** Extension IDs this depends on. */
    val dependencies: Set<VendorExtensionId> = emptySet(),
    val verification: VerificationLevel = VerificationLevel.INFERRED,
    val trustLevel: ExtensionTrustLevel = ExtensionTrustLevel.DESCRIPTIVE,
    val lifecycle: ExtensionLifecycle = ExtensionLifecycle.DRAFT,
    val limitations: List<String> = emptyList(),
) {
    init {
        require(version.isNotBlank()) { "extension version must not be blank" }
        require(manufacturerId.isNotBlank()) { "manufacturer id must not be blank" }
    }
}

package com.omnibuds.core.access

/**
 * Device classification.
 *
 * Phase 21 (OB-P21-REQ-001): distinguishes identity confidence, protocol
 * support, and write authorization as separate facts. Never merged into
 * a single field.
 */
enum class DeviceClassification {
    /** Identity or protocol cannot be established. Restricted read-only. */
    UNKNOWN_DEVICE,

    /** Some identity known; exact model/protocol unresolved. */
    PARTIALLY_IDENTIFIED,

    /** Model identified; no compatible verified protocol exists. */
    IDENTIFIED_UNSUPPORTED,

    /** Multiple plausible identities; writes disabled until resolved. */
    AMBIGUOUS_IDENTITY,

    /** Protocol known from research; implementation not validated. */
    KNOWN_PROTOCOL_UNVERIFIED,

    /** Protocol registered and compatible; capabilities determine access. */
    KNOWN_PROTOCOL_SUPPORTED,

    /** Fully supported: registered protocol + declared capabilities. */
    KNOWN_DEVICE_SUPPORTED,
}

/**
 * How confident the identity evidence is.
 * Separate from classification — a device can be classified while
 * confidence remains low.
 */
enum class IdentityConfidence {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * The complete access-relevant device state.
 * Three separate facts, never merged.
 */
data class DeviceAccessState(
    val classification: DeviceClassification,
    val identityConfidence: IdentityConfidence,
    /** True when a compatible, verified protocol is registered. */
    val protocolVerified: Boolean,
    /** True when any write path is authorized for this device. */
    val writeAuthorized: Boolean,
) {
    init {
        // Write authorization requires at least a supported protocol.
        if (writeAuthorized) {
            require(
                classification == DeviceClassification.KNOWN_PROTOCOL_SUPPORTED ||
                    classification == DeviceClassification.KNOWN_DEVICE_SUPPORTED,
            ) {
                "write authorization requires a supported protocol classification"
            }
            require(protocolVerified) {
                "write authorization requires a verified protocol"
            }
        }
    }

    companion object {
        /** The default for a newly discovered device: unknown, restricted. */
        fun unknown(): DeviceAccessState = DeviceAccessState(
            classification = DeviceClassification.UNKNOWN_DEVICE,
            identityConfidence = IdentityConfidence.NONE,
            protocolVerified = false,
            writeAuthorized = false,
        )
    }
}

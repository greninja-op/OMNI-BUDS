package com.omnibuds.core.presentation.control

/**
 * Status of an in-flight control operation on a hardware feature.
 */
enum class ControlExecutionStatus {
    IDLE,
    PENDING,
    SUCCEEDED,
    REJECTED,
    TIMED_OUT,
    AMBIGUOUS,
    FAILED,
}

/**
 * 6-state capability representation for hardware features.
 */
enum class FeatureCapabilityKind {
    UNSUPPORTED,
    UNKNOWN,
    READ_ONLY,
    VOLATILE,
    PERSISTENT,
    PERSISTENCE_VERIFIED,
}

/**
 * Unified representation of a controllable hardware feature across platforms.
 */
data class UnifiedHardwareControlModel(
    val featureId: String,
    val displayName: String,
    val category: String = "HARDWARE",
    val capabilityKind: FeatureCapabilityKind = FeatureCapabilityKind.UNKNOWN,
    val currentValue: String? = null,
    val desiredValue: String? = null,
    val acknowledgedValue: String? = null,
    val observedValue: String? = null,
    val executionStatus: ControlExecutionStatus = ControlExecutionStatus.IDLE,
    val availableOptions: List<String> = emptyList(),
    val failureReason: String? = null,
    val explanation: String? = null,
) {
    val isActionable: Boolean
        get() = capabilityKind in listOf(
            FeatureCapabilityKind.VOLATILE,
            FeatureCapabilityKind.PERSISTENT,
            FeatureCapabilityKind.PERSISTENCE_VERIFIED,
        ) && executionStatus != ControlExecutionStatus.PENDING

    val isPending: Boolean get() = executionStatus == ControlExecutionStatus.PENDING
    val isReadOnly: Boolean get() = capabilityKind == FeatureCapabilityKind.READ_ONLY
    val isUnsupported: Boolean get() = capabilityKind == FeatureCapabilityKind.UNSUPPORTED
    val isUnknown: Boolean get() = capabilityKind == FeatureCapabilityKind.UNKNOWN

    val accessibilityAnnouncement: String
        get() {
            val parts = mutableListOf<String>()
            parts.add("$displayName control")
            currentValue?.let { parts.add("Current: $it") }
            when (capabilityKind) {
                FeatureCapabilityKind.UNSUPPORTED -> parts.add("Unsupported by device")
                FeatureCapabilityKind.UNKNOWN -> parts.add("Capability unknown")
                FeatureCapabilityKind.READ_ONLY -> parts.add("Read only")
                FeatureCapabilityKind.PERSISTENCE_VERIFIED -> parts.add("Persistence verified")
                else -> {}
            }
            if (isPending) parts.add("Operation pending")
            failureReason?.let { parts.add("Error: $it") }
            return parts.joinToString(", ")
        }
}

package com.omnibuds.core.access

/**
 * A read-only observation of device state.
 *
 * Phase 21 (OB-P21-REQ-010): every observation carries its full metadata.
 * Unknown values stay null/unknown — never fabricated defaults (OB-P21-REQ-011).
 */
data class DeviceObservation(
    /** Observation identifier, e.g. "battery", "connection_state". */
    val observationId: String,
    /** Address-free device identity reference (identityKey). */
    val deviceKey: String,
    /** Property identifier, e.g. "anc_mode". Null for whole-device observations. */
    val propertyId: String?,
    /** Typed value. Null means unknown — not zero, not a default. */
    val value: ObservationValue?,
    /** Where the observation came from. */
    val source: ObservationSource,
    /** Epoch millis when observed. */
    val observedAtMillis: Long,
    /** Whether this observation is current or stale. */
    val freshness: ObservationFreshness,
    /** Confidence in the value. */
    val confidence: ObservationConfidence,
    /** Applicable transport or protocol. Null when not applicable. */
    val channel: String?,
    /** Session correlation ID. Null when no session context. */
    val sessionId: String?,
    /** Human-readable limitations, e.g. "Android does not expose ANC state". */
    val limitations: List<String>,
)

/** Typed observation values. Unknown is represented by null, not a default. */
sealed interface ObservationValue {
    data class Text(val text: String) : ObservationValue
    data class Number(val value: Double, val unit: String?) : ObservationValue
    data class BooleanValue(val value: Boolean) : ObservationValue
    data class EnumValue(val value: String, val allowedValues: Set<String>) : ObservationValue
}

/** Where an observation came from. */
enum class ObservationSource {
    /** Android public Bluetooth/audio APIs. */
    ANDROID_PUBLIC_API,

    /** Verified vendor read-back through a registered protocol. */
    VERIFIED_VENDOR_READ,

    /** Manufacturer-provided metadata (advertising, EIR). */
    MANUFACTURER_METADATA,

    /** User-provided or locally stored information. */
    LOCAL_INFORMATION,

    /** Inferred from other observations — lowest confidence. */
    INFERRED,
}

/** Whether an observation is current. */
enum class ObservationFreshness {
    CURRENT,
    STALE,
    /** The observation was attempted but no value was available. */
    UNAVAILABLE,
}

/** Confidence in the observation. */
enum class ObservationConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}

/**
 * Read-only observation engine.
 *
 * Phase 21: records observations from legitimate interfaces only.
 * The engine never invents values and never requires permissions it
 * doesn't need (OB-P21-REQ-012).
 */
class ObservationEngine {

    private val observations = mutableListOf<DeviceObservation>()

    /** Record an observation. Replaces any older observation with the same id+device. */
    fun record(observation: DeviceObservation) {
        observations.removeAll {
            it.observationId == observation.observationId &&
                it.deviceKey == observation.deviceKey
        }
        observations.add(observation)
    }

    /** The latest observation for [observationId] on [deviceKey], or null. */
    fun latest(deviceKey: String, observationId: String): DeviceObservation? =
        observations.lastOrNull {
            it.deviceKey == deviceKey && it.observationId == observationId
        }

    /** Mark all observations for a device stale (e.g. on disconnect). */
    fun markStale(deviceKey: String, reason: String) {
        val stale = observations.filter { it.deviceKey == deviceKey }.map {
            it.copy(
                freshness = ObservationFreshness.STALE,
                limitations = it.limitations + reason,
            )
        }
        observations.removeAll { it.deviceKey == deviceKey }
        observations.addAll(stale)
    }

    /** All current (non-stale) observations for a device. */
    fun current(deviceKey: String): List<DeviceObservation> =
        observations.filter {
            it.deviceKey == deviceKey &&
                it.freshness == ObservationFreshness.CURRENT
        }

    /**
     * Convenience: record an "unknown" observation — the value is explicitly
     * not known. This is distinct from having no observation at all.
     */
    fun recordUnknown(
        deviceKey: String,
        observationId: String,
        propertyId: String?,
        source: ObservationSource,
        observedAtMillis: Long,
        limitation: String,
    ) {
        record(
            DeviceObservation(
                observationId = observationId,
                deviceKey = deviceKey,
                propertyId = propertyId,
                value = null, // Unknown. Never a fabricated default.
                source = source,
                observedAtMillis = observedAtMillis,
                freshness = ObservationFreshness.UNAVAILABLE,
                confidence = ObservationConfidence.UNKNOWN,
                channel = null,
                sessionId = null,
                limitations = listOf(limitation),
            ),
        )
    }
}

package com.omnibuds.core.platform

/**
 * Whether the platform would tell us about this device at all.
 *
 * The third axis in ADR-P3-001, and the one that keeps the other two honest. Prompt section 6 lists
 * `UNAVAILABLE` beside the connection states, but "we cannot observe this device" is not a condition
 * of a link: it is a condition of our view of it. Putting it in `DeviceConnectionState` would make
 * `DISCONNECTED` and `UNAVAILABLE` compete to explain the same absence, and a consumer could not
 * tell "the phone says nobody is connected" from "the phone will not say".
 *
 * It also mirrors a decision already taken for the host: `ApiAvailability` answers the same question
 * about an adapter and its features (ADR-P2-008), so the shape here is deliberate symmetry rather
 * than a fourth way to say the same thing.
 */
enum class DeviceAvailability {
    /** Not determined. Never collapsed into [UNAVAILABLE]. */
    UNKNOWN,

    /** The platform reported this device, so its other fields mean something. */
    AVAILABLE,

    /**
     * The platform declined to report the device - refused, masked, or silent about this mechanism.
     *
     * A statement about the observation, and the reason `BluetoothOperation` rows exist for
     * `PERMISSION_DENIED` rather than a new category (ADR-P3-006).
     */
    UNAVAILABLE,
}

/** True only when the device was actually reported to us. */
fun DeviceAvailability.isObservable(): Boolean = this == DeviceAvailability.AVAILABLE

/**
 * True when nothing is known, which is not the same as knowing there is nothing to know.
 *
 * [DeviceAvailability.UNAVAILABLE] is a positive report of refusal and must stay distinguishable
 * from an unread field (Phase 2 prompt section 5.3, applied one layer up).
 */
fun DeviceAvailability.isUnread(): Boolean = this == DeviceAvailability.UNKNOWN

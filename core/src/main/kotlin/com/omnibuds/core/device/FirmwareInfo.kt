package com.omnibuds.core.device

import com.omnibuds.core.state.VerificationLevel

/**
 * The version evidence attached to a device, held apart from its identity.
 *
 * Versions describe a build, not a device: an update changes [firmwareVersion] while
 * the device stays the same device. That is why these fields are not on
 * [DeviceIdentity], and why [DeviceFingerprint.identityKey] ignores this type —
 * folding a firmware version into identity would make an updated device look like a
 * newly discovered one (Phase 1 prompt sections 9.1 and 10).
 *
 * A null version means "not read", and nothing here may be defaulted: there is no
 * `unknown version 0`, no `"0.0.0"`, no empty string standing in for a value, and no
 * display helper that invents text, because a fabricated version string is exactly
 * how a device that was never queried ends up looking real (Phase 1 prompt
 * section 53, master sections 23 and 53, ADR-P0-016). Rendering decisions belong to
 * a later UI phase, which must render unknown as unknown.
 */
data class FirmwareInfo(
    /** Firmware version as reported, or null when it was not read. */
    val firmwareVersion: String? = null,

    /** Hardware revision as reported, or null when it was not read. */
    val hardwareRevision: String? = null,

    /** Protocol version as reported, or null when it was not read. */
    val protocolVersion: String? = null,

    /**
     * How strongly this version record is supported by evidence.
     *
     * Deliberately without a default value. A verification level is a claim about
     * what was done to establish the versions, and a type cannot honestly guess the
     * caller's evidence tier; state is also never represented by null
     * (`docs/phases/phase-0/specs.md` section 2, ADR-P0-014).
     *
     * A device for which no version information was obtained at all is represented by
     * an absent [DeviceFingerprint.firmware], not by a FirmwareInfo carrying a
     * made-up level. Presence of this type is itself the statement that some version
     * evidence exists.
     */
    val verification: VerificationLevel,
) {
    /** How many of the three version fields hold real text. Blank text counts as unknown. */
    val knownVersionCount: Int
        get() = listOf(firmwareVersion, hardwareRevision, protocolVersion)
            .count { DeviceIdentity.normalised(it) != null }

    /** True when no version at all was read: the honest state before firmware discovery. */
    val hasNoVersionEvidence: Boolean
        get() = knownVersionCount == 0

    /** True only when all three versions are known, at whatever evidence tier [verification] states. */
    val isFullyKnown: Boolean
        get() = knownVersionCount == VERSION_FIELD_COUNT

    companion object {
        private const val VERSION_FIELD_COUNT = 3
    }
}

package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.capability.SiliconFamily

/**
 * Honest read-only driver for silicon families whose write opcodes are not
 * verified yet. It prevents an unknown device from silently falling into the
 * generic prober while never inventing a vendor frame.
 */
class ReadOnlySiliconDriver(
    private val family: SiliconFamily,
    private val brandTokens: List<String>,
) : IVendorDriver {

    override val driverName: String = "${family.displayName} (read-only)"

    override val preferredTransport: TransportPreference = TransportPreference.BLE_FIRST

    override fun matches(
        deviceName: String?,
        macAddress: String,
        serviceUuids: List<String>,
    ): Boolean {
        val name = deviceName?.lowercase() ?: return false
        return brandTokens.any { name.contains(it, ignoreCase = true) }
    }

    // No verified write frame for this family. Returning an empty packet makes
    // the engine fail honestly instead of sending a guessed vendor command.
    override fun buildAncCommand(mode: String): ByteArray = ByteArray(0)
}

package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.protocol.TwsBatteryReport

enum class TransportPreference {
    BLE_FIRST,
    RFCOMM_FIRST,
    EITHER,
}

/**
 * Common modular interface for multi-vendor earbud hardware drivers.
 */
interface IVendorDriver {

    val driverName: String

    val preferredTransport: TransportPreference
        get() = TransportPreference.EITHER

    /**
     * Determines whether this driver handles the given earbud based on Device Name,
     * MAC OUI, or discovered GATT services.
     */
    fun matches(deviceName: String?, macAddress: String, serviceUuids: List<String> = emptyList()): Boolean

    /**
     * Builds packet frame to set noise control mode:
     * mode: "ANC", "TRANSPARENCY", or "OFF" (Normal)
     */
    fun buildAncCommand(mode: String): ByteArray

    /**
     * Builds packet frame to set EQ preset:
     * preset: "BALANCED" / "FLAT", "BASS_BOOST", "VOCAL", "TREBLE_BOOST", "HARMAN_TARGET", "WARMTH"
     */
    fun buildEqualizerCommand(preset: String): ByteArray? = null

    /**
     * Builds packet frame for Spatial Audio toggle.
     */
    fun buildSpatialAudioCommand(enabled: Boolean): ByteArray? = null

    /**
     * Builds packet frame for Game Mode (Low Latency).
     */
    fun buildGameModeCommand(enabled: Boolean): ByteArray? = null

    /**
     * Builds packet frame to query battery telemetry.
     */
    fun buildBatteryQueryCommand(): ByteArray? = null

    /**
     * Parses incoming byte payload into multi-component battery levels (Left, Right, Case).
     */
    fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? = null
}

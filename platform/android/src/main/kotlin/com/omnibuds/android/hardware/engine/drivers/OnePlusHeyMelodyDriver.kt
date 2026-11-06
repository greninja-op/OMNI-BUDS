package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.RealmeProtocolParser
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Driver for OnePlus, Realme, and Oppo (HeyMelody / BES Chipset Profile).
 */
class OnePlusHeyMelodyDriver : IVendorDriver {

    override val driverName: String = "OnePlus / Realme / Oppo (HeyMelody / BES)"

    override val preferredTransport: TransportPreference = TransportPreference.RFCOMM_FIRST

    private val knownOuiPrefixes = listOf(
        "74:D7:13", // realme Buds T300
        "94:DB:56",
        "E4:12:1D",
        "80:EA:CA",
        "30:50:75",
        "1C:B0:44",
        "A4:C3:F0",
        "BC:9A:C4",
        "DC:2C:26",
    )

    private val knownGattUuids = listOf(
        "00000100-3c17-d293-8e48-14fe2e4da212",
        "00000200-3c17-d293-8e48-14fe2e4da212",
        "0000fdab-0000-1000-8000-00805f9b34fb",
        "0000079a-d102-11e1-9b23-00025b00a5a5",
    )

    override fun matches(
        deviceName: String?,
        macAddress: String,
        serviceUuids: List<String>,
    ): Boolean {
        val name = deviceName?.lowercase() ?: ""
        if (name.contains("realme") || name.contains("oneplus") || name.contains("oppo") ||
            name.contains("heymelody") || name.contains("enco") || name.contains("t300")
        ) {
            return true
        }

        val macUpper = macAddress.uppercase()
        if (knownOuiPrefixes.any { macUpper.startsWith(it) }) {
            return true
        }

        val servicesLower = serviceUuids.map { it.lowercase() }
        if (knownGattUuids.any { known -> servicesLower.any { it.contains(known) } }) {
            return true
        }

        return false
    }

    override fun buildAncCommand(mode: String): ByteArray {
        return RealmeProtocolParser.buildAncCommand(mode)
    }

    override fun buildEqualizerCommand(preset: String): ByteArray {
        return RealmeProtocolParser.buildEqualizerCommand(preset)
    }

    override fun buildSpatialAudioCommand(enabled: Boolean): ByteArray {
        return RealmeProtocolParser.buildSpatialAudioCommand(enabled)
    }

    override fun buildGameModeCommand(enabled: Boolean): ByteArray {
        return RealmeProtocolParser.buildGameModeCommand(enabled)
    }

    override fun buildBatteryQueryCommand(): ByteArray {
        return RealmeProtocolParser.buildBatteryQuery()
    }

    override fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? {
        return RealmeProtocolParser.parseBatteryReport(bytes)
    }
}

package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.protocol.AncByteMapping
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Driver for Nothing and CMF Earbuds (Nothing OS / CMF SPP & BLE Protocol).
 * Delegates to [NothingXProtocolDriver].
 */
class NothingEarbudsDriver : IVendorDriver {

    private val delegate = NothingXProtocolDriver()

    override val driverName: String = delegate.driverName
    override val preferredTransport: TransportPreference = delegate.preferredTransport

    companion object {
        val NOTHING_HEADER = NothingXProtocolDriver.NOTHING_HEADER
        const val CMD_NOISE_CONTROL = NothingXProtocolDriver.CMD_NOISE_CONTROL
        const val SUB_NOISE_CONTROL = NothingXProtocolDriver.SUB_NOISE_CONTROL
        const val CMD_BATTERY_QUERY = NothingXProtocolDriver.CMD_BATTERY
        const val SUB_BATTERY_QUERY = NothingXProtocolDriver.SUB_BATTERY
        const val CMD_EQ = NothingXProtocolDriver.CMD_EQ
        const val SUB_EQ = NothingXProtocolDriver.SUB_EQ_PRESET
        const val CMD_LOW_LATENCY = NothingXProtocolDriver.CMD_LOW_LAG
        const val SUB_LOW_LATENCY = NothingXProtocolDriver.SUB_LOW_LAG
    }

    override fun matches(
        deviceName: String?,
        macAddress: String,
        serviceUuids: List<String>,
    ): Boolean = delegate.matches(deviceName, macAddress, serviceUuids)

    fun buildNothingFrame(cmd: Int, subCmd: Int, payload: ByteArray = ByteArray(0)): ByteArray =
        delegate.buildNothingFrame(cmd, subCmd, payload)

    override fun buildAncCommand(mode: String): ByteArray = delegate.buildAncCommand(mode)

    override fun buildAncCommand(mode: String, mapping: AncByteMapping): ByteArray = delegate.buildAncCommand(mode)

    override fun buildEqualizerCommand(preset: String): ByteArray = delegate.buildEqualizerCommand(preset)

    override fun buildGameModeCommand(enabled: Boolean): ByteArray = delegate.buildGameModeCommand(enabled)

    override fun buildBatteryQueryCommand(): ByteArray = delegate.buildBatteryQueryCommand()

    override fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? = delegate.parseBatteryReport(bytes)

    override fun parseAncModeNotification(bytes: ByteArray, mapping: AncByteMapping): String? =
        delegate.parseAncModeNotification(bytes, mapping)

    override fun isAncAck(bytes: ByteArray): Boolean = delegate.isAncAck(bytes)
}

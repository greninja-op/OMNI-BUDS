package com.omnibuds.android.hardware.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.omnibuds.android.hardware.engine.drivers.GenericBleProberDriver
import com.omnibuds.android.hardware.engine.drivers.IVendorDriver
import com.omnibuds.android.hardware.engine.drivers.NothingEarbudsDriver
import com.omnibuds.android.hardware.engine.drivers.NothingXProtocolDriver
import com.omnibuds.android.hardware.engine.drivers.OnePlusHeyMelodyDriver
import com.omnibuds.android.hardware.engine.drivers.SamsungGalaxyBudsDriver
import com.omnibuds.android.hardware.engine.drivers.XiaomiAirohaRaceDriver
import com.omnibuds.android.hardware.protocol.AncByteMapping
import com.omnibuds.android.hardware.protocol.OpcodeRemapMatrix
import com.omnibuds.android.hardware.protocol.TwsBatteryReport
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-Vendor Hardware Protocol Router.
 *
 * Implements Layer 2:
 * Auto-detects connected earbuds by MAC OUI, Device Name, or Discovered Service UUIDs/RFCOMM channels,
 * and routes commands (ANC, Transparency, Equalizer, Game Mode) to the matching hardware driver.
 */
class VendorProtocolRouter {

    private val drivers: List<IVendorDriver> = listOf(
        NothingXProtocolDriver(),
        OnePlusHeyMelodyDriver(),
        XiaomiAirohaRaceDriver(),
        NothingEarbudsDriver(),
        SamsungGalaxyBudsDriver(),
        GenericBleProberDriver(),
    )

    private val resolvedDrivers = ConcurrentHashMap<String, IVendorDriver>()

    @SuppressLint("MissingPermission")
    fun resolveDriver(device: BluetoothDevice, serviceUuids: List<String> = emptyList()): IVendorDriver {
        val address = device.address
        resolvedDrivers[address]?.let { return it }

        val name = try { device.name } catch (_: Exception) { null }

        for (driver in drivers) {
            // Check specific drivers before generic fallback
            if (driver !is GenericBleProberDriver) {
                if (driver.matches(name, address, serviceUuids)) {
                    resolvedDrivers[address] = driver
                    return driver
                }
            }
        }

        val fallback = drivers.first { it is GenericBleProberDriver }
        resolvedDrivers[address] = fallback
        return fallback
    }

    fun getDriverForAddress(address: String): IVendorDriver? = resolvedDrivers[address]

    @SuppressLint("MissingPermission")
    fun registerDiscoveredServices(address: String, device: BluetoothDevice, serviceUuids: List<String>) {
        val name = try { device.name } catch (_: Exception) { null }
        for (driver in drivers) {
            if (driver !is GenericBleProberDriver) {
                if (driver.matches(name, address, serviceUuids)) {
                    resolvedDrivers[address] = driver
                    return
                }
            }
        }
    }

    fun getAncMapping(address: String): AncByteMapping = OpcodeRemapMatrix.getMapping(address)

    fun cycleAncProfile(address: String): AncByteMapping = OpcodeRemapMatrix.cycleProfile(address)

    fun setCustomAncMapping(address: String, off: Byte, anc: Byte, trans: Byte): AncByteMapping =
        OpcodeRemapMatrix.setCustomMapping(address, off, anc, trans)

    /**
     * Builds the matching ANC command packet for the given device with opcode remap matrix applied.
     */
    fun buildAncCommand(device: BluetoothDevice, mode: String, serviceUuids: List<String> = emptyList()): ByteArray {
        val driver = resolveDriver(device, serviceUuids)
        val mapping = getAncMapping(device.address)
        return driver.buildFullHardwareAncCommand(mode, mapping)
    }

    /**
     * Builds status query packet to retrieve current ANC / noise control status from hardware.
     */
    fun buildAncQueryCommand(device: BluetoothDevice, serviceUuids: List<String> = emptyList()): ByteArray? {
        val driver = resolveDriver(device, serviceUuids)
        return driver.buildAncQueryCommand()
    }

    /**
     * Parses incoming byte payload for ANC mode ACK, notification, or earbud pinch/tap.
     */
    fun parseAncModeNotification(address: String, bytes: ByteArray): String? {
        val driver = getDriverForAddress(address) ?: drivers.first()
        val mapping = getAncMapping(address)
        return driver.parseAncModeNotification(bytes, mapping)
    }

    /**
     * Checks if the incoming packet is a noise control command execution ACK for the driver.
     */
    fun isAncAck(address: String, bytes: ByteArray): Boolean {
        val driver = getDriverForAddress(address) ?: drivers.first()
        return driver.isAncAck(bytes)
    }

    /**
     * Builds the matching Equalizer command packet for the given device.
     */
    fun buildEqualizerCommand(device: BluetoothDevice, preset: String, serviceUuids: List<String> = emptyList()): ByteArray? {
        val driver = resolveDriver(device, serviceUuids)
        return driver.buildEqualizerCommand(preset)
    }

    /**
     * Builds the matching Spatial Audio command packet for the given device.
     */
    fun buildSpatialAudioCommand(device: BluetoothDevice, enabled: Boolean, serviceUuids: List<String> = emptyList()): ByteArray? {
        val driver = resolveDriver(device, serviceUuids)
        return driver.buildSpatialAudioCommand(enabled)
    }

    /**
     * Builds the matching Game Mode command packet for the given device.
     */
    fun buildGameModeCommand(device: BluetoothDevice, enabled: Boolean, serviceUuids: List<String> = emptyList()): ByteArray? {
        val driver = resolveDriver(device, serviceUuids)
        return driver.buildGameModeCommand(enabled)
    }

    /**
     * Builds the matching battery query packet for the given device.
     */
    fun buildBatteryQueryCommand(device: BluetoothDevice, serviceUuids: List<String> = emptyList()): ByteArray? {
        val driver = resolveDriver(device, serviceUuids)
        return driver.buildBatteryQueryCommand()
    }

    fun buildUltraBassCommand(device: BluetoothDevice, enabled: Boolean, level: Int): ByteArray? {
        val driver = resolveDriver(device)
        return if (driver is NothingXProtocolDriver) {
            driver.buildUltraBassCommand(enabled, level)
        } else {
            null
        }
    }

    fun buildCustom3BandEqCommand(device: BluetoothDevice, bass: Int, mid: Int, treble: Int): ByteArray? {
        val driver = resolveDriver(device)
        return if (driver is NothingXProtocolDriver) {
            driver.buildCustom3BandEqCommand(bass, mid, treble)
        } else {
            null
        }
    }

    fun buildInEarDetectionCommand(device: BluetoothDevice, enabled: Boolean): ByteArray? {
        val driver = resolveDriver(device)
        return if (driver is NothingXProtocolDriver) {
            driver.buildInEarDetectionCommand(enabled)
        } else {
            null
        }
    }

    fun buildDualConnectionCommand(device: BluetoothDevice, enabled: Boolean): ByteArray? {
        val driver = resolveDriver(device)
        return if (driver is NothingXProtocolDriver) {
            driver.buildDualConnectionCommand(enabled)
        } else {
            null
        }
    }

    fun buildFindMyBudsCommand(device: BluetoothDevice, bud: String, ring: Boolean): ByteArray? {
        val driver = resolveDriver(device)
        return if (driver is NothingXProtocolDriver) {
            driver.buildFindMyBudsCommand(bud, ring)
        } else {
            null
        }
    }

    fun buildTouchControlCommand(device: BluetoothDevice, bud: String, gesture: String, action: String): ByteArray? {
        val driver = resolveDriver(device)
        return if (driver is NothingXProtocolDriver) {
            driver.buildTouchControlCommand(bud, gesture, action)
        } else {
            null
        }
    }

    /**
     * Attempts to parse an incoming packet with the device's driver first,
     * falling back to all known drivers.
     */
    fun parseBatteryReport(address: String?, bytes: ByteArray): TwsBatteryReport? {
        if (bytes.isEmpty()) return null

        val deviceDriver = address?.let { resolvedDrivers[it] }
        deviceDriver?.parseBatteryReport(bytes)?.let { return it }

        for (driver in drivers) {
            if (driver != deviceDriver) {
                driver.parseBatteryReport(bytes)?.let { return it }
            }
        }
        return null
    }

    fun clear(address: String) {
        resolvedDrivers.remove(address)
    }
}

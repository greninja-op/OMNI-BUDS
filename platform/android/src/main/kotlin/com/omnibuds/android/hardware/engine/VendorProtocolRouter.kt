package com.omnibuds.android.hardware.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.omnibuds.android.hardware.engine.drivers.GenericBleProberDriver
import com.omnibuds.android.hardware.engine.drivers.IVendorDriver
import com.omnibuds.android.hardware.engine.drivers.NothingEarbudsDriver
import com.omnibuds.android.hardware.engine.drivers.OnePlusHeyMelodyDriver
import com.omnibuds.android.hardware.engine.drivers.SamsungGalaxyBudsDriver
import com.omnibuds.android.hardware.engine.drivers.XiaomiAirohaRaceDriver
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

    /**
     * Builds the matching ANC command packet for the given device.
     */
    fun buildAncCommand(device: BluetoothDevice, mode: String, serviceUuids: List<String> = emptyList()): ByteArray {
        val driver = resolveDriver(device, serviceUuids)
        return driver.buildAncCommand(mode)
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

package com.omnibuds.app

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid
import java.util.UUID

/**
 * Decides whether a Bluetooth device belongs in OmniBuds at all.
 *
 * OmniBuds is for personal audio wearables only: earbuds, TWS, headsets,
 * neckbands and headphones. Bluetooth speakers, car kits, set-top boxes,
 * phones, computers and everything else are excluded.
 *
 * Primary signal is the Bluetooth Class of Device (major = AUDIO_VIDEO,
 * minor = a wearable/headset/headphone form factor). When the class is
 * missing or uncategorised (common for BLE-only devices), advertised
 * audio service UUIDs and, last, the product name decide.
 */
object AudioWearableFilter {

    private val audioServiceUuids = setOf(
        UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb"), // A2DP Audio Sink
        UUID.fromString("00001108-0000-1000-8000-00805f9b34fb"), // Headset
        UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb"), // Hands-Free
        UUID.fromString("0000184e-0000-1000-8000-00805f9b34fb"), // LE Audio ASCS
        UUID.fromString("00001850-0000-1000-8000-00805f9b34fb"), // LE Audio PACS
    )

    private val wearableNameHints = listOf(
        "buds", "airpods", "earbud", "earbuds", "tws", "headphone", "headset",
        "earphone", "neckband", "freebuds", "soundcore", "pods",
    )

    /**
     * Car audio gate. Car kits and car head units report the same
     * "handsfree" Bluetooth class as headsets, so the class alone cannot
     * keep them out — their names can. Matched as whole name tokens so
     * brands like Audio-Technica ("audio") never trip the "audi" token.
     */
    private val carNameTokens = setOf(
        "car", "carkit", "innova", "fortuner", "creta", "nexon", "baleno",
        "ertiga", "scorpio", "toyota", "honda", "hyundai", "kia", "ford",
        "nissan", "renault", "skoda", "volkswagen", "mahindra", "maruti",
        "suzuki", "bmw", "audi", "jeep", "chevrolet", "fiat", "volvo",
    )

    private fun isCarAudio(device: BluetoothDevice): Boolean {
        val name = try {
            device.name
        } catch (_: Exception) {
            null
        }?.lowercase() ?: return false
        if (name.contains("car kit") || name.contains("car-kit")) return true
        val tokens = name.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return tokens.any { it in carNameTokens }
    }

    fun isAudioWearable(
        device: BluetoothDevice,
        advertisedServices: Collection<ParcelUuid>? = null,
    ): Boolean {
        // Cars are out first, whatever class they claim.
        if (isCarAudio(device)) return false
        val bluetoothClass = try {
            device.bluetoothClass
        } catch (_: Exception) {
            null
        }
        if (bluetoothClass != null) {
            when (bluetoothClass.majorDeviceClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                    return when (bluetoothClass.deviceClass) {
                        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
                        -> true
                        BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED ->
                            heuristic(device, advertisedServices)
                        // Loudspeakers, car audio, hi-fi, portable players,
                        // set-top boxes and other AV gear: not wearables.
                        else -> false
                    }
                }
                BluetoothClass.Device.Major.UNCATEGORIZED ->
                    return heuristic(device, advertisedServices)
                // Phones, computers, peripherals, imaging, toys, etc.
                else -> return false
            }
        }
        return heuristic(device, advertisedServices)
    }

    private fun heuristic(
        device: BluetoothDevice,
        advertisedServices: Collection<ParcelUuid>?,
    ): Boolean {
        if (advertisedServices != null && advertisedServices.any { it.uuid in audioServiceUuids }) {
            return true
        }
        val name = try {
            device.name
        } catch (_: Exception) {
            null
        }?.lowercase() ?: return false
        return wearableNameHints.any { name.contains(it) }
    }
}

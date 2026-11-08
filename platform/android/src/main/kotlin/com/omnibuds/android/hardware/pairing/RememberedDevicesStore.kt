package com.omnibuds.android.hardware.pairing

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Record of an audio device that has completed pairing and bonding.
 */
data class RememberedDevice(
    val address: String,
    val name: String,
    val formFactor: String,
    val firstPairedTimestamp: Long = System.currentTimeMillis(),
    val lastConnectedTimestamp: Long = System.currentTimeMillis(),
    val autoReconnect: Boolean = true,
)

/**
 * Lightweight local storage for bonded and remembered Bluetooth audio wearables.
 * Backed by Android [SharedPreferences] with in-memory synchronization.
 */
class RememberedDevicesStore(
    private val preferences: SharedPreferences? = null,
) {
    private val memoryMap = ConcurrentHashMap<String, RememberedDevice>()

    init {
        loadFromPreferences()
    }

    private fun loadFromPreferences() {
        val prefs = preferences ?: return
        val addresses = prefs.getStringSet(KEY_ADDRESSES, emptySet()) ?: emptySet()
        for (address in addresses) {
            val name = prefs.getString(PREFIX_NAME + address, null) ?: continue
            val formFactor = prefs.getString(PREFIX_FORM + address, "STEM_IN_EAR") ?: "STEM_IN_EAR"
            val firstPaired = prefs.getLong(PREFIX_FIRST + address, 0L)
            val lastConnected = prefs.getLong(PREFIX_LAST + address, 0L)
            val autoReconnect = prefs.getBoolean(PREFIX_AUTO + address, true)

            memoryMap[address] = RememberedDevice(
                address = address,
                name = name,
                formFactor = formFactor,
                firstPairedTimestamp = firstPaired,
                lastConnectedTimestamp = lastConnected,
                autoReconnect = autoReconnect,
            )
        }
    }

    @Synchronized
    fun saveDevice(device: RememberedDevice) {
        memoryMap[device.address] = device
        preferences?.edit()?.apply {
            val addresses = HashSet(preferences.getStringSet(KEY_ADDRESSES, emptySet()) ?: emptySet())
            addresses.add(device.address)
            putStringSet(KEY_ADDRESSES, addresses)
            putString(PREFIX_NAME + device.address, device.name)
            putString(PREFIX_FORM + device.address, device.formFactor)
            putLong(PREFIX_FIRST + device.address, device.firstPairedTimestamp)
            putLong(PREFIX_LAST + device.address, device.lastConnectedTimestamp)
            putBoolean(PREFIX_AUTO + device.address, device.autoReconnect)
            apply()
        }
    }

    fun getDevice(address: String): RememberedDevice? {
        return memoryMap[address]
    }

    fun getAllDevices(): List<RememberedDevice> {
        return memoryMap.values.toList().sortedByDescending { it.lastConnectedTimestamp }
    }

    @Synchronized
    fun removeDevice(address: String) {
        memoryMap.remove(address)
        preferences?.edit()?.apply {
            val addresses = HashSet(preferences.getStringSet(KEY_ADDRESSES, emptySet()) ?: emptySet())
            addresses.remove(address)
            putStringSet(KEY_ADDRESSES, addresses)
            remove(PREFIX_NAME + address)
            remove(PREFIX_FORM + address)
            remove(PREFIX_FIRST + address)
            remove(PREFIX_LAST + address)
            remove(PREFIX_AUTO + address)
            apply()
        }
    }

    @Synchronized
    fun updateLastConnected(address: String, timestamp: Long = System.currentTimeMillis()) {
        val existing = memoryMap[address] ?: return
        val updated = existing.copy(lastConnectedTimestamp = timestamp)
        saveDevice(updated)
    }

    @Synchronized
    fun setAutoReconnect(address: String, enabled: Boolean) {
        val existing = memoryMap[address] ?: return
        val updated = existing.copy(autoReconnect = enabled)
        saveDevice(updated)
    }

    @Synchronized
    fun clear() {
        memoryMap.clear()
        preferences?.edit()?.clear()?.apply()
    }

    companion object {
        private const val PREFS_NAME = "omnibuds_remembered_devices"
        private const val KEY_ADDRESSES = "remembered_addresses"
        private const val PREFIX_NAME = "name:"
        private const val PREFIX_FORM = "form:"
        private const val PREFIX_FIRST = "first:"
        private const val PREFIX_LAST = "last:"
        private const val PREFIX_AUTO = "auto:"

        @Volatile
        private var instance: RememberedDevicesStore? = null

        fun get(context: Context): RememberedDevicesStore {
            return instance ?: synchronized(this) {
                instance ?: RememberedDevicesStore(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
        }
    }
}

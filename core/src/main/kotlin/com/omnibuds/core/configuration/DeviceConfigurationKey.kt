package com.omnibuds.core.configuration

/**
 * Stable key identifying one device's configuration.
 *
 * Phase 17 (OB-P17-REQ-007): wraps the fingerprint-derived, address-free
 * identity key from [com.omnibuds.core.persistence.SavedDeviceRecord].
 * A blank key is refused — it identifies nothing.
 *
 * The key distinguishes devices sufficiently to avoid cross-device leakage.
 * It is never a raw Bluetooth address.
 */
@JvmInline
value class DeviceConfigurationKey private constructor(val value: String) {

    companion object {
        fun of(identityKey: String): DeviceConfigurationKey {
            require(identityKey.isNotBlank()) {
                "Device configuration key must not be blank"
            }
            return DeviceConfigurationKey(identityKey)
        }
    }

    override fun toString(): String = "DeviceConfig($value)"
}

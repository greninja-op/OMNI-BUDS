package com.omnibuds.core.configuration

import com.omnibuds.core.config.ConfigurationValue

/**
 * Schema version for persisted configurations.
 *
 * Phase 17 (OB-P17-REQ-019): every persisted configuration identifies its
 * schema version. Current version is 1.
 */
object ConfigurationSchema {
    const val CURRENT_VERSION: Int = 1
    const val MIN_SUPPORTED_VERSION: Int = 1
}

/**
 * Device-specific configuration.
 *
 * Phase 17: immutable. Preferences only — never a claim about hardware
 * state. A saved preference is not proof the hardware accepted it.
 */
data class DeviceConfiguration(
    val key: DeviceConfigurationKey,
    val schemaVersion: Int,
    /** Typed preferences; absent key = no preference (never an implied default). */
    val preferences: Map<String, ConfigurationValue>,
    /** When this configuration was last written. */
    val updatedAtMillis: Long,
) {
    init {
        require(schemaVersion >= 1) { "schemaVersion must be positive" }
        require(updatedAtMillis >= 0) { "updatedAtMillis must be non-negative" }
        require(preferences.keys.all { it.isNotBlank() }) {
            "preference keys must not be blank"
        }
    }

    companion object {
        fun empty(key: DeviceConfigurationKey, nowMillis: Long): DeviceConfiguration =
            DeviceConfiguration(
                key = key,
                schemaVersion = ConfigurationSchema.CURRENT_VERSION,
                preferences = emptyMap(),
                updatedAtMillis = nowMillis,
            )
    }
}

/**
 * Global application preferences.
 *
 * Phase 17: app-level settings, not device-specific. Never contains
 * hardware state.
 */
data class GlobalConfiguration(
    val schemaVersion: Int,
    val preferences: Map<String, ConfigurationValue>,
    val updatedAtMillis: Long,
) {
    init {
        require(schemaVersion >= 1) { "schemaVersion must be positive" }
        require(updatedAtMillis >= 0) { "updatedAtMillis must be non-negative" }
    }

    companion object {
        fun empty(nowMillis: Long): GlobalConfiguration = GlobalConfiguration(
            schemaVersion = ConfigurationSchema.CURRENT_VERSION,
            preferences = emptyMap(),
            updatedAtMillis = nowMillis,
        )
    }
}

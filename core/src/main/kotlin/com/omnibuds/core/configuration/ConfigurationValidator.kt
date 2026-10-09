package com.omnibuds.core.configuration

import com.omnibuds.core.config.ConfigurationValue

/**
 * Validates configurations before persistence and before application.
 *
 * Phase 17 (OB-P17-REQ-014): invalid configuration is never persisted as
 * valid. Errors are structured; values are never silently clamped.
 */
object ConfigurationValidator {

    /** Maximum preference key length. */
    const val MAX_KEY_LENGTH: Int = 128

    /** Maximum string value length. */
    const val MAX_STRING_LENGTH: Int = 4096

    /** Maximum preferences per configuration (DoS guard). */
    const val MAX_PREFERENCES: Int = 256

    fun validateDevice(config: DeviceConfiguration): List<String> {
        val errors = mutableListOf<String>()
        if (config.schemaVersion < ConfigurationSchema.MIN_SUPPORTED_VERSION) {
            errors.add("schemaVersion ${config.schemaVersion} below minimum ${ConfigurationSchema.MIN_SUPPORTED_VERSION}")
        }
        if (config.schemaVersion > ConfigurationSchema.CURRENT_VERSION) {
            errors.add("schemaVersion ${config.schemaVersion} is newer than supported ${ConfigurationSchema.CURRENT_VERSION}")
        }
        errors.addAll(validatePreferences(config.preferences))
        return errors
    }

    fun validateGlobal(config: GlobalConfiguration): List<String> {
        val errors = mutableListOf<String>()
        if (config.schemaVersion < ConfigurationSchema.MIN_SUPPORTED_VERSION) {
            errors.add("schemaVersion ${config.schemaVersion} below minimum")
        }
        if (config.schemaVersion > ConfigurationSchema.CURRENT_VERSION) {
            errors.add("schemaVersion ${config.schemaVersion} newer than supported")
        }
        errors.addAll(validatePreferences(config.preferences))
        return errors
    }

    private fun validatePreferences(prefs: Map<String, ConfigurationValue>): List<String> {
        val errors = mutableListOf<String>()
        if (prefs.size > MAX_PREFERENCES) {
            errors.add("too many preferences: ${prefs.size} > $MAX_PREFERENCES")
        }
        for ((key, value) in prefs) {
            if (key.length > MAX_KEY_LENGTH) {
                errors.add("preference key too long: $key")
            }
            validateValue(key, value)?.let { errors.add(it) }
        }
        return errors
    }

    private fun validateValue(key: String, value: ConfigurationValue): String? = when (value) {
        is ConfigurationValue.StringValue ->
            if (value.value.length > MAX_STRING_LENGTH) "preference '$key' string too long" else null
        is ConfigurationValue.CustomValue ->
            if (value.payload.length > MAX_STRING_LENGTH) "preference '$key' payload too long" else null
        is ConfigurationValue.StructuredValue ->
            if (value.fields.size > MAX_PREFERENCES) "preference '$key' has too many fields" else null
        else -> null // Boolean/Int/Mode/Float/Range/Bitmask validated at construction.
    }
}

package com.omnibuds.core.configuration

/**
 * Explicit outcomes for configuration reads.
 *
 * Phase 17 (OB-P17-REQ-010): never one nullable object hiding every outcome.
 */
sealed interface DeviceConfigurationResult {
    /** Configuration exists and is valid. */
    data class Found(val configuration: DeviceConfiguration) : DeviceConfigurationResult

    /** No configuration stored for this key. Normal, not an error. */
    data object NotFound : DeviceConfigurationResult

    /** Stored data exists but is invalid or corrupted. */
    data class Invalid(val reason: String) : DeviceConfigurationResult

    /** The stored schema is older and requires migration. */
    data class NeedsMigration(val storedVersion: Int) : DeviceConfigurationResult

    /** Storage itself failed. */
    data class ReadFailed(val reason: String) : DeviceConfigurationResult
}

/** Explicit outcomes for configuration writes. */
sealed interface ConfigurationWriteResult {
    /** The write committed. Success means committed, never assumed. */
    data object Saved : ConfigurationWriteResult

    /** Validation failed; nothing was written. */
    data class ValidationFailed(val errors: List<String>) : ConfigurationWriteResult

    /** The write failed; nothing was committed. */
    data class WriteFailed(val reason: String) : ConfigurationWriteResult
}

/**
 * The configuration repository contract.
 *
 * Phase 17 (OB-P17-REQ-010): implementations must be safe — a [Saved]
 * result corresponds to an actual committed write.
 */
interface ConfigurationRepository {

    suspend fun readGlobalConfiguration(): GlobalConfiguration

    suspend fun saveGlobalConfiguration(config: GlobalConfiguration): ConfigurationWriteResult

    suspend fun readDeviceConfiguration(key: DeviceConfigurationKey): DeviceConfigurationResult

    suspend fun saveDeviceConfiguration(config: DeviceConfiguration): ConfigurationWriteResult

    suspend fun resetDeviceConfiguration(key: DeviceConfigurationKey): ConfigurationWriteResult

    suspend fun resetGlobalConfiguration(): ConfigurationWriteResult
}

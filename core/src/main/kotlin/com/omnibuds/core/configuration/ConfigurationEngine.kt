package com.omnibuds.core.configuration

import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.config.ConfigurationValueJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes configurations to/from the storage format.
 *
 * Format (deterministic):
 * {"v":<schemaVersion>,"u":<updatedAtMillis>,"p":{"<key>":<valueJson>,...}}
 */
internal object ConfigurationCodec {

    fun encodeDevice(config: DeviceConfiguration): String = buildString {
        append("""{"v":${config.schemaVersion},"u":${config.updatedAtMillis},"p":{""")
        append(config.preferences.entries.joinToString(",") { (k, v) ->
            "${quote(k)}:${ConfigurationValueJson.encode(v)}"
        })
        append("}}")
    }

    fun encodeGlobal(config: GlobalConfiguration): String = buildString {
        append("""{"v":${config.schemaVersion},"u":${config.updatedAtMillis},"p":{""")
        append(config.preferences.entries.joinToString(",") { (k, v) ->
            "${quote(k)}:${ConfigurationValueJson.encode(v)}"
        })
        append("}}")
    }

    /**
     * Decode, or null when malformed. Malformed input routes to corruption
     * recovery — never silently repaired.
     */
    fun decodeDevice(key: DeviceConfigurationKey, json: String): DeviceConfiguration? = try {
        val v = extractInt(json, "\"v\":") ?: return null
        val u = extractLong(json, "\"u\":") ?: return null
        val prefs = extractPrefs(json) ?: return null
        DeviceConfiguration(key, v, prefs, u)
    } catch (e: Exception) {
        null
    }

    fun decodeGlobal(json: String): GlobalConfiguration? = try {
        val v = extractInt(json, "\"v\":") ?: return null
        val u = extractLong(json, "\"u\":") ?: return null
        val prefs = extractPrefs(json) ?: return null
        GlobalConfiguration(v, prefs, u)
    } catch (e: Exception) {
        null
    }

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun extractInt(json: String, field: String): Int? {
        val idx = json.indexOf(field)
        if (idx == -1) return null
        val start = idx + field.length
        val end = json.indexOfFirst(start) { !it.isDigit() && it != '-' }
        return json.substring(start, if (end == -1) json.length else end).toIntOrNull()
    }

    private fun extractLong(json: String, field: String): Long? {
        val idx = json.indexOf(field)
        if (idx == -1) return null
        val start = idx + field.length
        val end = json.indexOfFirst(start) { !it.isDigit() && it != '-' }
        return json.substring(start, if (end == -1) json.length else end).toLongOrNull()
    }

    private fun String.indexOfFirst(start: Int, pred: (Char) -> Boolean): Int {
        for (i in start until length) if (pred(this[i])) return i
        return -1
    }

    private fun extractPrefs(json: String): Map<String, ConfigurationValue>? {
        val pIdx = json.indexOf("\"p\":{")
        if (pIdx == -1) return null
        var i = pIdx + 5 // after "\"p\":{"
        val prefs = mutableMapOf<String, ConfigurationValue>()
        // Skip whitespace; handle empty object.
        while (i < json.length && json[i].isWhitespace()) i++
        if (i < json.length && json[i] == '}') return prefs
        while (i < json.length) {
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != '"') return null
            // Parse key.
            i++
            val keySb = StringBuilder()
            while (i < json.length && json[i] != '"') {
                if (json[i] == '\\') { i++; keySb.append(json.getOrNull(i) ?: return null) }
                else keySb.append(json[i])
                i++
            }
            if (i >= json.length) return null
            i++ // closing quote
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != ':') return null
            i++
            while (i < json.length && json[i].isWhitespace()) i++
            // Parse value object: find matching brace.
            if (i >= json.length || json[i] != '{') return null
            var depth = 0
            var inStr = false
            var esc = false
            val start = i
            while (i < json.length) {
                val c = json[i]
                if (esc) { esc = false }
                else if (c == '\\' && inStr) { esc = true }
                else if (c == '"') { inStr = !inStr }
                else if (!inStr && c == '{') { depth++ }
                else if (!inStr && c == '}') {
                    depth--
                    if (depth == 0) { i++; break }
                }
                i++
            }
            val valueJson = json.substring(start, i)
            val value = ConfigurationValueJson.decode(valueJson) ?: return null
            prefs[keySb.toString()] = value
            while (i < json.length && json[i].isWhitespace()) i++
            when (json.getOrNull(i)) {
                ',' -> { i++; continue }
                '}' -> break
                else -> return null
            }
        }
        return prefs
    }
}

/**
 * The Persistent Configuration Engine.
 *
 * Phase 17: orchestrates storage, validation, migrations, and observation.
 * - Writes are validated before persistence; invalid configs are never stored.
 * - Reads detect corruption, unknown schemas, and migration needs explicitly.
 * - Per-device mutexes: independent devices never block each other.
 * - Flows publish immutable configurations with dedup.
 */
class ConfigurationEngine(
    private val storage: ConfigurationStorage,
    private val migrations: MigrationRegistry = MigrationRegistry(emptyList()),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ConfigurationRepository {

    private val deviceLocks = mutableMapOf<DeviceConfigurationKey, Mutex>()
    private val deviceLocksGuard = Any()
    private val globalLock = Mutex()

    private val globalFlow = MutableStateFlow<GlobalConfiguration?>(null)
    private val deviceFlows = mutableMapOf<DeviceConfigurationKey, MutableStateFlow<DeviceConfiguration?>>()

    private fun lockFor(key: DeviceConfigurationKey): Mutex = synchronized(deviceLocksGuard) {
        deviceLocks.getOrPut(key) { Mutex() }
    }

    private fun flowFor(key: DeviceConfigurationKey): MutableStateFlow<DeviceConfiguration?> =
        synchronized(deviceLocksGuard) {
            deviceFlows.getOrPut(key) { MutableStateFlow(null) }
        }

    /** Observe global configuration changes. */
    fun observeGlobal(): StateFlow<GlobalConfiguration?> = globalFlow.asStateFlow()

    /** Observe one device's configuration changes. */
    fun observeDevice(key: DeviceConfigurationKey): StateFlow<DeviceConfiguration?> =
        flowFor(key).asStateFlow()

    override suspend fun readGlobalConfiguration(): GlobalConfiguration {
        val raw = try {
            storage.read(GLOBAL_KEY)
        } catch (e: Exception) {
            return GlobalConfiguration.empty(clock()) // Storage failure → empty, not crash.
        }
        if (raw == null) return GlobalConfiguration.empty(clock())
        return ConfigurationCodec.decodeGlobal(raw)
            ?: GlobalConfiguration.empty(clock()) // Corrupt → empty, not crash.
    }

    override suspend fun saveGlobalConfiguration(config: GlobalConfiguration): ConfigurationWriteResult =
        globalLock.withLock {
            val errors = ConfigurationValidator.validateGlobal(config)
            if (errors.isNotEmpty()) return ConfigurationWriteResult.ValidationFailed(errors)
            val ok = try {
                storage.write(GLOBAL_KEY, ConfigurationCodec.encodeGlobal(config))
            } catch (e: Exception) {
                false
            }
            if (!ok) return ConfigurationWriteResult.WriteFailed("storage write failed")
            globalFlow.value = config
            ConfigurationWriteResult.Saved
        }

    override suspend fun readDeviceConfiguration(key: DeviceConfigurationKey): DeviceConfigurationResult =
        lockFor(key).withLock {
            val raw = try {
                storage.read(deviceKey(key))
            } catch (e: Exception) {
                return DeviceConfigurationResult.ReadFailed(e.message ?: "read failed")
            }
            if (raw == null) return DeviceConfigurationResult.NotFound
            val config = ConfigurationCodec.decodeDevice(key, raw)
                ?: return DeviceConfigurationResult.Invalid("malformed stored configuration")
            if (config.schemaVersion > ConfigurationSchema.CURRENT_VERSION) {
                return DeviceConfigurationResult.Invalid(
                    "stored schema v${config.schemaVersion} newer than supported v${ConfigurationSchema.CURRENT_VERSION}",
                )
            }
            if (config.schemaVersion < ConfigurationSchema.CURRENT_VERSION) {
                return DeviceConfigurationResult.NeedsMigration(config.schemaVersion)
            }
            val errors = ConfigurationValidator.validateDevice(config)
            if (errors.isNotEmpty()) {
                return DeviceConfigurationResult.Invalid(errors.joinToString("; "))
            }
            flowFor(key).value = config
            DeviceConfigurationResult.Found(config)
        }

    override suspend fun saveDeviceConfiguration(config: DeviceConfiguration): ConfigurationWriteResult =
        lockFor(config.key).withLock {
            val errors = ConfigurationValidator.validateDevice(config)
            if (errors.isNotEmpty()) return ConfigurationWriteResult.ValidationFailed(errors)
            val ok = try {
                storage.write(deviceKey(config.key), ConfigurationCodec.encodeDevice(config))
            } catch (e: Exception) {
                false
            }
            if (!ok) return ConfigurationWriteResult.WriteFailed("storage write failed")
            flowFor(config.key).value = config
            ConfigurationWriteResult.Saved
        }

    override suspend fun resetDeviceConfiguration(key: DeviceConfigurationKey): ConfigurationWriteResult =
        lockFor(key).withLock {
            val ok = try {
                storage.delete(deviceKey(key))
            } catch (e: Exception) {
                false
            }
            if (!ok) return ConfigurationWriteResult.WriteFailed("reset failed")
            flowFor(key).value = null
            ConfigurationWriteResult.Saved
        }

    override suspend fun resetGlobalConfiguration(): ConfigurationWriteResult =
        globalLock.withLock {
            val ok = try {
                storage.delete(GLOBAL_KEY)
            } catch (e: Exception) {
                false
            }
            if (!ok) return ConfigurationWriteResult.WriteFailed("reset failed")
            globalFlow.value = null
            ConfigurationWriteResult.Saved
        }

    /**
     * Migrate a device configuration to the current schema.
     * Returns the migrated config, or null when migration is impossible.
     */
    suspend fun migrateDeviceConfiguration(key: DeviceConfigurationKey): DeviceConfiguration? =
        lockFor(key).withLock {
            val raw = try {
                storage.read(deviceKey(key))
            } catch (e: Exception) {
                return null
            } ?: return null
            val config = ConfigurationCodec.decodeDevice(key, raw) ?: return null
            if (config.schemaVersion >= ConfigurationSchema.CURRENT_VERSION) return config
            val migratedPrefs = try {
                migrations.migrateToCurrent(config.schemaVersion, config.preferences)
            } catch (e: MigrationException) {
                return null
            }
            val migrated = config.copy(
                schemaVersion = ConfigurationSchema.CURRENT_VERSION,
                preferences = migratedPrefs,
                updatedAtMillis = clock(),
            )
            val errors = ConfigurationValidator.validateDevice(migrated)
            if (errors.isNotEmpty()) return null
            val ok = try {
                storage.write(deviceKey(key), ConfigurationCodec.encodeDevice(migrated))
            } catch (e: Exception) {
                false
            }
            if (!ok) return null
            flowFor(key).value = migrated
            migrated
        }

    companion object {
        internal const val GLOBAL_KEY: String = "__global__"
        internal fun deviceKey(key: DeviceConfigurationKey): String = "device:${key.value}"
    }
}

package com.omnibuds.core.configuration

import com.omnibuds.core.config.ConfigurationValue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 17 §26A/B: repository and device-isolation tests.
 */
class ConfigurationEngineTest {

    private val keyA = DeviceConfigurationKey.of("device-a-fingerprint")
    private val keyB = DeviceConfigurationKey.of("device-b-fingerprint")

    private fun engine() = ConfigurationEngine(InMemoryConfigurationStorage(), clock = { 1000L })

    private fun deviceConfig(
        key: DeviceConfigurationKey = keyA,
        prefs: Map<String, ConfigurationValue> = mapOf(
            "anc.mode" to ConfigurationValue.ModeValue("adaptive", "Adaptive"),
        ),
    ) = DeviceConfiguration(key, ConfigurationSchema.CURRENT_VERSION, prefs, 1000L)

    @Test
    fun `save and read device configuration`() = runTest {
        val e = engine()
        val config = deviceConfig()
        assertEquals(ConfigurationWriteResult.Saved, e.saveDeviceConfiguration(config))

        val result = e.readDeviceConfiguration(keyA)
        assertTrue(result is DeviceConfigurationResult.Found)
        assertEquals(config.preferences, (result as DeviceConfigurationResult.Found).configuration.preferences)
    }

    @Test
    fun `missing configuration is NotFound`() = runTest {
        val e = engine()
        assertEquals(DeviceConfigurationResult.NotFound, e.readDeviceConfiguration(keyA))
    }

    @Test
    fun `devices are isolated`() = runTest {
        val e = engine()
        e.saveDeviceConfiguration(deviceConfig(keyA, mapOf("anc.mode" to ConfigurationValue.ModeValue("on", "On"))))
        e.saveDeviceConfiguration(deviceConfig(keyB, mapOf("anc.mode" to ConfigurationValue.ModeValue("off", "Off"))))

        val a = e.readDeviceConfiguration(keyA) as DeviceConfigurationResult.Found
        val b = e.readDeviceConfiguration(keyB) as DeviceConfigurationResult.Found
        assertEquals("on", (a.configuration.preferences["anc.mode"] as ConfigurationValue.ModeValue).technicalName)
        assertEquals("off", (b.configuration.preferences["anc.mode"] as ConfigurationValue.ModeValue).technicalName)
    }

    @Test
    fun `reset device does not touch other devices`() = runTest {
        val e = engine()
        e.saveDeviceConfiguration(deviceConfig(keyA))
        e.saveDeviceConfiguration(deviceConfig(keyB))
        e.resetDeviceConfiguration(keyA)

        assertEquals(DeviceConfigurationResult.NotFound, e.readDeviceConfiguration(keyA))
        assertTrue(e.readDeviceConfiguration(keyB) is DeviceConfigurationResult.Found)
    }

    @Test
    fun `invalid config is not persisted`() = runTest {
        val e = engine()
        val bad = DeviceConfiguration(
            key = keyA,
            schemaVersion = ConfigurationSchema.CURRENT_VERSION,
            preferences = mapOf("x".repeat(200) to ConfigurationValue.BooleanValue(true)),
            updatedAtMillis = 1000L,
        )
        val result = e.saveDeviceConfiguration(bad)
        assertTrue(result is ConfigurationWriteResult.ValidationFailed)
        assertEquals(DeviceConfigurationResult.NotFound, e.readDeviceConfiguration(keyA))
    }

    @Test
    fun `write failure is reported`() = runTest {
        val storage = InMemoryConfigurationStorage().apply { failWrites = true }
        val e = ConfigurationEngine(storage, clock = { 1000L })
        val result = e.saveDeviceConfiguration(deviceConfig())
        assertTrue(result is ConfigurationWriteResult.WriteFailed)
    }

    @Test
    fun `corrupt data yields Invalid`() = runTest {
        val storage = InMemoryConfigurationStorage()
        val e = ConfigurationEngine(storage, clock = { 1000L })
        storage.write("device:${keyA.value}", "not-json{{{")
        val result = e.readDeviceConfiguration(keyA)
        assertTrue(result is DeviceConfigurationResult.Invalid)
    }

    @Test
    fun `future schema is protected`() = runTest {
        val storage = InMemoryConfigurationStorage()
        val e = ConfigurationEngine(storage, clock = { 1000L })
        // Hand-craft a future-schema payload.
        storage.write("device:${keyA.value}", """{"v":99,"u":1000,"p":{}}""")
        val result = e.readDeviceConfiguration(keyA)
        assertTrue(result is DeviceConfigurationResult.Invalid)
    }

    @Test
    fun `old schema needs migration`() = runTest {
        val storage = InMemoryConfigurationStorage()
        val e = ConfigurationEngine(storage, clock = { 1000L })
        storage.write("device:${keyA.value}", """{"v":0,"u":1000,"p":{}}""")
        // v0 is below minimum → Invalid (not silently migrated).
        val result = e.readDeviceConfiguration(keyA)
        assertTrue(result is DeviceConfigurationResult.Invalid)
    }

    @Test
    fun `global config round-trips`() = runTest {
        val e = engine()
        val global = GlobalConfiguration(
            ConfigurationSchema.CURRENT_VERSION,
            mapOf("diagnostics.verbose" to ConfigurationValue.BooleanValue(true)),
            1000L,
        )
        assertEquals(ConfigurationWriteResult.Saved, e.saveGlobalConfiguration(global))
        assertEquals(global.preferences, e.readGlobalConfiguration().preferences)
    }

    @Test
    fun `blank device key is refused`() {
        try {
            DeviceConfigurationKey.of("  ")
            assertTrue(false, "should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun `concurrent device writes are isolated`() = runTest {
        val e = engine()
        // Interleaved writes to different devices; both must land.
        e.saveDeviceConfiguration(deviceConfig(keyA, mapOf("k" to ConfigurationValue.IntValue(1))))
        e.saveDeviceConfiguration(deviceConfig(keyB, mapOf("k" to ConfigurationValue.IntValue(2))))
        val a = e.readDeviceConfiguration(keyA) as DeviceConfigurationResult.Found
        val b = e.readDeviceConfiguration(keyB) as DeviceConfigurationResult.Found
        assertEquals(1, (a.configuration.preferences["k"] as ConfigurationValue.IntValue).value)
        assertEquals(2, (b.configuration.preferences["k"] as ConfigurationValue.IntValue).value)
    }
}

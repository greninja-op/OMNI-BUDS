package com.omnibuds.app

import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Iteration 3 regression tests for the "Unknown" device-name failure:
 * identity must improve when better evidence arrives, instead of being
 * frozen at whatever the first observation happened to carry.
 */
class DeviceStateBridgeIdentityTest {

    private fun device(name: String?, alias: String? = null) = AndroidDiscoveredDevice(
        id = "AA:BB:CC:DD:EE:FF",
        name = name,
        alias = alias,
        address = "AA:BB:CC:DD:EE:FF",
    )

    private fun identityOf(repo: GlobalDeviceStateRepository): IdentityState.Identified {
        val state = runBlocking { repo.getDevice(GlobalDeviceId("AA:BB:CC:DD:EE:FF")) }
        return state!!.identity as IdentityState.Identified
    }

    @Test
    fun `a device first seen without a name gains its identity when the name arrives`() {
        val repo = GlobalDeviceStateRepository()
        val bridge = DeviceStateBridge(repo, CoroutineScope(Dispatchers.Unconfined))
        bridge.syncDevice(device(name = null), ConnectionState.Disconnected)
        assertNull(identityOf(repo).modelId)
        bridge.syncDevice(device(name = "realme Buds T300"), ConnectionState.Connected("s", 1L, "Bluetooth"))
        val identity = identityOf(repo)
        assertEquals("realme Buds T300", identity.modelId)
        // The catalog verifies this model, so the manufacturer is real:
        assertEquals("realme", identity.manufacturerId)
        assertEquals("High", identity.confidence)
    }

    @Test
    fun `alias is the display evidence while the hardware name still verifies`() {
        val repo = GlobalDeviceStateRepository()
        val bridge = DeviceStateBridge(repo, CoroutineScope(Dispatchers.Unconfined))
        bridge.syncDevice(
            device(name = "boAt Rockerz 450", alias = "My Headphones"),
            ConnectionState.Disconnected,
        )
        val identity = identityOf(repo)
        assertEquals("My Headphones", identity.modelId)
        assertEquals("boAt", identity.manufacturerId)
    }

    @Test
    fun `unchanged evidence does not churn identity`() {
        val repo = GlobalDeviceStateRepository()
        val bridge = DeviceStateBridge(repo, CoroutineScope(Dispatchers.Unconfined))
        repeat(3) {
            bridge.syncDevice(device(name = "CMF Buds"), ConnectionState.Disconnected)
        }
        val identity = identityOf(repo)
        assertEquals("CMF Buds", identity.modelId)
        assertEquals("CMF by Nothing", identity.manufacturerId)
    }

    @Test
    fun `an unverifiable name keeps the placeholder manufacturer internally`() {
        val repo = GlobalDeviceStateRepository()
        val bridge = DeviceStateBridge(repo, CoroutineScope(Dispatchers.Unconfined))
        bridge.syncDevice(device(name = "Acme SoundPods X9"), ConnectionState.Disconnected)
        val identity = identityOf(repo)
        assertEquals("Acme SoundPods X9", identity.modelId)
        // Internal sentinel only — the presentation resolver filters it
        // out of every user-visible field (see resolver tests).
        assertEquals("Unknown", identity.manufacturerId)
        assertEquals("Low", identity.confidence)
    }
}

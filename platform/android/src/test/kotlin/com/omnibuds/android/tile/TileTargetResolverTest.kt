package com.omnibuds.android.tile

import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 25: target-resolution tests.
 */
class TileTargetResolverTest {

    private fun connected(id: String): GlobalDeviceState =
        GlobalDeviceState.empty(GlobalDeviceId(id)).copy(
            connection = ConnectionState.Connected("s-$id", 1L, "rfcomm"),
        )

    private fun disconnected(id: String): GlobalDeviceState =
        GlobalDeviceState.empty(GlobalDeviceId(id))

    @Test
    fun `no devices`() {
        val result = TileTargetResolver.resolve(emptyMap(), null)
        assertTrue(result is TargetResolution.NoDevice)
    }

    @Test
    fun `single eligible device resolves`() {
        val devices = mapOf(GlobalDeviceId("dev-1") to connected("dev-1"))
        val result = TileTargetResolver.resolve(devices, null)
        assertTrue(result is TargetResolution.Resolved)
        assertTrue((result as TargetResolution.Resolved).deviceId.value == "dev-1")
    }

    @Test
    fun `multiple eligible without selection is ambiguous`() {
        val devices = mapOf(
            GlobalDeviceId("dev-1") to connected("dev-1"),
            GlobalDeviceId("dev-2") to connected("dev-2"),
        )
        val result = TileTargetResolver.resolve(devices, null)
        assertTrue(result is TargetResolution.Ambiguous)
    }

    @Test
    fun `explicit selection preferred`() {
        val devices = mapOf(
            GlobalDeviceId("dev-1") to connected("dev-1"),
            GlobalDeviceId("dev-2") to connected("dev-2"),
        )
        val result = TileTargetResolver.resolve(devices, GlobalDeviceId("dev-2"))
        assertTrue(result is TargetResolution.Resolved)
        assertTrue((result as TargetResolution.Resolved).deviceId.value == "dev-2")
    }

    @Test
    fun `disconnected selection unresolvable`() {
        val devices = mapOf(
            GlobalDeviceId("dev-1") to connected("dev-1"),
            GlobalDeviceId("dev-2") to disconnected("dev-2"),
        )
        val result = TileTargetResolver.resolve(devices, GlobalDeviceId("dev-2"))
        assertTrue(result is TargetResolution.Unresolvable)
    }

    @Test
    fun `unknown selection unresolvable`() {
        val devices = mapOf(GlobalDeviceId("dev-1") to connected("dev-1"))
        val result = TileTargetResolver.resolve(devices, GlobalDeviceId("dev-9"))
        assertTrue(result is TargetResolution.Unresolvable)
    }

    @Test
    fun `no eligible devices`() {
        val devices = mapOf(GlobalDeviceId("dev-1") to disconnected("dev-1"))
        val result = TileTargetResolver.resolve(devices, null)
        assertTrue(result is TargetResolution.Unresolvable)
    }
}

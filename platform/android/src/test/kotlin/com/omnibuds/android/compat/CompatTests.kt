package com.omnibuds.android.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiLevelPolicyTest {

    @Test
    fun `bluetooth runtime permissions from API 31`() {
        assertFalse(ApiLevelPolicy.bluetoothRuntimePermissionsRequired(30))
        assertTrue(ApiLevelPolicy.bluetoothRuntimePermissionsRequired(31))
        assertTrue(ApiLevelPolicy.bluetoothRuntimePermissionsRequired(35))
    }

    @Test
    fun `legacy bluetooth model on 26 to 30`() {
        assertTrue(ApiLevelPolicy.legacyBluetoothPermissions(26))
        assertTrue(ApiLevelPolicy.legacyBluetoothPermissions(30))
        assertFalse(ApiLevelPolicy.legacyBluetoothPermissions(31))
    }

    @Test
    fun `notification runtime permission from API 33`() {
        assertFalse(ApiLevelPolicy.notificationRuntimePermissionRequired(32))
        assertTrue(ApiLevelPolicy.notificationRuntimePermissionRequired(33))
    }

    @Test
    fun `notification channels always required at minSdk`() {
        assertTrue(ApiLevelPolicy.notificationChannelsRequired(26))
        assertTrue(ApiLevelPolicy.notificationChannelsRequired(35))
    }

    @Test
    fun `tile service available at minSdk`() {
        assertTrue(ApiLevelPolicy.tileServiceAvailable(26))
    }

    @Test
    fun `supported range is 26 to 35`() {
        assertFalse(ApiLevelPolicy.isSupported(25))
        assertTrue(ApiLevelPolicy.isSupported(26))
        assertTrue(ApiLevelPolicy.isSupported(35))
        assertFalse(ApiLevelPolicy.isSupported(36))
    }

    @Test
    fun `foreground service types enforced from 29`() {
        assertFalse(ApiLevelPolicy.foregroundServiceTypesEnforced(28))
        assertTrue(ApiLevelPolicy.foregroundServiceTypesEnforced(29))
    }
}

class PermissionPolicyTest {

    @Test
    fun `granted allows`() {
        assertTrue(
            PermissionPolicy.decide(PermissionState.GRANTED, "op") is PermissionDecision.Allow,
        )
    }

    @Test
    fun `denied refuses without crashing`() {
        val d = PermissionPolicy.decide(PermissionState.DENIED, "op")
        assertTrue(d is PermissionDecision.Refuse)
        assertTrue((d as PermissionDecision.Refuse).reason.contains("denied"))
    }

    @Test
    fun `unavailable refuses distinctly`() {
        val d = PermissionPolicy.decide(PermissionState.UNAVAILABLE, "op")
        assertTrue(d is PermissionDecision.Refuse)
        assertTrue((d as PermissionDecision.Refuse).reason.contains("unavailable"))
    }

    @Test
    fun `restricted refuses distinctly`() {
        val d = PermissionPolicy.decide(PermissionState.RESTRICTED, "op")
        assertTrue(d is PermissionDecision.Refuse)
        assertTrue((d as PermissionDecision.Refuse).reason.contains("restricted"))
    }

    @Test
    fun `unknown defers for recheck`() {
        val d = PermissionPolicy.decide(PermissionState.UNKNOWN, "op")
        assertTrue(d is PermissionDecision.Defer)
    }

    @Test
    fun `security exception is classified as refusal`() {
        val d = PermissionPolicy.classifySecurityException("op")
        assertTrue(d is PermissionDecision.Refuse)
    }
}

class BluetoothPlatformPolicyTest {

    private fun state(
        present: Boolean = true,
        enabled: Boolean = true,
        connect: PermissionState = PermissionState.GRANTED,
        scan: PermissionState = PermissionState.GRANTED,
    ) = BluetoothPlatformState(present, enabled, connect, scan)

    @Test
    fun `no adapter cannot operate`() {
        val d = BluetoothPlatformPolicy.decide(state(present = false))
        assertTrue(d is BluetoothDecision.CannotOperate)
    }

    @Test
    fun `disabled adapter degrades honestly`() {
        val d = BluetoothPlatformPolicy.decide(state(enabled = false))
        assertTrue(d is BluetoothDecision.Degraded)
        assertTrue((d as BluetoothDecision.Degraded).reason.contains("disabled"))
    }

    @Test
    fun `granted permission may operate`() {
        assertTrue(BluetoothPlatformPolicy.decide(state()) is BluetoothDecision.MayOperate)
    }

    @Test
    fun `denied permission cannot operate`() {
        val d = BluetoothPlatformPolicy.decide(state(connect = PermissionState.DENIED))
        assertTrue(d is BluetoothDecision.CannotOperate)
    }

    @Test
    fun `unknown permission defers`() {
        val d = BluetoothPlatformPolicy.decide(state(connect = PermissionState.UNKNOWN))
        assertTrue(d is BluetoothDecision.Degraded)
    }

    @Test
    fun `permission grant does not prove operation success`() {
        // The policy only gates on permission + adapter state; the
        // transport still reports its own outcome per exchange.
        val d = BluetoothPlatformPolicy.decide(state())
        assertTrue(d is BluetoothDecision.MayOperate)
        assertFalse(d is BluetoothDecision.CannotOperate)
    }
}

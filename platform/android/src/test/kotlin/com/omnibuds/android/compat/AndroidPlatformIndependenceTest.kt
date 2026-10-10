package com.omnibuds.android.compat

import com.omnibuds.android.lifecycle.AndroidPlatformLifecycleSource
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformLifecycleState
import com.omnibuds.core.platform.PlatformType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 46 tests for Android platform adapter implementations.
 */
class AndroidPlatformIndependenceTest {

    @Test
    fun androidPlatformDescriptorReportsAccuratePlatformType() {
        val descriptor = AndroidPlatformDescriptor.fromApiLevel(
            apiLevel = 34,
            osVersion = "14",
            capabilities = BluetoothPlatformCapabilities.unobserved(),
        )

        assertEquals(PlatformType.ANDROID, descriptor.platformType)
        assertTrue(descriptor.isMobile)
        assertFalse(descriptor.isDesktop)
        assertEquals(34, descriptor.apiLevel)
        assertEquals("Android", descriptor.osName)
        assertEquals("14", descriptor.osVersion)
        assertEquals(PlatformType.ANDROID, descriptor.capabilities.platformType)
    }

    @Test
    fun androidPlatformIdentifierSourceGeneratesValidUniqueIds() {
        val source = AndroidPlatformIdentifierSource()

        val opId1 = source.generateOperationId()
        val opId2 = source.generateOperationId()
        val customOpId = source.generateOperationId("custom-prefix")

        assertTrue(opId1.startsWith("android-op-"))
        assertTrue(opId2.startsWith("android-op-"))
        assertTrue(customOpId.startsWith("custom-prefix-"))
        assertFalse(opId1 == opId2)

        val nonce1 = source.generateNonce()
        val nonce2 = source.generateNonce()
        assertNotNull(nonce1)
        assertNotNull(nonce2)
        assertFalse(nonce1.isBlank())
        assertFalse(nonce1 == nonce2)
    }

    @Test
    fun androidPlatformLifecycleSourceTracksStateTransitions() {
        val source = AndroidPlatformLifecycleSource(initialState = PlatformLifecycleState.BACKGROUND)

        assertEquals(PlatformLifecycleState.BACKGROUND, source.currentState)

        source.updateState(PlatformLifecycleState.FOREGROUND)
        assertEquals(PlatformLifecycleState.FOREGROUND, source.currentState)
        assertTrue(source.currentState.isInteractive)

        source.updateState(PlatformLifecycleState.TERMINATING)
        assertEquals(PlatformLifecycleState.TERMINATING, source.currentState)
        assertFalse(source.currentState.isInteractive)
    }
}

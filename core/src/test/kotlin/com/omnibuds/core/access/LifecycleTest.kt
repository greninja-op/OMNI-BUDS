package com.omnibuds.core.access

import com.omnibuds.core.device.DeviceFingerprint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 21: lifecycle and concurrency tests.
 */
class LifecycleTest {

    @Test
    fun `unknown to partially identified`() {
        val manager = DeviceLifecycleManager()
        val fp = DeviceFingerprint(
            manufacturerData = listOf(
                com.omnibuds.core.device.ManufacturerDataEntry(companyId = 0x004C),
            ),
        )
        val result = manager.handle(
            DeviceLifecycleEvent.IdentityEvidenceArrived("d1", fp),
        )
        assertEquals(DeviceClassification.UNKNOWN_DEVICE, result.previous.classification)
        assertEquals(DeviceClassification.PARTIALLY_IDENTIFIED, result.current.classification)
        assertTrue(result.reEvaluate)
    }

    @Test
    fun `conflicting evidence makes ambiguous and cancels`() {
        val manager = DeviceLifecycleManager()
        val fp = DeviceFingerprint(deviceClass = 2368)
        manager.handle(DeviceLifecycleEvent.IdentityEvidenceArrived("d1", fp))
        val result = manager.handle(DeviceLifecycleEvent.ConflictingEvidence("d1"))
        assertEquals(DeviceClassification.AMBIGUOUS_IDENTITY, result.current.classification)
        assertTrue(result.cancelInFlight)
        assertFalse(result.current.writeAuthorized)
    }

    @Test
    fun `stale evidence downgrades to unknown`() {
        val manager = DeviceLifecycleManager()
        val fp = DeviceFingerprint(deviceClass = 2368)
        manager.handle(DeviceLifecycleEvent.IdentityEvidenceArrived("d1", fp))
        val result = manager.handle(DeviceLifecycleEvent.EvidenceStale("d1", "timeout"))
        assertEquals(DeviceClassification.UNKNOWN_DEVICE, result.current.classification)
        assertTrue(result.cancelInFlight)
    }

    @Test
    fun `protocol invalidation revokes authorization`() {
        val manager = DeviceLifecycleManager()
        manager.handle(
            DeviceLifecycleEvent.ProtocolRegistrationChanged("d1", protocolVerified = true),
        )
        val result = manager.handle(
            DeviceLifecycleEvent.ProtocolRegistrationChanged("d1", protocolVerified = false),
        )
        assertFalse(result.current.protocolVerified)
        assertTrue(result.cancelInFlight)
    }

    @Test
    fun `version change revokes write authorization`() {
        val manager = DeviceLifecycleManager()
        val result = manager.handle(DeviceLifecycleEvent.VersionChanged("d1"))
        assertFalse(result.current.protocolVerified)
        assertTrue(result.cancelInFlight)
    }

    @Test
    fun `disconnect resets to unknown`() {
        val manager = DeviceLifecycleManager()
        val fp = DeviceFingerprint(deviceClass = 2368)
        manager.handle(DeviceLifecycleEvent.IdentityEvidenceArrived("d1", fp))
        val result = manager.handle(DeviceLifecycleEvent.Disconnected("d1"))
        assertEquals(DeviceClassification.UNKNOWN_DEVICE, result.current.classification)
        assertTrue(result.cancelInFlight)
    }

    @Test
    fun `device sessions isolated`() {
        val manager = DeviceLifecycleManager()
        val fp = DeviceFingerprint(deviceClass = 2368)
        manager.handle(DeviceLifecycleEvent.IdentityEvidenceArrived("d1", fp))
        manager.handle(DeviceLifecycleEvent.ConflictingEvidence("d2"))
        // d1 unaffected by d2's ambiguity.
        assertEquals(
            DeviceClassification.PARTIALLY_IDENTIFIED,
            manager.stateFor("d1").classification,
        )
        assertEquals(
            DeviceClassification.AMBIGUOUS_IDENTITY,
            manager.stateFor("d2").classification,
        )
    }

    @Test
    fun `out-of-order evidence converges deterministically`() {
        val manager = DeviceLifecycleManager()
        val fp = DeviceFingerprint(deviceClass = 2368)
        // Conflicting first, then identity — ambiguity is sticky.
        manager.handle(DeviceLifecycleEvent.ConflictingEvidence("d1"))
        val result = manager.handle(DeviceLifecycleEvent.IdentityEvidenceArrived("d1", fp))
        assertEquals(DeviceClassification.AMBIGUOUS_IDENTITY, result.current.classification)
    }
}

package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The container's one job is to keep "not discovered" from becoming a claim — either
 * "unsupported" or "supported" — and to combine discovery passes without losing evidence
 * (P1-DOM-002, phase-1 prompt sections 14, 24 and 53, protocol-governance PROTO-CAP-001
 * to PROTO-CAP-005).
 */
class DeviceCapabilitiesTest {

    private val anc: FeatureId = FeatureId.of("noise-control", "anc")
    private val transparency: FeatureId = FeatureId.of("noise-control", "transparency")
    private val adaptiveAnc: FeatureId = FeatureId.of("noise-control", "adaptive-anc")
    private val equalizer: FeatureId = FeatureId.of("equalization", "equalizer")
    private val battery: FeatureId = FeatureId.of("power", "battery")
    private val wearDetection: FeatureId = FeatureId.of("sensing", "wear-detection")
    private val firmwareInfo: FeatureId = FeatureId.of("firmware", "info")
    private val neverAsked: FeatureId = FeatureId.of("connectivity", "multipoint")

    private fun record(
        feature: FeatureId,
        state: CapabilityState,
        protocolId: String? = "phase-1-fixture-protocol",
        transport: TransportKind = TransportKind.GATT,
    ): FeatureCapability {
        val affordances = when (state) {
            CapabilityState.UNKNOWN,
            CapabilityState.UNSUPPORTED,
            -> false to false

            CapabilityState.READ_ONLY -> true to false

            CapabilityState.SUPPORTED_VOLATILE,
            CapabilityState.SUPPORTED_PERSISTENT,
            CapabilityState.PERSISTENCE_VERIFIED,
            -> true to true
        }
        val verification = if (state == CapabilityState.PERSISTENCE_VERIFIED) {
            VerificationLevel.PERSISTENCE_VERIFIED
        } else {
            VerificationLevel.HARDWARE_VERIFIED
        }
        return FeatureCapability(
            feature = feature,
            state = state,
            readable = affordances.first,
            writable = affordances.second,
            transport = transport,
            protocolId = protocolId,
            requiresConnection = true,
            verification = verification,
        )
    }

    /** A snapshot holding exactly one record per state, each under its own identity. */
    private fun oneOfEachState(): DeviceCapabilities = DeviceCapabilities(
        mapOf(
            adaptiveAnc to record(adaptiveAnc, CapabilityState.UNKNOWN),
            firmwareInfo to record(firmwareInfo, CapabilityState.UNSUPPORTED),
            wearDetection to record(wearDetection, CapabilityState.READ_ONLY),
            anc to record(anc, CapabilityState.SUPPORTED_VOLATILE),
            transparency to record(transparency, CapabilityState.SUPPORTED_PERSISTENT),
            equalizer to record(equalizer, CapabilityState.PERSISTENCE_VERIFIED),
        ),
    )

    @Test
    fun anAbsentFeatureIsUnknownAndNotUnsupported() {
        val discovered = DeviceCapabilities(mapOf(battery to record(battery, CapabilityState.READ_ONLY)))

        assertEquals(CapabilityState.UNKNOWN, discovered.stateOf(neverAsked))
        assertNotEquals(CapabilityState.UNSUPPORTED, discovered.stateOf(neverAsked))
        assertNull(discovered[neverAsked], "no record is the honest answer, and it is not an error")
        assertFalse(neverAsked in discovered.unsupported, "absence must not populate the unsupported set")
        assertFalse(neverAsked in discovered.controllable)
    }

    @Test
    fun theEmptyContainerAnswersEveryQuestionWithoutThrowing() {
        val empty = DeviceCapabilities.empty()

        assertEquals(CapabilityState.UNKNOWN, empty.stateOf(anc))
        assertEquals(CapabilityState.UNKNOWN, empty.stateOf(neverAsked))
        assertNull(empty[anc])
        assertTrue(empty.features.isEmpty())
        assertTrue(empty.controllable.isEmpty())
        assertTrue(empty.readable.isEmpty())
        assertTrue(empty.unknown.isEmpty())
        assertTrue(empty.unsupported.isEmpty())
        assertNotNull(empty.toString())
    }

    @Test
    fun anExplicitUnknownRecordIsKeptApartFromAnUnsupportedOne() {
        val discovered = DeviceCapabilities(
            mapOf(
                anc to record(anc, CapabilityState.UNKNOWN),
                transparency to record(transparency, CapabilityState.UNSUPPORTED),
            ),
        )

        assertEquals(CapabilityState.UNKNOWN, discovered.stateOf(anc))
        assertEquals(CapabilityState.UNSUPPORTED, discovered.stateOf(transparency))
        assertEquals(setOf(anc), discovered.unknown)
        assertEquals(setOf(transparency), discovered.unsupported)
        assertTrue(discovered.controllable.isEmpty(), "nothing established means nothing controllable")
        assertTrue(discovered.readable.isEmpty())
    }

    @Test
    fun theDerivedSetsSplitTheContainerIntoThreeDisjointGroups() {
        val discovered = oneOfEachState()
        val readableOrEstablished = discovered.readable
        val unknown = discovered.unknown
        val unsupported = discovered.unsupported

        assertEquals(setOf(adaptiveAnc), unknown)
        assertEquals(setOf(firmwareInfo), unsupported)
        assertEquals(
            setOf(wearDetection, anc, transparency, equalizer),
            readableOrEstablished,
            "read-only plus every controllable rung",
        )
        assertEquals(
            setOf(anc, transparency, equalizer),
            discovered.controllable,
            "only the three SUPPORTED rungs",
        )

        // UNKNOWN, UNSUPPORTED and "has a reading" partition the records: nothing falls in
        // two groups, nothing falls in none.
        assertTrue(unknown.intersect(unsupported).isEmpty())
        assertTrue(unknown.intersect(readableOrEstablished).isEmpty())
        assertTrue(unsupported.intersect(readableOrEstablished).isEmpty())
        assertEquals(discovered.features, unknown + unsupported + readableOrEstablished)
        assertTrue(discovered.controllable.all { it in discovered.readable })
    }

    @Test
    fun aReadOnlyFeatureIsReadableButNeverControllable() {
        val discovered = DeviceCapabilities(mapOf(battery to record(battery, CapabilityState.READ_ONLY)))

        assertEquals(setOf(battery), discovered.readable)
        assertTrue(discovered.controllable.isEmpty())
        assertFalse(assertNotNull(discovered[battery]).isControllable)
    }

    @Test
    fun withProducesANewInstanceAndLeavesTheOriginalAlone() {
        val before = DeviceCapabilities(mapOf(battery to record(battery, CapabilityState.READ_ONLY)))

        val after = before.with(anc, record(anc, CapabilityState.SUPPORTED_VOLATILE))

        assertEquals(CapabilityState.UNKNOWN, before.stateOf(anc), "the snapshot that was handed in is untouched")
        assertEquals(setOf(battery), before.features)
        assertEquals(CapabilityState.SUPPORTED_VOLATILE, after.stateOf(anc))
        assertEquals(setOf(battery, anc), after.features, "both the old record and the new one survive")
        assertNotEquals(before, after)
    }

    @Test
    fun withReplacesARecordRatherThanStackingAnother() {
        val before = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.READ_ONLY)))
        val after = before.with(anc, record(anc, CapabilityState.SUPPORTED_PERSISTENT))

        assertEquals(1, after.features.size)
        assertEquals(CapabilityState.SUPPORTED_PERSISTENT, after.stateOf(anc))
        assertEquals(CapabilityState.READ_ONLY, before.stateOf(anc))
    }

    @Test
    fun withRefusesToFileARecordAboutOneFeatureUnderAnotherIdentity() {
        val discovered = DeviceCapabilities.empty()

        assertFailsWith<IllegalArgumentException> {
            discovered.with(anc, record(transparency, CapabilityState.SUPPORTED_VOLATILE))
        }
    }

    @Test
    fun aMergeNeverDowngradesObservedPersistenceToSessionOnlySupport() {
        val verified = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.PERSISTENCE_VERIFIED)))
        val weaker = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.SUPPORTED_VOLATILE)))

        assertEquals(
            CapabilityState.PERSISTENCE_VERIFIED,
            verified.mergedWith(weaker).stateOf(anc),
            "a weaker later pass cannot erase survival that was observed",
        )
        assertEquals(
            CapabilityState.PERSISTENCE_VERIFIED,
            weaker.mergedWith(verified).stateOf(anc),
            "and the order of the two sources does not change that",
        )
    }

    @Test
    fun aMergeNeverDowngradesAnyBetterEstablishedState() {
        val ladder = listOf(
            CapabilityState.UNKNOWN,
            CapabilityState.UNSUPPORTED,
            CapabilityState.READ_ONLY,
            CapabilityState.SUPPORTED_VOLATILE,
            CapabilityState.SUPPORTED_PERSISTENT,
            CapabilityState.PERSISTENCE_VERIFIED,
        )

        ladder.forEachIndexed { strongerIndex, strongerState ->
            val stronger = DeviceCapabilities(mapOf(anc to record(anc, strongerState)))
            for (weakerIndex in 0 until strongerIndex) {
                val weakerState = ladder[weakerIndex]
                val weaker = DeviceCapabilities(mapOf(anc to record(anc, weakerState)))

                assertEquals(
                    strongerState,
                    stronger.mergedWith(weaker).stateOf(anc),
                    "$strongerState must beat $weakerState in either order",
                )
                assertEquals(
                    strongerState,
                    weaker.mergedWith(stronger).stateOf(anc),
                    "$weakerState must not erase $strongerState in either order",
                )
            }
        }
    }

    @Test
    fun anUnconcludedReDiscoveryCannotEraseWhatWasAlreadyProven() {
        val proven = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.SUPPORTED_PERSISTENT)))
        val stillLooking = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.UNKNOWN)))

        assertEquals(CapabilityState.SUPPORTED_PERSISTENT, proven.mergedWith(stillLooking).stateOf(anc))
        assertEquals(CapabilityState.SUPPORTED_PERSISTENT, stillLooking.mergedWith(proven).stateOf(anc))
    }

    @Test
    fun aPositiveEstablishmentOutranksAnUnrelatedAbsenceRecord() {
        val readOnlyDevice = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.READ_ONLY)))
        val deniedDevice = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.UNSUPPORTED)))

        assertEquals(CapabilityState.READ_ONLY, readOnlyDevice.mergedWith(deniedDevice).stateOf(anc))
        assertEquals(CapabilityState.READ_ONLY, deniedDevice.mergedWith(readOnlyDevice).stateOf(anc))
    }

    @Test
    fun aTieKeepsTheReceiversRecordWholesaleAndIsDeterministic() {
        val receiver = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.READ_ONLY, protocolId = "mine")))
        val incoming = DeviceCapabilities(
            mapOf(anc to record(anc, CapabilityState.READ_ONLY, protocolId = "theirs")),
        )

        assertEquals("mine", receiver.mergedWith(incoming)[anc]?.protocolId)
        assertEquals("theirs", incoming.mergedWith(receiver)[anc]?.protocolId)
        assertEquals(receiver, receiver.mergedWith(receiver), "merging a snapshot with itself changes nothing")
    }

    @Test
    fun aMergeCarriesForwardFeaturesNeitherSideHeldAlone() {
        val noiseSide = DeviceCapabilities(
            mapOf(
                anc to record(anc, CapabilityState.SUPPORTED_VOLATILE),
                transparency to record(transparency, CapabilityState.READ_ONLY),
            ),
        )
        val powerSide = DeviceCapabilities(
            mapOf(
                battery to record(battery, CapabilityState.READ_ONLY),
                equalizer to record(equalizer, CapabilityState.SUPPORTED_PERSISTENT),
            ),
        )

        val merged = noiseSide.mergedWith(powerSide)

        assertEquals(setOf(anc, transparency, battery, equalizer), merged.features)
        assertEquals(CapabilityState.SUPPORTED_PERSISTENT, merged.stateOf(equalizer))
        assertEquals(CapabilityState.READ_ONLY, merged.stateOf(battery))
        assertEquals(CapabilityState.UNKNOWN, merged.stateOf(neverAsked))
    }

    @Test
    fun aHandedInMapIsCopiedSoLaterEditsCannotReachTheSnapshot() {
        val source = mutableMapOf(
            anc to record(anc, CapabilityState.SUPPORTED_VOLATILE),
            transparency to record(transparency, CapabilityState.READ_ONLY),
            battery to record(battery, CapabilityState.READ_ONLY),
        )
        val snapshot = DeviceCapabilities(source)
        val featureCount = snapshot.features.size

        source.remove(battery)
        source[firmwareInfo] = record(firmwareInfo, CapabilityState.UNSUPPORTED)

        assertEquals(featureCount, snapshot.features.size, "the snapshot did not move with the caller's map")
        assertEquals(CapabilityState.READ_ONLY, snapshot.stateOf(battery))
        assertEquals(CapabilityState.UNKNOWN, snapshot.stateOf(firmwareInfo))
    }

    @Test
    fun equalityIsByContentAndNotByInstance() {
        val one = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.READ_ONLY)))
        val two = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.READ_ONLY)))
        val three = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.SUPPORTED_VOLATILE)))

        assertEquals(one, two)
        assertEquals(one.hashCode(), two.hashCode())
        assertNotEquals(one, three)
        assertNotEquals(one, DeviceCapabilities.empty())
    }

    @Test
    fun theCoreCatalogueAnsweredAgainstAnEmptySnapshotIsUnknownEverywhere() {
        val empty = DeviceCapabilities.empty()

        CoreFeature.all.forEach { feature ->
            val standing = empty.stateOf(feature)
            assertEquals(CapabilityState.UNKNOWN, standing, "${feature.qualifiedName} on an empty snapshot")
        }
        assertTrue(empty.controllable.isEmpty(), "a catalogue is not a discovery result")
    }

    /**
     * A container that only holds records about *core* identities can still be asked about
     * a vendor feature, and the answer stays `UNKNOWN`: the vendor's existence is not
     * something the container invents, and not something it denies either.
     */
    @Test
    fun anUnregisteredVendorFeatureIsUnknownRatherThanUnsupported() {
        val discovered = DeviceCapabilities(mapOf(anc to record(anc, CapabilityState.SUPPORTED_VOLATILE)))
        val sonyAdaptive = FeatureId.ofVendor("sony", "adaptive-sound-control")

        assertEquals(CapabilityState.UNKNOWN, discovered.stateOf(sonyAdaptive))
        assertTrue(discovered.unsupported.isEmpty())
    }
}

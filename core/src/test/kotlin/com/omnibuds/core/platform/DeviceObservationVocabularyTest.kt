package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.state.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Phase 3 observation vocabulary, tested as separations rather than as values.
 *
 * Every case here is a way the three axes (ADR-P3-001) could quietly collapse into one another,
 * because that collapse is the defect the separation exists to prevent: a bonded device that is at
 * home gets reported as disconnected, or an unreadable device gets reported as absent, and the user
 * is told something about their hardware that no report supported. The prompt asks for the
 * distinction in section 6; the interesting tests are the ones that would fail if someone "simplified"
 * it later.
 */
class DeviceObservationVocabularyTest {

    @Test
    fun everyPlatformStateHasATransitionRow() {
        // Exhaustiveness rather than a hand-maintained list: a new member must be given a row or the
        // build fails, which is the rule ADR-P2-005 set for the error categories.
        val all = DeviceConnectionState.entries.toSet()
        assertEquals(all, DeviceConnectionStateTransitions.coveredStates)
    }

    @Test
    fun aRepeatedStateIsIdempotentRatherThanIllegal() {
        for (state in DeviceConnectionState.entries) {
            assertTrue(
                DeviceConnectionStateTransitions.isLegal(state, state),
                "a duplicate report of $state must not be treated as a defect",
            )
            assertNull(DeviceConnectionStateTransitions.refusalReason(state, state))
        }
    }

    @Test
    fun theTwoMovesThePlatformNeverMakesAreRefusedWithAReason() {
        val refused = listOf(
            DeviceConnectionState.CONNECTING to DeviceConnectionState.DISCONNECTING,
            DeviceConnectionState.DISCONNECTING to DeviceConnectionState.CONNECTED,
        )
        for ((from, to) in refused) {
            assertFalse(DeviceConnectionStateTransitions.isLegal(from, to), "$from -> $to must be refused")
            assertNotNull(
                DeviceConnectionStateTransitions.refusalReason(from, to),
                "a refused transition has to say why, not just no",
            )
        }
    }

    @Test
    fun theFirstReportAboutAnUnknownDeviceMayBeAnyState() {
        for (to in DeviceConnectionState.entries - DeviceConnectionState.UNKNOWN) {
            assertTrue(
                DeviceConnectionStateTransitions.isLegal(DeviceConnectionState.UNKNOWN, to),
                "UNKNOWN -> $to is the ordinary case of a device's first report",
            )
        }
    }

    @Test
    fun unknownConnectionStateIsNeverReadAsDisconnected() {
        val unknown = DeviceConnectionState.UNKNOWN

        assertFalse(unknown.isConnected())
        assertFalse(unknown.isProvablyDisconnected(), "absence of a report is not a report of absence")
        assertTrue(unknown.isUnsettled())
        assertFalse(unknown.isKnown())
    }

    @Test
    fun onlyConnectedCountsAsConnectedAndOnlyDisconnectedCountsAsProvenDown() {
        assertTrue(DeviceConnectionState.CONNECTED.isConnected())
        assertFalse(DeviceConnectionState.CONNECTING.isConnected(), "a link still coming up is not up")
        assertTrue(DeviceConnectionState.DISCONNECTED.isProvablyDisconnected())
        assertFalse(DeviceConnectionState.DISCONNECTING.isProvablyDisconnected())
        assertTrue(DeviceConnectionState.DISCONNECTING.isUnsettled())
    }

    @Test
    fun bondedAndDisconnectedAreBothTrueAtOnce() {
        // The distinction prompt section 6 calls mandatory: a paired headset sitting in a drawer is
        // bonded and disconnected simultaneously, which no single flattened enum could say.
        val bond = DeviceBondState.BONDED
        val link = DeviceConnectionState.DISCONNECTED

        assertTrue(bond.isBonded())
        assertTrue(link.isProvablyDisconnected())
        assertFalse(link.isConnected())
    }

    @Test
    fun bondStateUnknownIsNeverReadAsUnpaired() {
        assertFalse(DeviceBondState.UNKNOWN.isBonded())
        assertFalse(DeviceBondState.UNKNOWN.isKnown())
        assertTrue(DeviceBondState.NONE.isKnown())
        assertFalse(DeviceBondState.NONE.isBonded())
    }

    @Test
    fun availabilityRefusalIsDistinctFromAnUnreadField() {
        assertFalse(DeviceAvailability.UNAVAILABLE.isObservable())
        assertFalse(DeviceAvailability.UNAVAILABLE.isUnread(), "a refusal was itself a report")
        assertTrue(DeviceAvailability.UNKNOWN.isUnread())
        assertTrue(DeviceAvailability.AVAILABLE.isObservable())
    }

    @Test
    fun theThreeAxesAreIndependentTypesWithNoCoercion() {
        // A compile-time separation stated as a test so that a future "just map it to
        // ConnectionState" convenience has something to break against.
        val axes: Set<Class<*>> = setOf(DeviceConnectionState::class.java, DeviceBondState::class.java, DeviceAvailability::class.java)
        assertEquals(3, axes.size, "one type cannot stand in for three facts")
        assertFalse(axes.contains(ConnectionState::class.java), "the session axis is not a platform axis")
    }

    @Test
    fun stageAnswersLifecycleAndNeverExplainsAFailure() {
        assertTrue(ObservationStage.OBSERVING.isActive())
        assertFalse(ObservationStage.NOT_STARTED.isActive())
        assertFalse(ObservationStage.STOPPED.isActive())

        val names = ObservationStage.entries.map { it.name }
        for (forbidden in listOf("PERMISSION_DENIED", "BLUETOOTH_DISABLED", "UNSUPPORTED", "FAILED")) {
            assertFalse(forbidden in names, "$forbidden is an outcome fact, not a lifecycle stage (ADR-P3-005)")
        }
    }

    @Test
    fun anEmptySuccessfulRoundIsADifferentThingFromAFailureWithNoList() {
        // Declared at the supertype so the match below is a real decision rather than something the
        // compiler can prove from the static type - which is the point of the assertion.
        val empty: ObservationRound<Nothing> =
            ObservationRound.Success(devices = emptyList(), stage = ObservationStage.OBSERVING)
        val refused: ObservationRound<Nothing> = ObservationRound.Failure(
            OmniBudsError(
                category = OmniBudsErrorCategory.PERMISSION_DENIED,
                operationId = "test.round",
            ),
        )

        // The rule prompt section 14 states in words: a device list exists solely inside a Success,
        // so "how many devices?" can never be how a consumer discovers that the round was refused.
        val listOf: (ObservationRound<Nothing>) -> List<*>? = { round ->
            when (round) {
                is ObservationRound.Success<*> -> round.devices
                is ObservationRound.Failure<*> -> null
                ObservationRound.Cancelled -> null
            }
        }

        val emptyList = assertNotNull(listOf(empty), "a successful round carries its list")
        assertEquals(0, emptyList.size, "empty means empty, and it is a real answer")
        assertNull(listOf(refused), "a failed round has no device list to misread")

        assertEquals(ObservationStage.OBSERVING, assertIs<ObservationRound.Success<Nothing>>(empty).stage)
        val failure = assertIs<ObservationRound.Failure<Nothing>>(refused)
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, failure.error.category)
    }

    @Test
    fun cancellationIsAnOutcomeCaseAndNeverAnErrorCategory() {
        val cancelled = ObservationRound.Cancelled

        assertIs<ObservationRound.Cancelled>(cancelled)
        for (category in OmniBudsErrorCategory.entries) {
            val name = category.name
            assertFalse(
                name.contains("CANCEL"),
                "ADR-P1-004 keeps cancellation out of the category set; found $name",
            )
        }
    }
}

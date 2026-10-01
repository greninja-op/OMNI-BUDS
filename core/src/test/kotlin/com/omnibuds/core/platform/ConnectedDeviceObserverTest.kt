package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reconciliation engine, tested against scripted sources.
 *
 * Every case here is one of the hard cases prompt sections 9 through 12 names, and each is tested
 * through the seam rather than on a handset because that is the point of the engine living in `:core`
 * (ADR-P3-003): losing a transition that arrived during a snapshot, welding two earbuds that share a
 * label, turning a refused permission into "nobody is connected", or claiming a headset was switched off
 * because the phone's own radio went quiet are all defects a scripted source can prove without a radio,
 * a pairing or a user.
 *
 * No case here makes any claim about real hardware, and none of them can: what a particular handset
 * announces, and when, is device-level knowledge and is listed as UNVERIFIED in the research.
 */
// advanceUntilIdle is the only way to say "the concurrent collector has started" without sleeping,
// and it is still marked experimental in the coroutines version this project pins.
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectedDeviceObserverTest {

    private val adapterQuiet = FakeAdapterStateSource()

    @Test
    fun theThreeAxesOfOneDeviceMoveIndependently() = runTest {
        // The separation ADR-P3-001 exists for, asserted as behaviour: a paired headset in a closed case
        // is bonded, disconnected and perfectly observable at the same time, and a pairing announcement
        // must not move either of the other two.
        val pairedAndDown = reported(
            ADDRESS_LEFT,
            link = DeviceConnectionState.DISCONNECTED,
            bond = DeviceBondState.BONDED,
        )
        val source = FakeConnectedDeviceSource(
            success(pairedAndDown),
            eventScript = listOf(bondEvent(ADDRESS_LEFT, DeviceBondState.NONE)),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        assertEquals(2, rounds.size)
        val before = assertIs<ObservationRound.Success<DeviceObservation>>(rounds[0]).devices.single()
        assertTrue(before.bond.isBonded())
        assertTrue(before.link.isProvablyDisconnected())
        assertTrue(before.availability.isObservable())

        val after = assertIs<ObservationRound.Success<DeviceObservation>>(rounds[1]).devices.single()
        assertFalse(after.bond.isBonded(), "the pairing announcement was accepted")
        assertTrue(after.link.isProvablyDisconnected(), "and it said nothing whatever about the link")
        assertTrue(after.availability.isObservable())
        assertEquals(ObservationArrival.EVENT, after.arrival)
    }

    @Test
    fun aTransitionAnnouncedWhileTheSnapshotWasInFlightSurvivesIt() = runTest {
        // Prompt section 9. The platform promises no order between an announcement and the read that
        // duplicates it, so the announcement has to be applied *after* the union is folded. An engine
        // that registered late, or that let the snapshot replace the projection wholesale, ends up here
        // reporting a connected device that had already dropped.
        val connected = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val source = FakeConnectedDeviceSource(
            success(connected),
            eventScript = listOf(linkEvent(ADDRESS_LEFT, ObservedProfile.A2DP, DeviceConnectionState.DISCONNECTED)),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        assertEquals(2, rounds.size, "the read, then the announcement it had to keep")
        assertEquals(
            DeviceConnectionState.CONNECTED,
            rounds.stateOf(ADDRESS_LEFT, 0),
            "the union was truthful at the moment it read",
        )
        assertEquals(
            DeviceConnectionState.DISCONNECTED,
            rounds.stateOf(ADDRESS_LEFT, 1),
            "and the later announcement survived the earlier read",
        )
        assertEquals(ObservationArrival.EVENT, rounds.recordOf(ADDRESS_LEFT, 1)?.arrival)
    }

    @Test
    fun aDeviceThatAppearedWhileTheSnapshotWasInFlightIsNotLost() = runTest {
        // The mirror case: a union that saw nobody, and an announcement that arrived while it was being
        // read. Emptying the projection on the strength of the read would drop a device the platform had
        // already named.
        val source = FakeConnectedDeviceSource(
            success(),
            eventScript = listOf(
                linkEvent(ADDRESS_RIGHT, ObservedProfile.LE_AUDIO, DeviceConnectionState.CONNECTED, at = 4_000L),
            ),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        assertEquals(2, rounds.size)
        assertTrue(assertIs<ObservationRound.Success<DeviceObservation>>(rounds[0]).devices.isEmpty())
        val arrived = assertNotNull(rounds.recordOf(ADDRESS_RIGHT, 1))
        assertEquals(DeviceConnectionState.CONNECTED, arrived.link)
        assertEquals(setOf(ObservedProfile.LE_AUDIO), arrived.observedProfiles)
        assertEquals(4_000L, arrived.observedAtEpochMillis, "the platform's own reading, not the observer's clock")
    }

    @Test
    fun twoDevicesCarryingTheSameNameRemainTwoDevices() = runTest {
        // Prompt section 12. Identical labels are ordinary - one user, two of the same product - and a
        // merge on the name silently welds them into one device whose state is a compromise between two.
        val left = reported(ADDRESS_LEFT, name = LABEL, profiles = setOf(ObservedProfile.A2DP))
        val right = reported(ADDRESS_RIGHT, name = LABEL, profiles = setOf(ObservedProfile.A2DP))
        val source = FakeConnectedDeviceSource(
            success(left, right),
            eventScript = listOf(linkEvent(ADDRESS_LEFT, ObservedProfile.A2DP, DeviceConnectionState.DISCONNECTED)),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        val before = assertIs<ObservationRound.Success<DeviceObservation>>(rounds[0]).devices
        assertEquals(2, before.size)
        assertEquals(1, before.mapNotNull { it.displayName }.distinct().size, "both reports carried one label")

        val after = assertIs<ObservationRound.Success<DeviceObservation>>(rounds[1]).devices
        assertEquals(2, after.size, "one of them dropped and they are still two devices")
        assertEquals(DeviceConnectionState.DISCONNECTED, after.stateOfKey(ADDRESS_LEFT))
        assertEquals(DeviceConnectionState.CONNECTED, after.stateOfKey(ADDRESS_RIGHT))
    }

    @Test
    fun twoProfilesReportingOneDeviceProduceOneRecordNamingBoth() = runTest {
        // Prompt section 7's observedProfiles and section 12's no-duplication rule in one case: the union
        // lists one address twice, and the projection has to answer one device with two witnesses.
        val viaAudio = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP), name = LABEL)
        val viaCall = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.HEADSET))
        val source = FakeConnectedDeviceSource(success(viaAudio, viaCall))
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        val record = assertIs<ObservationRound.Success<DeviceObservation>>(rounds.single()).devices.single()
        assertEquals(setOf(ObservedProfile.A2DP, ObservedProfile.HEADSET), record.observedProfiles)
        assertEquals(LABEL, record.displayName, "one profile gave no name, and the merge filled the gap")
    }

    @Test
    fun oneServiceDroppingDoesNotDisconnectADeviceAnotherStillHolds() = runTest {
        // The reason the profile set has to survive a merge. A device held by two services that loses one
        // is still connected, and a projection that read the announcement as a device-level event would
        // tell the user their audio had died while it was still playing.
        val held = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP, ObservedProfile.HEADSET))
        val source = FakeConnectedDeviceSource(
            success(held),
            eventScript = listOf(linkEvent(ADDRESS_LEFT, ObservedProfile.A2DP, DeviceConnectionState.DISCONNECTED)),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        val after = assertNotNull(rounds.recordOf(ADDRESS_LEFT, rounds.size - 1))
        assertEquals(DeviceConnectionState.CONNECTED, after.link)
        assertEquals(setOf(ObservedProfile.HEADSET), after.observedProfiles)
    }

    @Test
    fun aLinkLayerDisconnectEndsEveryProfileThePlatformHadReported() = runTest {
        // The one inference this engine makes, and it is about the transport rather than the device: a
        // service connection is carried by the link, so the announcement that names no service at all
        // ends every claim the profiles were making. It still says nothing about the device.
        val held = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP, ObservedProfile.LE_AUDIO))
        val source = FakeConnectedDeviceSource(
            success(held),
            eventScript = listOf(linkEvent(ADDRESS_LEFT, profile = null, link = DeviceConnectionState.DISCONNECTED)),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        val after = assertNotNull(rounds.recordOf(ADDRESS_LEFT, 1))
        assertEquals(emptySet<ObservedProfile>(), after.observedProfiles)
        assertEquals(DeviceConnectionState.DISCONNECTED, after.link)
    }

    @Test
    fun reportsThePlatformGaveNoKeyForAreNeverMerged() = runTest {
        // ADR-P3-010's consequence inside the engine: two unkeyed reports are two records, and an unkeyed
        // announcement cannot be attributed to either of them, so it is refused rather than adopted.
        val nameless = reported(
            null,
            name = LABEL,
            profiles = setOf(ObservedProfile.A2DP),
        )
        val source = FakeConnectedDeviceSource(
            success(nameless, nameless),
            eventScript = listOf(linkEvent(null, ObservedProfile.A2DP, DeviceConnectionState.DISCONNECTED)),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        assertEquals(
            2,
            assertIs<ObservationRound.Success<DeviceObservation>>(rounds.first()).devices.size,
            "two reports of nothing are not one report of one thing",
        )
        assertEquals(1, rounds.size, "the unattributable announcement published nothing")
        val refusal = observer.snapshot.value.refusedTransitions.single()
        assertEquals(RefusalKind.UNATTRIBUTABLE_REPORT, refusal.kind)
        assertTrue(refusal.reason.isNotBlank())
        assertTrue(refusal.key.isUnattributable())
    }

    @Test
    fun aDisconnectThePlatformAnnouncesThreeTimesIsAppliedOnce() = runTest {
        // Prompt section 11's idempotence requirement, and the dedupe that keeps a UI from animating on a
        // broadcast that arrived twice. Note what the record does not gain either: no newer timestamp,
        // because a clock that advances while nothing changed reports a round that did not happen.
        val connected = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val drops = List(3) {
            linkEvent(ADDRESS_LEFT, ObservedProfile.A2DP, DeviceConnectionState.DISCONNECTED, at = 1_000L)
        }
        val source = FakeConnectedDeviceSource(success(connected), eventScript = drops)
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        assertEquals(2, rounds.size, "one for the read and one for the transition")
        val settled = assertNotNull(rounds.recordOf(ADDRESS_LEFT, 1))
        assertEquals(1_000L, settled.observedAtEpochMillis)
        assertEquals(1, source.snapshotCalls, "a duplicate announcement earns no re-read")
    }

    @Test
    fun anAdapterGoingQuietMarksDevicesUnobservableAndNeverPoweredOff() = runTest {
        // Prompt section 11's second half, and the case this file exists to keep honest. A phone's own
        // radio stopping is evidence about the phone. DISCONNECTED would be a claim about the headset's
        // link and UNKNOWN is not, so the record ends up unobservable with its link unread - and the
        // round carries no device list, so nobody can read a count out of it.
        val connected = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val source = FakeConnectedDeviceSource(success(connected))
        val adapter = FakeAdapterStateSource(eventScript = listOf(BluetoothAdapterState.DISABLED))
        val observer = ConnectedDeviceObserver(source.asSource(), adapter.asSource())

        val rounds = observer.observe().toList()

        val failure = assertIs<ObservationRound.Failure<DeviceObservation>>(rounds.last())
        assertEquals(OmniBudsErrorCategory.BLUETOOTH_DISABLED, failure.error.category)
        assertTrue(
            failure.error.detail.orEmpty().contains("not evidence about the power state"),
            "the refusal has to say what it does not claim: ${failure.error.detail}",
        )
        val marked = assertNotNull(observer.snapshot.value.records.single())
        assertEquals(
            DeviceConnectionState.UNKNOWN,
            marked.link,
            "not DISCONNECTED: nothing reported that the headset stopped",
        )
        assertEquals(DeviceAvailability.UNAVAILABLE, marked.availability)
        assertFalse(marked.availability.isUnread(), "a refusal is itself a report about our view")
        assertFalse(marked.isReportedConnected)
        assertTrue(observer.snapshot.value.connectedDevices.isEmpty())
    }

    @Test
    fun theAdapterComingBackReReadsTheUnionInsteadOfAssumingAnything() = runTest {
        // The research's architecture in one case: a snapshot on start, deltas while running, and a
        // re-snapshot on an adapter edge. No timer and no polling anywhere - the second read is caused by
        // an announcement, and the device that had been marked unreadable is restated as a report.
        val connected = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val source = FakeConnectedDeviceSource(success(connected), success())
        val adapter = FakeAdapterStateSource(
            eventScript = listOf(BluetoothAdapterState.DISABLED, BluetoothAdapterState.ENABLED),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapter.asSource())

        val rounds = observer.observe().toList()

        assertEquals(3, rounds.size)
        assertIs<ObservationRound.Failure<DeviceObservation>>(rounds[1])
        val restored = assertIs<ObservationRound.Success<DeviceObservation>>(rounds[2]).devices.single()
        assertEquals(2, source.snapshotCalls)
        assertEquals(DeviceConnectionState.DISCONNECTED, restored.link, "the re-read looked, and did not list it")
        assertTrue(restored.availability.isObservable(), "and a completed round means the platform will speak again")
    }

    @Test
    fun aTransitionThePlatformCannotReportIsRefusedWithItsReasonAndTheStateIsKept() = runTest {
        // Prompt section 11's "do not swallow". DISCONNECTING becoming CONNECTED is not a move the stack
        // makes, so the engine holds the announcement against the record, writes the table's own reason
        // into the projection that produced it, and does not adopt a state nobody reported.
        val dropping = reported(ADDRESS_LEFT, link = DeviceConnectionState.DISCONNECTING)
        val source = FakeConnectedDeviceSource(
            success(dropping),
            eventScript = listOf(linkEvent(ADDRESS_LEFT, ObservedProfile.A2DP, DeviceConnectionState.CONNECTED)),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        assertEquals(1, rounds.size, "a refused announcement republished nothing")
        val refusal = observer.snapshot.value.refusedTransitions.single()
        assertEquals(RefusalKind.IMPOSSIBLE_TRANSITION, refusal.kind)
        assertEquals(DeviceConnectionState.DISCONNECTING, refusal.retained)
        assertEquals(DeviceConnectionState.CONNECTED, refusal.attempted)
        assertEquals(
            DeviceConnectionStateTransitions.refusalReason(
                DeviceConnectionState.DISCONNECTING,
                DeviceConnectionState.CONNECTED,
            ),
            refusal.reason,
        )
        assertTrue(observer.snapshot.value.records.single().link.isUnsettled(), "and the state was kept")
    }

    @Test
    fun aRefusedPermissionArrivesAsAFailureCarryingNoDeviceList() = runTest {
        // ADR-P3-009, tested where it is testable: the source settles standing before it enumerates, and
        // the only shape that can express "we were not allowed to look" is a round with no list in it.
        // The engine then has nothing to misread as an empty room.
        val refusal = OmniBudsError(
            category = OmniBudsErrorCategory.PERMISSION_DENIED,
            operationId = "fake-device-source.snapshot",
            detail = "the standing check refused the enumeration before it began",
        )
        val source = FakeConnectedDeviceSource(ObservationRound.Failure(refusal))
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        val failure = assertIs<ObservationRound.Failure<DeviceObservation>>(rounds.single())
        assertEquals(refusal, failure.error, "the refusal is forwarded as the source stated it, not relabelled")
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, failure.error.category)
        assertTrue(observer.snapshot.value.records.isEmpty(), "no device list was invented to fill the gap")
        assertFalse(observer.snapshot.value.isUnionComplete, "and the projection never claims to have looked")
    }

    @Test
    fun anEmptySuccessfulRoundIsADifferentFactFromARefusedOne() = runTest {
        // The same engine and the same scripted sequence, two different published values - which is what
        // prompt section 14's rule has to mean in code rather than in prose.
        val emptyRoom = ConnectedDeviceObserver(
            FakeConnectedDeviceSource(success()).asSource(),
            adapterQuiet.asSource(),
        )
        val round = emptyRoom.observe().toList().single()

        val success = assertIs<ObservationRound.Success<DeviceObservation>>(round)
        assertTrue(success.devices.isEmpty())
        assertEquals(ObservationStage.OBSERVING, success.stage, "the engine restates the stage, not the source")
        assertTrue(emptyRoom.snapshot.value.isUnionComplete, "every profile answered, so empty is a finding")

        val refused = ConnectedDeviceObserver(
            FakeConnectedDeviceSource(permissionDenied()).asSource(),
            adapterQuiet.asSource(),
        ).observe().toList().single()
        assertIs<ObservationRound.Failure<DeviceObservation>>(refused)
    }

    @Test
    fun aUnionWhereNothingAnsweredCannotReportAnEmptyRoom() = runTest {
        // ADR-P3-008's rule taken literally: a profile whose binding never arrives is unobservable, and a
        // snapshot assembled out of nothing but unobservable profiles has observed nothing about anybody.
        // Forwarding its empty list would be the exact confusion ADR-P3-009 exists to close.
        val source = FakeConnectedDeviceSource(
            success(),
            support = OperationOutcome.Success(noProfilesAnswerable()),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        val failure = assertIs<ObservationRound.Failure<DeviceObservation>>(rounds.single())
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, failure.error.category)
        assertEquals(
            ObservedProfile.enumerationUnion.toSet(),
            observer.snapshot.value.unansweredProfiles,
            "the projection says which profiles it could not hear",
        )
        assertFalse(observer.snapshot.value.isUnionComplete)
        assertEquals(ObservationStage.STOPPED, observer.stage, "a refused round is not a broken lifecycle")
    }

    @Test
    fun aSilentProfileCannotWithdrawALinkItNeverRestated() = runTest {
        // The partial union, which is the ordinary case on a handset: LE Audio declines to bind, and its
        // silence about a device is not a statement about that device. The same second round, run against
        // a union where everything answered, does disconnect it - the difference is entirely in who spoke.
        val held = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val partial = FakeConnectedDeviceSource(
            success(held),
            success(),
            eventScript = listOf(linkEvent(ADDRESS_LEFT, ObservedProfile.LE_AUDIO, DeviceConnectionState.CONNECTED)),
            support = OperationOutcome.Success(profilesAnswerable(setOf(ObservedProfile.A2DP))),
        )
        val partialObserver = ConnectedDeviceObserver(partial.asSource(), adapterQuiet.asSource())
        partialObserver.observe().toList()
        assertIs<ObservationRound.Success<DeviceObservation>>(partialObserver.refresh())

        val stillHeld = assertNotNull(partialObserver.snapshot.value.records.single())
        assertEquals(DeviceConnectionState.CONNECTED, stillHeld.link, "only A2DP answered, and LE Audio stays unread")
        assertEquals(setOf(ObservedProfile.LE_AUDIO), stillHeld.observedProfiles)

        val complete = FakeConnectedDeviceSource(
            success(held),
            success(),
            eventScript = listOf(linkEvent(ADDRESS_LEFT, ObservedProfile.LE_AUDIO, DeviceConnectionState.CONNECTED)),
        )
        val completeObserver = ConnectedDeviceObserver(complete.asSource(), adapterQuiet.asSource())
        completeObserver.observe().toList()
        assertIs<ObservationRound.Success<DeviceObservation>>(completeObserver.refresh())

        val accountedFor = assertNotNull(completeObserver.snapshot.value.records.single())
        assertEquals(
            DeviceConnectionState.DISCONNECTED,
            accountedFor.link,
            "every profile answered, so the union genuinely found nobody",
        )
    }

    @Test
    fun aSecondConcurrentObserverIsRefusedInsteadOfRegisteringTwice() = runTest {
        val source = FakeConnectedDeviceSource(success(), keepEventsOpen = true)
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        // Attempted from inside the live collection, so the slot is demonstrably held rather than racing
        // the scheduler: the first observation releases it only in its finally block.
        var slotHeldDuringAttempt = false
        val secondAttempt = observer.observe()
            .map {
                slotHeldDuringAttempt = observer.isObserving
                observer.observe().first()
            }
            .first()

        val failure = assertIs<ObservationRound.Failure<DeviceObservation>>(secondAttempt)
        assertTrue(slotHeldDuringAttempt, "the first observation was running when the second was refused")
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, failure.error.category)
        assertEquals(1, source.openCalls, "the platform must be registered with exactly once")
    }

    @Test
    fun cancellationReleasesEveryRegistrationThisObservationMade() = runTest {
        // Two registrations now rather than one: the announcement receiver and the adapter stream. A
        // teardown that releases whichever one it reaches first is a leak with a green test beside it.
        val source = FakeConnectedDeviceSource(success(), keepEventsOpen = true)
        val adapter = FakeAdapterStateSource(keepOpen = true)
        val observer = ConnectedDeviceObserver(source.asSource(), adapter.asSource())

        val collected = mutableListOf<ObservationRound<DeviceObservation>>()
        val collector = launch { observer.observe().toList(collected) }
        advanceUntilIdle()
        collector.cancelAndJoin()

        assertEquals(1, collected.size, "the snapshot was published before the collector was called off")
        assertEquals(1, source.disposeCalls)
        assertEquals(1, adapter.disposeCalls)
        assertEquals(0, source.activeRegistrations)
        assertEquals(0, adapter.activeRegistrations)
        assertFalse(observer.isObserving)
        assertNull(observer.teardownProblem)
        assertEquals(ObservationStage.STOPPED, observer.stage)
    }

    @Test
    fun aTeardownThatThrowsIsRecordedInsteadOfDisappearing() = runTest {
        val source = FakeConnectedDeviceSource(success())
        source.failDisposal = true
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        observer.observe().toList()

        val problem = assertNotNull(observer.teardownProblem)
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, problem.category)
        assertTrue(problem.detail.orEmpty().contains("did not release"))
        assertFalse(observer.isObserving, "a failed teardown still releases the slot, or nothing ever observes again")
    }

    @Test
    fun aRefusedRegistrationOpensNothingAndReportsWhy() = runTest {
        val source = FakeConnectedDeviceSource(success())
        source.failOpen = true
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        val failure = assertIs<ObservationRound.Failure<DeviceObservation>>(rounds.single())
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, failure.error.category)
        assertEquals(0, source.activeRegistrations)
        assertEquals(0, source.supportCalls, "nothing was enumerated, because nothing could be observed")
    }

    @Test
    fun aCancelledRoundIsForwardedUnchangedAndStillTearsDown() = runTest {
        val source = FakeConnectedDeviceSource(ObservationRound.Cancelled)
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        // ADR-P2-018: cancellation is an outcome case, not a fault, and it is forwarded as itself. The
        // registration already opened is still released, because "the read was called off" has never once
        // meant "the receiver may stay".
        assertEquals(1, rounds.size)
        assertIs<ObservationRound.Cancelled>(rounds.single())
        assertEquals(1, source.disposeCalls)
        assertEquals(0, source.activeRegistrations)
        assertFalse(observer.isObserving)
    }

    @Test
    fun aCancelledRegistrationEndsTheObservationWithoutInventingAFault() = runTest {
        val source = FakeConnectedDeviceSource(success(), openCancels = true)
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        assertIs<ObservationRound.Cancelled>(rounds.single())
        assertEquals(0, source.snapshotCalls, "nothing was enumerated, because nothing was registered")
        assertEquals(0, source.activeRegistrations)
    }

    @Test
    fun lifecycleIsReportedAsAStageAndNeverAsAFailureShape() = runTest {
        val source = FakeConnectedDeviceSource(success())
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        assertEquals(ObservationStage.NOT_STARTED, observer.stage)
        observer.observe().toList()

        // An ended observation is a thing that was deliberately finished, not a fault (ADR-P3-005), and
        // the only lifecycle answer a consumer may ask is the stage.
        assertEquals(ObservationStage.STOPPED, observer.stage)
        assertEquals(0, source.activeRegistrations)
        assertEquals(0, adapterQuiet.activeRegistrations)
    }

    @Test
    fun aTimestampIsRecordedOnlyWhenThePlatformSuppliesOne() = runTest {
        val stamped = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val clock = TimeProvider { 1_700_000_000_000L }
        val timed = ConnectedDeviceObserver(
            FakeConnectedDeviceSource(success(stamped)).asSource(),
            adapterQuiet.asSource(),
            time = clock,
        )
        val untimed = ConnectedDeviceObserver(
            FakeConnectedDeviceSource(success(stamped)).asSource(),
            adapterQuiet.asSource(),
        )

        timed.observe().toList()
        assertEquals(1_700_000_000_000L, assertNotNull(timed.snapshot.value.observedAtEpochMillis))

        untimed.observe().toList()
        // No clock reading is not the epoch; it is unknown time (ADR-P1-012).
        assertNull(untimed.snapshot.value.observedAtEpochMillis)
        // And a record is never backdated from the observer's clock, because the platform gave no reading
        // for the device: an invented time would be a claim about when a link was seen.
        assertNull(untimed.snapshot.value.records.single().observedAtEpochMillis)
    }

    @Test
    fun aRoundThatDidNotAnswerLeavesTheLastProjectionIntact() = runTest {
        // ADR-P3-005's consequence for state rather than for messages: a refusal has no authority over
        // what was already reported, so the projection keeps the devices the platform last named instead
        // of being emptied by a round that saw nothing.
        val connected = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val observer = ConnectedDeviceObserver(
            FakeConnectedDeviceSource(success(connected), permissionDenied()).asSource(),
            adapterQuiet.asSource(),
        )

        assertIs<ObservationRound.Success<DeviceObservation>>(observer.observe().toList().single())
        assertIs<ObservationRound.Failure<DeviceObservation>>(observer.refresh())

        assertEquals(1, observer.snapshot.value.records.size)
        assertEquals(DeviceConnectionState.CONNECTED, observer.snapshot.value.records.single().link)
    }

    @Test
    fun refreshWorksWithoutTheSlotAndDoesNotRegisterAnything() = runTest {
        // A one-shot re-read must not reserve a registration, for the same reason Phase 2's readOnce does
        // not: a single read needs no teardown it cannot prove.
        val connected = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val source = FakeConnectedDeviceSource(success(connected))
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val round = assertIs<ObservationRound.Success<DeviceObservation>>(observer.refresh())

        assertEquals(1, round.devices.size)
        assertEquals(1, source.snapshotCalls)
        assertEquals(0, source.openCalls)
        assertEquals(ObservationStage.NOT_STARTED, observer.stage)
        assertFalse(observer.isObserving)
    }

    @Test
    fun anUnavailableSourceRefusesEveryQuestionAndAnswersNoneWithAnEmptyList() = runTest {
        val observer = ConnectedDeviceObserver(
            ConnectedDeviceSource.unavailable(),
            AdapterStateSource.unavailable(),
        )

        val rounds = observer.observe().toList()

        // The composition root with no device mechanism is honest about having none: the first answer is
        // a refusal, and no empty device list is ever produced to be mistaken for a quiet room.
        val failure = assertIs<ObservationRound.Failure<DeviceObservation>>(rounds.single())
        assertEquals(OmniBudsErrorCategory.ADAPTER_UNAVAILABLE, failure.error.category)
        assertEquals(0, observer.snapshot.value.records.size)
    }

    @Test
    fun theEnumerationUnionCarriesItsReasonAndItsIntroductionLevel() {
        // ADR-P3-008 makes this list a maintained decision, and the per-entry reason is the only thing
        // that stops a later phase pruning an entry whose job is to be present when a device appears.
        assertEquals(ObservedProfile.entries.toSet(), ObservedProfile.enumerationUnion.toSet())
        val names = ObservedProfile.entries.map { it.technicalName }
        assertEquals(names.size, names.toSet().size, "two profiles cannot share one diagnostic name")

        for (profile in ObservedProfile.entries) {
            assertTrue(profile.reason.isNotBlank(), "${profile.name} must say why it is in the union")
            assertTrue(profile.introducedApiLevel > 0, "${profile.name} must say when it became askable")
            assertEquals(
                ApiAvailability.UNKNOWN,
                profile.availabilityAt(null),
                "an unknown device SDK is not an absent one",
            )
        }

        // Availability is the OS question and nothing more: LE Audio exists at 33 and not at 31, which is
        // stated by the number the SDK carries rather than by a guess.
        assertEquals(ApiAvailability.UNAVAILABLE, ObservedProfile.LE_AUDIO.availabilityAt(31))
        assertEquals(ApiAvailability.AVAILABLE, ObservedProfile.LE_AUDIO.availabilityAt(33))
        assertEquals(ApiAvailability.AVAILABLE, ObservedProfile.A2DP.availabilityAt(26))
    }

    @Test
    fun aBlankNameFromThePlatformIsNoNameAtAll() {
        // Prompt section 6's "unknown stays unknown" applied to the one field a UI is most tempted to fill
        // in: a blank is demoted to null on the way in, so a padded label can neither win a field nor be
        // read as a device that named itself.
        val blank = DeviceObservation.reported(
            key = keyOf(ADDRESS_LEFT),
            link = DeviceConnectionState.CONNECTED,
            bond = DeviceBondState.UNKNOWN,
            availability = DeviceAvailability.AVAILABLE,
            displayName = "   ",
        )
        val named = reported(ADDRESS_LEFT, name = LABEL)

        assertNull(blank.displayName)
        assertFalse(blank.hasReportedName)
        assertEquals(LABEL, blank.mergedWith(named).displayName, "a later report fills what nobody knew")
        assertEquals(LABEL, named.mergedWith(blank).displayName, "and a blank one withdraws nothing")
    }

    @Test
    fun aMergeTheKeyCannotAttributeIsRefusedRatherThanGuessed() {
        // The join rule with a compile-time-ish guard behind it: `mergedWith` is the only merge the model
        // offers, and it refuses both a different address and a shared absence, because the second is the
        // one a careless caller would read as "these are the same unkeyed device".
        val left = reported(ADDRESS_LEFT)
        val right = reported(ADDRESS_RIGHT)
        val unkeyed = reported(null)

        val differentDevice = assertFailsWith<IllegalArgumentException> { left.mergedWith(right) }
        val bothUnkeyed = assertFailsWith<IllegalArgumentException> { unkeyed.mergedWith(unkeyed) }
        assertTrue(differentDevice.message!!.contains("must not be merged"))
        assertTrue(bothUnkeyed.message!!.contains("must not be merged"), "two absences are not one device")

        val viaAudio = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val viaCall = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.HEADSET))
        assertEquals(
            setOf(ObservedProfile.A2DP, ObservedProfile.HEADSET),
            viaAudio.mergedWith(viaCall).observedProfiles,
            "the join the rule does allow still merges",
        )
    }

    private fun success(vararg devices: DeviceObservation): ObservationRound<DeviceObservation> =
        // A source states no lifecycle: whatever stage a round arrives with, the observer restates its own.
        ObservationRound.Success(devices = devices.toList(), stage = ObservationStage.NOT_STARTED)

    private fun permissionDenied(): ObservationRound<DeviceObservation> = ObservationRound.Failure(
        OmniBudsError(
            category = OmniBudsErrorCategory.PERMISSION_DENIED,
            operationId = "fake-device-source.snapshot",
            detail = "the standing check refused the enumeration before it began",
        ),
    )

    private fun reported(
        address: String?,
        link: DeviceConnectionState = DeviceConnectionState.CONNECTED,
        bond: DeviceBondState = DeviceBondState.UNKNOWN,
        availability: DeviceAvailability = DeviceAvailability.AVAILABLE,
        profiles: Set<ObservedProfile> = emptySet(),
        name: String? = null,
    ): DeviceObservation = DeviceObservation.reported(
        key = keyOf(address),
        link = link,
        bond = bond,
        availability = availability,
        observedProfiles = profiles,
        displayName = name,
    )

    private fun List<ObservationRound<DeviceObservation>>.recordOf(
        address: String?,
        index: Int,
    ): DeviceObservation? = (this[index] as? ObservationRound.Success)?.devices?.firstOrNull {
        it.key.joinableWith(keyOf(address))
    }

    private fun List<ObservationRound<DeviceObservation>>.stateOf(
        address: String?,
        index: Int,
    ): DeviceConnectionState? = recordOf(address, index)?.link

    private companion object {
        const val ADDRESS_LEFT = "00:11:22:AA:BB:01"
        const val ADDRESS_RIGHT = "00:11:22:AA:BB:02"
        const val LABEL = "OmniBuds Air"
    }
}

private fun keyOf(address: String?): DeviceObservationKey = DeviceObservationKey.ofReportedAddress(address)

private fun List<DeviceObservation>.stateOfKey(address: String): DeviceConnectionState? =
    firstOrNull { it.key.joinableWith(keyOf(address)) }?.link

private fun linkEvent(
    address: String?,
    profile: ObservedProfile?,
    link: DeviceConnectionState,
    at: Long? = null,
): DeviceConnectionEvent = DeviceConnectionEvent.LinkChanged(
    key = keyOf(address),
    profile = profile,
    link = link,
    observedAtEpochMillis = at,
)

private fun bondEvent(
    address: String?,
    bond: DeviceBondState,
    at: Long? = null,
): DeviceConnectionEvent = DeviceConnectionEvent.BondChanged(
    key = keyOf(address),
    bond = bond,
    observedAtEpochMillis = at,
)

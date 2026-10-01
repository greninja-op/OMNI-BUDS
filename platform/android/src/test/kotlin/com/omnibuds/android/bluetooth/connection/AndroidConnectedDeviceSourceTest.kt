package com.omnibuds.android.bluetooth.connection

import com.omnibuds.android.bluetooth.capability.ApiLevelProvider
import com.omnibuds.android.bluetooth.capability.TargetSdkProvider
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.android.bluetooth.permission.PermissionRequestLedger
import com.omnibuds.android.bluetooth.permission.PermissionStandingReader
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.ConnectedDeviceEventChannel
import com.omnibuds.core.platform.DeviceAvailability
import com.omnibuds.core.platform.DeviceBondState
import com.omnibuds.core.platform.DeviceConnectionEvent
import com.omnibuds.core.platform.DeviceConnectionState
import com.omnibuds.core.platform.DeviceObservation
import com.omnibuds.core.platform.DeviceObservationKey
import com.omnibuds.core.platform.ObservationArrival
import com.omnibuds.core.platform.ObservationRound
import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.platform.ProfileObservationSupport
import com.omnibuds.core.platform.ProfileSupportReport
import com.omnibuds.core.platform.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 3's Android device-observation half, tested against a scripted handle and no radio.
 *
 * The tests are grouped by the claim they protect rather than by the method they call, because the claim
 * that matters here is a sequence. ADR-P3-009's rule is not "check permission somewhere", it is "check it
 * *before* asking the platform anything", and the only way to hold a line drawn in order of operations is
 * to observe the calls that did not happen. Each refusal test therefore asserts on the fake's request logs
 * as well as on the returned value: an empty log is the evidence.
 *
 * Nothing in this file is evidence about a handset. The framework calls are the subject of the instrumented
 * suite, which ADR-P3-014 keeps authored, unrun and unclaimed for this phase.
 */
class AndroidConnectedDeviceSourceTest {

    // ---- The ordering rule: standing first, in all three entry points -------------------------------

    @Test
    fun aRoundRefusesBeforeAskingThePlatformAnythingWhenStandingIsNotAGrant() = runTest {
        // Three standings that are not a grant, each reached the way the platform would produce it: a
        // refusal with a request on record, a refusal with none, and no answer at all. ADR-P3-009 names
        // the third case explicitly, because refusing to look is honest where reporting zero is not.
        val cases = listOf(
            NonGrant(PermissionStandingReader { false }, requestedBefore = true),
            NonGrant(PermissionStandingReader { false }, requestedBefore = false),
            NonGrant(PermissionStandingReader { null }, requestedBefore = true),
        )
        for (case in cases) {
            val handle = FakeConnectedDeviceHandle().answeringTheWholeUnion(LEFT)
            val source = source(handle, reader = case.reader, requestedBefore = case.requestedBefore)

            val round = source.snapshot()

            assertIs<ObservationRound.Failure<DeviceObservation>>(round, "a non-granted standing must refuse the round")
            assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, round.error.category)
            assertEquals(
                emptyList(),
                handle.enumerationRequests,
                "a refused round must not have enumerated a single profile",
            )
            assertEquals(emptyList(), handle.answerabilityRequests, "nor asked what could answer")
        }
    }

    @Test
    fun theSupportQuestionRefusesBeforeReadingAnyBindingState() = runTest {
        val handle = FakeConnectedDeviceHandle().answeringTheWholeUnion(LEFT)
        val source = source(handle, reader = PermissionStandingReader { false })

        val outcome = source.profileSupport()

        assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, outcome.error.category)
        assertEquals(
            emptyList(),
            handle.answerabilityRequests,
            "the platform's binding table must not be read by a round that was refused",
        )
    }

    @Test
    fun theAnnouncementStreamRefusesBeforeRegisteringAnything() = runTest {
        val handle = FakeConnectedDeviceHandle().answeringTheWholeUnion(LEFT)
        val source = source(handle, reader = PermissionStandingReader { false })

        val outcome = source.openConnectionEvents()

        assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, outcome.error.category)
        assertEquals(emptyList(), handle.openRequests, "a refused standing must not have registered a receiver")
        assertNull(handle.emit, "nor captured an emission path")
    }

    @Test
    fun anUnknownTargetSdkRefusesRatherThanChoosingAPermissionBand() = runTest {
        // The band is what decides the requirement, so guessing it would be answering the permission
        // question with a default. The resolver's own indeterminate plan becomes a refusal here, which is
        // the same outcome as a denial and a different one from an empty list.
        val handle = FakeConnectedDeviceHandle().answeringTheWholeUnion(LEFT)
        val source = source(handle, targetSdk = null)

        assertIs<ObservationRound.Failure<DeviceObservation>>(source.snapshot())
        assertEquals(emptyList(), handle.enumerationRequests)
    }

    @Test
    fun aGrantClearsTheRoundAndTheDevicesThePlatformNamedAreReported() = runTest {
        val handle = FakeConnectedDeviceHandle().answeringTheWholeUnion(LEFT)
        val source = source(handle)

        val round = source.snapshot()

        assertIs<ObservationRound.Success<DeviceObservation>>(round)
        val record = round.devices.single()
        assertEquals(DeviceObservationKey.ofReportedAddress(LEFT), record.key)
        assertEquals(ObservationArrival.SNAPSHOT, record.arrival)
        assertEquals(CLOCK_MILLIS, record.observedAtEpochMillis)
    }

    // ---- What an empty list is allowed to mean ------------------------------------------------------

    @Test
    fun anEmptyListIsReportedOnlyAfterEveryProfileInTheUnionAnswered() = runTest {
        val handle = FakeConnectedDeviceHandle().answeredByNobody()
        val source = source(handle)

        val support = assertOnSuccess(source.profileSupport())
        val round = source.snapshot()

        assertIs<ObservationRound.Success<DeviceObservation>>(round)
        assertEquals(
            emptySet(),
            support.unansweredWithin(ObservedProfile.enumerationUnion),
            "every profile spoke, so the union is a census",
        )
        assertEquals(
            emptyList(),
            round.devices,
            "an answered union that named nobody is the one case an empty list means an empty room",
        )
    }

    @Test
    fun aUnionWhereNothingAnswersReachesTheEngineAsNoAnswersAndNotAsNoDevices() = runTest {
        // The source does not duplicate the engine's empty-union refusal (ADR-P3-015 rule 7); it makes
        // that refusal possible by reporting which profiles had no mechanism behind them. A list and a
        // census are different claims, and the typed support report is what keeps them apart.
        val handle = FakeConnectedDeviceHandle()
        ObservedProfile.enumerationUnion.forEach { profile -> handle.declined(profile) }
        val source = source(handle)

        val support = assertOnSuccess(source.profileSupport())

        assertEquals(
            ObservedProfile.enumerationUnion.toSet(),
            support.unansweredWithin(ObservedProfile.enumerationUnion),
            "every decline must be visible, or an empty round would read as a completed census",
        )
    }

    @Test
    fun aProfileThatDeclinedContributesNothingAndIsNeverEnumerated() = runTest {
        val handle = FakeConnectedDeviceHandle()
            .answeringTheWholeUnion(LEFT)
            .declined(ObservedProfile.A2DP)
        val source = source(handle)

        val support = assertOnSuccess(source.profileSupport())
        val round = source.snapshot()

        assertEquals(
            ProfileObservationSupport.NOT_ANSWERABLE,
            support.supportFor(ObservedProfile.A2DP),
            "a refused binding is a statement about the mechanism, which is what the availability axis is for",
        )
        assertFalse(ObservedProfile.A2DP in handle.enumerationRequests, "and a declined profile is not asked")
        assertIs<ObservationRound.Success<DeviceObservation>>(round)
        assertEquals(
            ObservedProfile.enumerationUnion.toSet() - ObservedProfile.A2DP,
            round.devices.single().observedProfiles,
            "the device is reported by the profiles that answered; the declined one is named in the report",
        )
    }

    @Test
    fun aProfileStillWaitingForItsCallbackIsUnknownRatherThanDeclined() = runTest {
        // Not the same fact: the platform has not said no. Collapsing the two would let one slow handset
        // look permanently incapable, and the engine's rule that a completed round restores availability
        // depends on the difference holding (ADR-P3-015 rule 5).
        val handle = FakeConnectedDeviceHandle()
            .answeringTheWholeUnion(LEFT)
            .awaitingCallback(ObservedProfile.LE_AUDIO)
        val source = source(handle)

        val support = assertOnSuccess(source.profileSupport())

        assertEquals(ProfileObservationSupport.UNKNOWN, support.supportFor(ObservedProfile.LE_AUDIO))
        assertTrue(ObservedProfile.LE_AUDIO in support.unansweredWithin(ObservedProfile.enumerationUnion))
        assertFalse(ObservedProfile.LE_AUDIO in handle.enumerationRequests)
    }

    @Test
    fun aProfileTheRunningOsDoesNotExposeIsDeclinedWithoutAskingTheHandset() = runTest {
        val handle = FakeConnectedDeviceHandle().answeringTheWholeUnion(LEFT)
        val source = source(handle, apiLevel = 28)

        val support = assertOnSuccess(source.profileSupport())

        assertEquals(
            ProfileObservationSupport.NOT_ANSWERABLE,
            support.supportFor(ObservedProfile.CSIP_SET_COORDINATOR),
            "the coordinated-set constant is API 33, so there is nothing on this OS to bind",
        )
        assertEquals(ProfileObservationSupport.ANSWERABLE, support.supportFor(ObservedProfile.A2DP))
        assertEquals(
            emptyList(),
            handle.answerabilityRequests.filter { profile -> profile.introducedApiLevel > 28 },
            "the OS-level answer is given before the mechanism is consulted, not instead of it",
        )
    }

    // ---- The union and its mapping -------------------------------------------------------------------

    @Test
    fun oneDeviceNamedByTwoProfilesBecomesOneRecordNamingBoth() = runTest {
        val handle = FakeConnectedDeviceHandle()
            .answersWith(ObservedProfile.HEADSET, FakeConnectedDeviceHandle.device(LEFT, link = RawLinkState.CONNECTED))
            .answersWith(ObservedProfile.A2DP, FakeConnectedDeviceHandle.device(LEFT, link = RawLinkState.CONNECTING))
        val source = source(handle)

        val round = source.snapshot()

        assertIs<ObservationRound.Success<DeviceObservation>>(round)
        val record = round.devices.single()
        assertEquals(setOf(ObservedProfile.HEADSET, ObservedProfile.A2DP), record.observedProfiles)
        // One service still holds the link, so a second service's forming link cannot withdraw it
        // (ADR-P3-015 rule 2), and no second device was invented for the second name.
        assertEquals(DeviceConnectionState.CONNECTED, record.link)
    }

    @Test
    fun aDeviceThePlatformNamedButDidNotIdentifyIsReportedWithoutAKey() = runTest {
        val handle = FakeConnectedDeviceHandle()
            .answersWith(ObservedProfile.A2DP, FakeConnectedDeviceHandle.device(null))
        val source = source(handle)

        val record = (source.snapshot() as ObservationRound.Success).devices.single()

        assertIs<DeviceObservationKey.NotReported>(record.key)
        assertFalse(record.isAttributable, "an unkeyed record must not join with anything")
        assertEquals(DeviceAvailability.AVAILABLE, record.availability, "the platform did report a device")
    }

    @Test
    fun anEmptyAddressIsNotAKeyAndTwoUnkeyedReportsStayTwoRecords() = runTest {
        val handle = FakeConnectedDeviceHandle()
            .answersWith(
                ObservedProfile.A2DP,
                FakeConnectedDeviceHandle.device("   "),
                FakeConnectedDeviceHandle.device(""),
            )
        val source = source(handle)

        val records = (source.snapshot() as ObservationRound.Success).devices

        assertEquals(2, records.size, "merging them would weld together two devices the platform could not name")
        assertTrue(records.all { record -> record.key == DeviceObservationKey.NotReported })
    }

    @Test
    fun anUnreadableLinkStateBecomesUnknownAndNeverADisconnect() = runTest {
        val handle = FakeConnectedDeviceHandle()
            .answersWith(ObservedProfile.A2DP, FakeConnectedDeviceHandle.device(LEFT, link = RawLinkState.UNREADABLE))
        val source = source(handle)

        val record = (source.snapshot() as ObservationRound.Success).devices.single()

        assertEquals(
            DeviceConnectionState.UNKNOWN,
            record.link,
            "a value this code could not transcribe is not a report that a link went down",
        )
        assertFalse(record.isReportedConnected)
    }

    @Test
    fun anUnreadableBondStateBecomesUnknownAndNeverUnpaired() = runTest {
        val handle = FakeConnectedDeviceHandle()
            .answersWith(ObservedProfile.A2DP, FakeConnectedDeviceHandle.device(LEFT, bond = RawBondState.UNREADABLE))
        val source = source(handle)

        val record = (source.snapshot() as ObservationRound.Success).devices.single()

        assertEquals(DeviceBondState.UNKNOWN, record.bond, "no reading is not a positive report of no bond")
    }

    @Test
    fun aBlankReportedNameIsDemotedAndNoNameIsEverInvented() = runTest {
        val handle = FakeConnectedDeviceHandle()
            .answersWith(
                ObservedProfile.A2DP,
                FakeConnectedDeviceHandle.device(LEFT, name = "   "),
                FakeConnectedDeviceHandle.device(RIGHT, name = null),
            )
        val source = source(handle)

        val records = (source.snapshot() as ObservationRound.Success).devices

        assertEquals(2, records.size)
        assertTrue(records.all { record -> record.displayName == null }, "an empty string is not a name")
        assertTrue(records.all { record -> !record.hasReportedName })
    }

    @Test
    fun aRoundWithoutAClockLeavesEveryTimestampUnsetRatherThanZero() = runTest {
        val handle = FakeConnectedDeviceHandle().answeringTheWholeUnion(LEFT)
        val source = source(handle, time = TimeProvider { null })

        val record = (source.snapshot() as ObservationRound.Success).devices.single()

        assertNull(record.observedAtEpochMillis, "a zero would sort an unknown reading to the epoch (ADR-P0-016)")
    }

    @Test
    fun aThrowingEnumerationFailsTheRoundInsteadOfReportingNobody() = runTest {
        val handle = FakeConnectedDeviceHandle()
            .answeringTheWholeUnion(LEFT)
            .apply { enumerationFailure = IllegalStateException("the service handle went away") }
        val source = source(handle)

        val round = source.snapshot()

        assertIs<ObservationRound.Failure<DeviceObservation>>(round)
        assertEquals(OmniBudsErrorCategory.PLATFORM_EXCEPTION, round.error.category)
        assertTrue(round.error.detail.orEmpty().contains("IllegalStateException"), "the class is named for diagnostics")
        // The message is not quoted: a framework exception text can carry a device address (SEC-LOG-001).
        assertFalse(round.error.detail.orEmpty().contains("went away"))
    }

    @Test
    fun anAbsentAdapterRefusesTheRoundInsteadOfReportingNobody() = runTest {
        val handle = FakeConnectedDeviceHandle(present = false).answeringTheWholeUnion(LEFT)
        val source = source(handle)

        val round = source.snapshot()

        assertIs<ObservationRound.Failure<DeviceObservation>>(round)
        assertEquals(OmniBudsErrorCategory.ADAPTER_UNAVAILABLE, round.error.category)
        assertEquals(emptyList(), handle.enumerationRequests, "there was nothing to ask")
    }

    @Test
    fun theUnionAskedOfThePlatformIsTheMaintainedListAndNotSomethingInventedHere() = runTest {
        val handle = FakeConnectedDeviceHandle().answeringTheWholeUnion(LEFT)
        val source = source(handle)

        source.openConnectionEvents()
        source.snapshot()

        assertEquals(ObservedProfile.enumerationUnion, handle.openRequests.single())
        assertEquals(ObservedProfile.enumerationUnion, handle.enumerationRequests.distinct())
    }

    // ---- Registration, bindings and teardown ---------------------------------------------------------

    @Test
    fun openingRegistersOnceAndDisposalReleasesTheReceiverAndEveryProxy() = runTest {
        val handle = FakeConnectedDeviceHandle()
        val source = source(handle)
        val channel = assertOnSuccess(source.openConnectionEvents())

        assertTrue(channel.registration.isActive, "opening should have registered with the platform")
        assertEquals(1, handle.openRequests.size)

        channel.registration.dispose()

        assertFalse(channel.registration.isActive)
        assertEquals(1, handle.receiverUnregistrations, "one open, one unregister")
        assertEquals(
            ObservedProfile.enumerationUnion.size,
            handle.proxyClosures,
            "every profile this open bound has to be released, or a proxy outlives the channel that asked for it",
        )
        assertEquals(emptyList(), handle.liveRegistrations())
    }

    @Test
    fun disposingTwiceReachesThePlatformOnce() = runTest {
        val handle = FakeConnectedDeviceHandle()
        val source = source(handle)
        val channel = assertOnSuccess(source.openConnectionEvents())

        channel.registration.dispose()
        channel.registration.dispose()

        assertEquals(1, handle.receiverUnregistrations, "unregistering twice would throw on the real platform")
        assertEquals(ObservedProfile.enumerationUnion.size, handle.proxyClosures)
    }

    @Test
    fun disposalEndsTheStreamSoNoCollectorHangs() = runTest {
        val source = source(FakeConnectedDeviceHandle())
        val channel = assertOnSuccess(source.openConnectionEvents())
        val collector = launch { channel.events.collect { } }
        yield()

        channel.registration.dispose()
        collector.join()

        assertTrue(collector.isCompleted, "a disposed registration must close its stream")
    }

    @Test
    fun aFailedOpenIsReportedAndHandsBackNothingLive() = runTest {
        val handle = FakeConnectedDeviceHandle().apply {
            openFailure = IllegalStateException("no receiver could be made")
        }
        val source = source(handle)

        val outcome = source.openConnectionEvents()

        assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.PLATFORM_EXCEPTION, outcome.error.category)
        assertNull(handle.emit, "a failed registration must not leave an emission path behind")
        assertEquals(emptyList(), handle.liveRegistrations())
    }

    @Test
    fun aTeardownThatThrowsStillEndsTheStreamAndReportsTheProblem() = runTest {
        val handle = FakeConnectedDeviceHandle().apply { failDisposal = true }
        val source = source(handle)
        val channel = assertOnSuccess(source.openConnectionEvents())
        val collector = launch { channel.events.collect { } }
        yield()

        val problem = runCatching { channel.registration.dispose() }.exceptionOrNull()

        assertNotNull(problem, "a teardown that failed has to surface rather than be swallowed (ADR-P2-007)")
        collector.join()
        assertTrue(collector.isCompleted, "a stream whose teardown threw must still end")
    }

    // ---- Announcements -------------------------------------------------------------------------------

    @Test
    fun anAnnouncementReachesTheStreamAlreadyMapped() = runTest {
        val handle = FakeConnectedDeviceHandle()
        val source = source(handle)
        val channel = assertOnSuccess(source.openConnectionEvents())

        handle.emit!!(
            DeviceAnnouncement.Link(
                reportedAddress = LEFT,
                profile = ObservedProfile.A2DP,
                link = RawLinkState.DISCONNECTING,
            ),
        )

        val event = channel.events.first()

        assertIs<DeviceConnectionEvent.LinkChanged>(event)
        assertEquals(DeviceObservationKey.ofReportedAddress(LEFT), event.key)
        assertEquals(ObservedProfile.A2DP, event.profile)
        assertEquals(DeviceConnectionState.DISCONNECTING, event.link)
        assertEquals(CLOCK_MILLIS, event.observedAtEpochMillis)
    }

    @Test
    fun aLinkAnnouncementThatNamesNoProfileSurvivesAsOneThatNamesNoProfile() = runTest {
        val handle = FakeConnectedDeviceHandle()
        val source = source(handle)
        val channel = assertOnSuccess(source.openConnectionEvents())

        handle.emit!!(DeviceAnnouncement.Link(reportedAddress = LEFT, profile = null, link = RawLinkState.DISCONNECTED))

        val event = channel.events.first()

        assertIs<DeviceConnectionEvent.LinkChanged>(event)
        // The link-layer announcement names no service, and that absence is the fact the engine folds on
        // (ADR-P3-015 rule 3). Substituting a profile here would be inventing which service dropped.
        assertNull(event.profile)
        assertEquals(DeviceConnectionState.DISCONNECTED, event.link)
    }

    @Test
    fun anAnnouncementWithAnUnreadableStateBecomesUnknownAndAMissingDeviceBecomesNoKey() = runTest {
        val handle = FakeConnectedDeviceHandle()
        val source = source(handle)
        val channel = assertOnSuccess(source.openConnectionEvents())

        handle.emit!!(DeviceAnnouncement.Link(reportedAddress = null, profile = null, link = RawLinkState.UNREADABLE))

        val event = channel.events.first()

        assertIs<DeviceConnectionEvent.LinkChanged>(event)
        // A broadcast that arrived without a device extra, or without a readable state, is kept as the
        // no-value report it is: the engine records it as a refusal rather than dropping it, and an
        // invented address or an inferred disconnect would each be a fabrication about a real device.
        assertEquals(DeviceObservationKey.NotReported, event.key)
        assertEquals(DeviceConnectionState.UNKNOWN, event.link)
    }

    @Test
    fun aBondAnnouncementMovesTheBondAxisAndSaysNothingAboutTheLink() = runTest {
        val handle = FakeConnectedDeviceHandle()
        val source = source(handle)
        val channel = assertOnSuccess(source.openConnectionEvents())

        handle.emit!!(DeviceAnnouncement.Bond(reportedAddress = LEFT, bond = RawBondState.BONDED))

        val event = channel.events.first()

        assertIs<DeviceConnectionEvent.BondChanged>(event)
        assertEquals(DeviceBondState.BONDED, event.bond)
        assertEquals(DeviceObservationKey.ofReportedAddress(LEFT), event.key)
    }

    // ---- Fixtures ------------------------------------------------------------------------------------

    private fun source(
        handle: FakeConnectedDeviceHandle,
        reader: PermissionStandingReader = PermissionStandingReader { true },
        requestedBefore: Boolean = true,
        apiLevel: Int = 35,
        targetSdk: Int? = 35,
        time: TimeProvider = TimeProvider { CLOCK_MILLIS },
    ): AndroidConnectedDeviceSource = AndroidConnectedDeviceSource(
        handle = handle,
        permissionProvider = AndroidPermissionStateProvider(
            reader = reader,
            ledger = PermissionRequestLedger { requestedBefore },
        ),
        apiLevel = ApiLevelProvider { apiLevel },
        targetSdk = TargetSdkProvider { targetSdk },
        time = time,
        // Unconfined rather than the test dispatcher: every call in this suite is a single buffered read
        // or a scripted emission, so running them in place keeps the assertions about *order* - which are
        // the whole point of the file - independent of scheduling. No test here sleeps, waits or yields on
        // a timer.
        dispatcher = Dispatchers.Unconfined,
    )

    private fun assertOnSuccess(outcome: OperationOutcome<ConnectedDeviceEventChannel>): ConnectedDeviceEventChannel {
        assertIs<OperationOutcome.Success<ConnectedDeviceEventChannel>>(outcome)
        return outcome.value
    }

    private fun assertOnSuccess(outcome: OperationOutcome<ProfileSupportReport>): ProfileSupportReport {
        assertIs<OperationOutcome.Success<ProfileSupportReport>>(outcome)
        return outcome.value
    }

    /** One standing that is not a grant, paired with the request history that makes it that standing. */
    private class NonGrant(val reader: PermissionStandingReader, val requestedBefore: Boolean)

    private companion object {
        const val LEFT = "00:11:22:AA:BB:01"
        const val RIGHT = "00:11:22:AA:BB:02"
        const val CLOCK_MILLIS = 4_000L
    }
}

/**
 * Scripts every profile in the maintained union as answering with one connected device at [address].
 *
 * An extension on the fake rather than a test helper, because it is the fake's vocabulary: "the whole
 * union answered" is a claim about the platform, and the test that needs the opposite case should have to
 * say so against the same name.
 */
fun FakeConnectedDeviceHandle.answeringTheWholeUnion(address: String): FakeConnectedDeviceHandle = apply {
    ObservedProfile.enumerationUnion.forEach { profile ->
        answersWith(profile, FakeConnectedDeviceHandle.device(address))
    }
}

/** Scripts the whole union as having answered with nobody, which is the empty-room case. */
fun FakeConnectedDeviceHandle.answeredByNobody(): FakeConnectedDeviceHandle = apply {
    ObservedProfile.enumerationUnion.forEach { profile -> answersWithNobody(profile) }
}

package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsErrorCategory
import kotlinx.coroutines.flow.toList
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
 * Prompt section 10's collection B, tested as a separate answer rather than as a flag.
 *
 * Section 3 of the Phase 3 prompt requires the engine to "distinguish paired devices from connected
 * devices", section 6 calls the distinction mandatory and says a paired device is not necessarily
 * connected, and section 10 names the two collections this file is about: what the platform reports a link
 * to, and what the phone has paired. The model has carried a bond axis since ADR-P3-001, but an axis nobody
 * can populate is not a distinction - so every case here is about the paired half of the projection
 * arriving, staying separate, and never being mistaken for the connected half.
 *
 * The tests are grouped by the confusion they prevent rather than by the function they call, and they run
 * through the same scripted seam as [ConnectedDeviceObserverTest] for the same reason (ADR-P3-003): every
 * one of these is a decision about ordering, authority and attribution, and a decision that needs a radio
 * to test is one nobody will re-check.
 *
 * Nothing here is evidence about a handset. What a real phone's bond list contains, whether it is readable
 * while the adapter is off, and whether a paired headset appears in it as expected are platform claims
 * capped at `IMPLEMENTED` by ADR-P3-014 and collected in the deferred device session.
 */
class BondedDeviceObservationTest {

    private val adapterQuiet = FakeAdapterStateSource()

    // ---- The two collections stay two answers ---------------------------------------------------------

    @Test
    fun aPairedDeviceNoProfileReportedIsInNoLinkProjectionAtAll() = runTest {
        // Section 10.B's whole point: the user's headset is paired, it is at home, and the engine can now
        // name it. Note what the record does *not* carry: no link state, because the bond list never
        // mentioned one. Filling that gap with DISCONNECTED would be ADR-P0-016's forbidden substitution -
        // absence of a report dressed up as a report of absence - and filling it with CONNECTED would be
        // section 6's refused mistake in the other direction. The honest answer to "what is this paired
        // device's link state" is UNKNOWN, and the shape that keeps it honest is a record with no field to
        // be wrong in.
        val source = FakeConnectedDeviceSource(
            success(),
            pairedRounds = listOf(pairedRound(bondedRecord(ADDRESS_LEFT, name = LABEL))),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        observer.observe().toList()

        val projection = observer.snapshot.value
        assertEquals(ADDRESS_LEFT_KEY, assertNotNull(projection.pairedDevices.single()).key)
        assertTrue(projection.pairedDevices.single().bond.isBonded())
        assertTrue(projection.records.isEmpty(), "a paired-only device must not enter the link projection")
        assertTrue(projection.connectedDevices.isEmpty(), "and so cannot appear among active connections")
        assertTrue(projection.isPairedCensus, "the read answered, so its contents are a counted answer")
        assertTrue(projection.isUnionComplete, "the link census is a separate question and stays complete")
    }

    @Test
    fun aRefusedBondListIsNeverRestatedAsAPhoneWithNoPairings() = runTest {
        // ADR-P3-009 applied to the pairing side, which is the sharper case: the platform's own contract
        // answers an empty set for a refused read, a switched-off adapter and a phone with no bonds alike.
        // So the refusal has to be visible *as* a refusal and the collection has to stay unclaimed.
        val source = FakeConnectedDeviceSource(success(), pairedRounds = listOf(pairedRoundRefused()))
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val rounds = observer.observe().toList()

        assertIs<ObservationRound.Success<DeviceObservation>>(
            rounds.single(),
            "a refused bond list does not fail the link observation it sits beside",
        )
        val projection = observer.snapshot.value
        assertTrue(projection.pairedDevices.isEmpty())
        assertFalse(projection.restsOnCompletedPairedRound, "nobody read the list, so nobody counted it")
        assertFalse(projection.isPairedCensus)
        assertEquals(
            OmniBudsErrorCategory.PERMISSION_DENIED,
            assertNotNull(projection.pairedRoundRefusal).category,
            "the reason is carried as a fact about the observation, not dropped",
        )
    }

    @Test
    fun aGoodPairedRoundThenARefusalKeepsTheEntriesAndWithdrawsTheCensus() = runTest {
        // A refusal has no authority over what the platform last said (ADR-P3-005), so the entries survive;
        // but the claim that they are current does not, because the read that would have confirmed them was
        // not performed. Both halves are needed: entries without the withdrawal would read as a live
        // census, and a withdrawal that also deleted the entries would be a refusal destroying evidence.
        val source = FakeConnectedDeviceSource(
            success(),
            success(),
            pairedRounds = listOf(pairedRound(bondedRecord(ADDRESS_LEFT)), pairedRoundRefused()),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())
        observer.observe().toList()

        assertIs<ObservationRound.Success<DeviceObservation>>(observer.refresh())

        val projection = observer.snapshot.value
        assertEquals(1, projection.pairedDevices.size, "the phone last named this pairing and may still hold it")
        assertTrue(
            projection.restsOnCompletedPairedRound,
            "the entries still came from the read that answered; a later refusal says nothing about that",
        )
        assertFalse(projection.isPairedCensus, "and the refusal is what withdraws the claim that they are current")
        assertNotNull(projection.pairedRoundRefusal)
    }

    @Test
    fun aCalledOffPairedRoundChangesNothingIncludingTheReasonBeforeIt() = runTest {
        // Cancellation is a case rather than a finding (ADR-P2-018), and on the pairing side it also has to
        // leave a real refusal standing: a read called off after a read refused says nothing about why the
        // second one did not happen, and clearing the reason would lose the only evidence of the first.
        val source = FakeConnectedDeviceSource(
            success(),
            success(),
            pairedRounds = listOf(pairedRoundRefused(), ObservationRound.Cancelled),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())
        observer.observe().toList()

        observer.refresh()

        val projection = observer.snapshot.value
        assertFalse(projection.restsOnCompletedPairedRound)
        assertEquals(
            OmniBudsErrorCategory.PERMISSION_DENIED,
            assertNotNull(projection.pairedRoundRefusal).category,
            "the earlier refusal is still the reason the collection is unclaimed",
        )
        assertEquals(2, source.bondedCalls)
    }

    @Test
    fun eachPairedRoundRestatesTheCollectionInsteadOfAccumulatingIt() = runTest {
        // Prompt sections 10.C and 16 forbid a saved-device store, and the comfortable way to break that
        // rule is an engine that unions every bond list it has ever read - a history of the user's pairings
        // wearing a projection's name. The bond list is the phone's live record, so a device the phone has
        // since unbonded leaves when the next read says so, and nothing accumulates between rounds.
        val source = FakeConnectedDeviceSource(
            success(),
            success(),
            pairedRounds = listOf(
                pairedRound(bondedRecord(ADDRESS_LEFT), bondedRecord(ADDRESS_RIGHT)),
                pairedRound(bondedRecord(ADDRESS_RIGHT)),
            ),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        observer.observe().toList()
        observer.refresh()

        val projection = observer.snapshot.value
        assertEquals(
            listOf(ADDRESS_RIGHT_KEY),
            projection.pairedDevices.map { record -> record.key },
            "the last answer replaces the previous one; nothing is kept for having once been seen",
        )
        assertEquals(2, source.bondedCalls)
    }

    @Test
    fun twoPairedDevicesSharingALabelStayTwoPairedRecords() = runTest {
        // Section 12's rule on the pairing side: one user, two of the same product, one name. The join is
        // the key and nothing else, so a merge that consulted the display name would weld them together.
        val source = FakeConnectedDeviceSource(
            success(),
            pairedRounds = listOf(
                pairedRound(bondedRecord(ADDRESS_LEFT, name = LABEL), bondedRecord(ADDRESS_RIGHT, name = LABEL)),
            ),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        observer.observe().toList()

        val paired = observer.snapshot.value.pairedDevices
        assertEquals(2, paired.size)
        assertEquals(1, paired.mapNotNull { record -> record.displayName }.distinct().size, "one label, two devices")
    }

    @Test
    fun aPairedRecordThePlatformGaveNoKeyForJoinsNothingAndStaysSeparate() = runTest {
        // ADR-P3-010's consequence for the new record type: two nameless paired reports are two reports of
        // nothing, and an unkeyed record cannot credit a bond to a projection record either.
        val connected = reported(ADDRESS_LEFT)
        val source = FakeConnectedDeviceSource(
            success(connected),
            pairedRounds = listOf(pairedRound(bondedRecord(null), bondedRecord("   "))),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        observer.observe().toList()

        val projection = observer.snapshot.value
        assertEquals(2, projection.pairedDevices.size, "merging two absences would invent one device")
        assertTrue(projection.pairedDevices.all { record -> !record.isAttributable })
        assertEquals(
            DeviceBondState.UNKNOWN,
            projection.records.single().bond,
            "and nothing unattributable may move a record's bond axis",
        )
    }

    // ---- What the paired fold is allowed to do to a projection record ---------------------------------

    @Test
    fun theBondListCreditsTheBondAxisAndLeavesEveryOtherFieldAlone() = runTest {
        // The one thing the fold may do, pinned as narrowly as possible: a device the profiles report as
        // connected and the bond list names is one device with two reports, and the paired read must not
        // move the link, the profile set, the arrival, the availability or even the name it found. A
        // consumer that wants one row per device joins the collections on the key (ADR-P3-010) rather than
        // the engine writing across the boundary between them.
        val held = reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val source = FakeConnectedDeviceSource(
            success(held),
            pairedRounds = listOf(pairedRound(bondedRecord(ADDRESS_LEFT, name = LABEL))),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        val round = assertIs<ObservationRound.Success<DeviceObservation>>(observer.observe().toList().single())

        val record = round.devices.single()
        assertEquals(DeviceBondState.BONDED, record.bond, "the pairing is now a report rather than a gap")
        assertEquals(DeviceConnectionState.CONNECTED, record.link, "and it is still the only thing that says so")
        assertEquals(setOf(ObservedProfile.A2DP), record.observedProfiles)
        assertEquals(ObservationArrival.SNAPSHOT, record.arrival)
        assertEquals(DeviceAvailability.AVAILABLE, record.availability)
        assertNull(record.displayName, "a paired record's name does not cross into the link projection")
        assertEquals(1, observer.snapshot.value.pairedDevices.size, "the same device is named in both collections")
        assertEquals(1, observer.snapshot.value.connectedDevices.size, "and only one of them is a connection")
    }

    @Test
    fun aBondRoundCannotMakeADeviceTheUnionNeverListedLookDisconnected() = runTest {
        // ADR-P3-015 rule 4 in its paired-side form: absence from a completed union becomes a disconnect only
        // for a record the projection already holds. A paired device the link mechanisms never named is not
        // in the projection to be disconnected, so no stale link claim is created and then withdrawn.
        val held = reported(ADDRESS_RIGHT, profiles = setOf(ObservedProfile.A2DP))
        val source = FakeConnectedDeviceSource(
            success(held),
            success(),
            pairedRounds = listOf(pairedRound(bondedRecord(ADDRESS_LEFT))),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())
        observer.observe().toList()

        observer.refresh()

        val projection = observer.snapshot.value
        assertEquals(
            listOf(ADDRESS_RIGHT_KEY),
            projection.records.map { record -> record.key },
            "only the record a profile reported is still there, and the completed union disconnects it",
        )
        assertEquals(DeviceConnectionState.DISCONNECTED, projection.records.single().link)
        assertEquals(listOf(ADDRESS_LEFT_KEY), projection.pairedDevices.map { record -> record.key })
        assertTrue(projection.connectedDevices.isEmpty())
    }

    @Test
    fun aPairedReadingThatDisagreesWithTheListIsKeptRatherThanSmoothed() = runTest {
        // The list named the device and the device object itself says NONE - the mid-unbond case, and the one
        // place where two mechanisms disagree about one axis. ADR-P3-001's three axes and the merge rule in
        // DeviceObservation.mergedWith both say a disagreement is a finding for a human rather than
        // something a fold may splice into a hybrid, so the record keeps what the device reported.
        val source = FakeConnectedDeviceSource(
            success(),
            pairedRounds = listOf(pairedRound(bondedRecord(ADDRESS_LEFT, bond = DeviceBondState.NONE))),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        observer.observe().toList()

        val paired = observer.snapshot.value.pairedDevices.single()
        assertFalse(paired.bond.isBonded(), "membership of the list is not a licence to overrule the device")
        assertTrue(paired.bond.isKnown())
    }

    // ---- The adapter edge, which is where an empty list is most tempting ------------------------------

    @Test
    fun anAdapterGoingOffKeepsThePairedEntriesAndClearsOnlyTheClaim() = runTest {
        // Section 11 speaks of clearing the active-connected projection when Bluetooth turns off, and a
        // pairing is not an active connection. The link records go unobservable as they always did; the
        // paired list keeps its entries and loses its census, because the platform's own contract for this
        // read is that it answers an empty set whenever the adapter is not on. Emptying the collection here
        // would be this phase's central prohibition, arrived at through the pairing side.
        val source = FakeConnectedDeviceSource(
            success(reported(ADDRESS_LEFT, profiles = setOf(ObservedProfile.A2DP))),
            pairedRounds = listOf(pairedRound(bondedRecord(ADDRESS_LEFT))),
        )
        val adapter = FakeAdapterStateSource(eventScript = listOf(BluetoothAdapterState.DISABLED))
        val observer = ConnectedDeviceObserver(source.asSource(), adapter.asSource())

        val rounds = observer.observe().toList()

        assertIs<ObservationRound.Failure<DeviceObservation>>(rounds.last())
        val projection = observer.snapshot.value
        assertEquals(1, projection.pairedDevices.size, "the phone's pairings were not erased by its radio stopping")
        assertTrue(projection.restsOnCompletedPairedRound, "they still came from a read that answered")
        assertFalse(projection.isPairedCensus, "and the adapter edge is what withdraws the claim on them")
        assertEquals(
            OmniBudsErrorCategory.BLUETOOTH_DISABLED,
            assertNotNull(projection.pairedRoundRefusal).category,
            "and the reason is the adapter, not a count",
        )
        assertTrue(projection.connectedDevices.isEmpty(), "the active-connected projection is the part that clears")
    }

    // ---- The seam's own discipline --------------------------------------------------------------------

    @Test
    fun theBondListIsReadOncePerRoundAndNeverAsASideEffectOfAnotherRead() = runTest {
        // The counts are the assertion: one paired read per device round, the announcements opened before
        // either read, and no second read smuggled in beside an announcement. A source whose bond list
        // arrived inside snapshot() would be one list with a flag again, so the call sequence is what is
        // pinned here rather than the contents.
        val source = FakeConnectedDeviceSource(
            success(),
            success(),
            pairedRounds = listOf(pairedRound(bondedRecord(ADDRESS_LEFT)), pairedRound(bondedRecord(ADDRESS_LEFT))),
        )
        val observer = ConnectedDeviceObserver(source.asSource(), adapterQuiet.asSource())

        observer.observe().toList()
        observer.refresh()

        assertEquals(2, source.snapshotCalls)
        assertEquals(2, source.bondedCalls)
        assertEquals(1, source.openCalls, "the announcements were opened once, before either read")
    }

    @Test
    fun anUnavailableSourceRefusesTheBondListTooAndNeverAnswersZero() = runTest {
        // The composition root with no device-facing mechanism is the case that most easily invents a quiet
        // room, because it has nothing to say and an empty list is always available to return.
        val observer = ConnectedDeviceObserver(
            ConnectedDeviceSource.unavailable(),
            AdapterStateSource.unavailable(),
        )

        assertIs<ObservationRound.Failure<DeviceObservation>>(observer.refresh())

        val projection = observer.snapshot.value
        assertEquals(0, projection.pairedDevices.size, "nothing was read, so nothing was counted")
        assertFalse(projection.isPairedCensus)
        assertEquals(
            OmniBudsErrorCategory.ADAPTER_UNAVAILABLE,
            assertNotNull(projection.pairedRoundRefusal).category,
            "the source says so about pairings as well as about links",
        )
    }

    // ---- The model's own shape ------------------------------------------------------------------------

    @Test
    fun thePairedRecordHasNoFieldThatCouldClaimALink() {
        // The structural half of "never present a paired device as connected", checked against the declared
        // fields rather than against prose: a consumer cannot read a link out of a record that has none, and
        // a later edit that adds one has a build to argue with. A predicate every consumer would have to
        // remember is a convention; an absent field is a refusal.
        //
        // The companion object is excluded because Kotlin emits it as a static field and it is the factory
        // the platform adapter calls, not a fact about a device.
        val fields = BondedDeviceObservation::class.java.declaredFields
            .map { field -> field.name }
            .filter { name -> name != COMPANION_FIELD }
            .toSet()

        assertEquals(
            setOf("key", "displayName", "bond", "observedAtEpochMillis"),
            fields,
            "a paired record carries the four facts a bond read can report and nothing else",
        )
        assertFalse("link" in fields, "the bond list reports no link")
        assertFalse("observedProfiles" in fields, "and no profile set arrives from a pairing")
        assertFalse("availability" in fields, "and observability is the link mechanisms' answer, not this one's")
    }

    @Test
    fun mergingTwoPairedRecordsThatCannotJoinIsRefusedRatherThanGuessed() {
        val left = bondedRecord(ADDRESS_LEFT)
        val right = bondedRecord(ADDRESS_RIGHT)
        val unkeyed = bondedRecord(null)

        val differentDevice = assertFailsWith<IllegalArgumentException> { left.mergedWith(right) }
        val bothUnkeyed = assertFailsWith<IllegalArgumentException> { unkeyed.mergedWith(unkeyed) }
        assertTrue(differentDevice.message!!.contains("must not be merged"))
        assertTrue(bothUnkeyed.message!!.contains("must not be merged"), "two absences are not one pairing")

        val nameless = bondedRecord(ADDRESS_LEFT)
        val named = bondedRecord(ADDRESS_LEFT, name = LABEL)
        assertEquals(LABEL, nameless.mergedWith(named).displayName, "a join that is legal still fills the gap")
        assertEquals(
            DeviceBondState.BONDED,
            bondedRecord(ADDRESS_LEFT, bond = DeviceBondState.NONE).mergedWith(named).bond,
            "and the more positive bond report survives the merge",
        )
    }

    @Test
    fun aBlankPairedNameIsNoNameAtAll() {
        val blank = bondedRecord(ADDRESS_LEFT, name = "   ")
        val absent = bondedRecord(ADDRESS_LEFT, name = null)

        assertNull(blank.displayName)
        assertFalse(blank.hasReportedName)
        assertFalse(absent.hasReportedName)
    }

    @Test
    fun theProjectionStartsUnreadOnBothCollections() {
        // The opening state must not read as an answer on either side: "no devices connected" *and* "no
        // devices paired" are two claims an unstarted observer is not entitled to make.
        val projection = ConnectedDeviceSnapshot.notStarted(ObservedProfile.entries.toList())

        assertTrue(projection.records.isEmpty())
        assertTrue(projection.pairedDevices.isEmpty())
        assertFalse(projection.isUnionComplete)
        assertFalse(projection.isPairedCensus)
        assertFalse(projection.restsOnCompletedPairedRound)
        assertNull(projection.pairedRoundRefusal, "nothing has been refused yet either")
        assertEquals(ObservationStage.NOT_STARTED, projection.stage)
    }

    // ---- Fixtures -------------------------------------------------------------------------------------

    private fun success(vararg devices: DeviceObservation): ObservationRound<DeviceObservation> =
        // A source states no lifecycle: whatever stage a round arrives with, the observer restates its own.
        ObservationRound.Success(devices = devices.toList(), stage = ObservationStage.NOT_STARTED)

    private fun reported(
        address: String,
        link: DeviceConnectionState = DeviceConnectionState.CONNECTED,
        bond: DeviceBondState = DeviceBondState.UNKNOWN,
        profiles: Set<ObservedProfile> = emptySet(),
    ): DeviceObservation = DeviceObservation.reported(
        key = DeviceObservationKey.ofReportedAddress(address),
        link = link,
        bond = bond,
        availability = DeviceAvailability.AVAILABLE,
        observedProfiles = profiles,
    )

    private companion object {
        const val ADDRESS_LEFT = "00:11:22:AA:BB:01"
        const val ADDRESS_RIGHT = "00:11:22:AA:BB:02"
        const val LABEL = "OmniBuds Air"

        /** The static field Kotlin emits for a companion object, which is a factory and not a fact. */
        const val COMPANION_FIELD = "Companion"
        val ADDRESS_LEFT_KEY: DeviceObservationKey = DeviceObservationKey.ofReportedAddress(ADDRESS_LEFT)
        val ADDRESS_RIGHT_KEY: DeviceObservationKey = DeviceObservationKey.ofReportedAddress(ADDRESS_RIGHT)
    }
}

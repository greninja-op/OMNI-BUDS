package com.omnibuds.android.bluetooth.connection

import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.platform.PlatformRegistration

/**
 * A scripted [ConnectedDeviceHandle] for unit tests.
 *
 * It exists so the interesting half of Phase 3's Android code - the ordering, the refusal, the union and
 * the teardown - can be tested on a JVM with no radio, which is the same argument that put
 * [com.omnibuds.core.platform.ConnectedDeviceObserver] in `:core` and made
 * [com.omnibuds.android.bluetooth.adapter.BluetoothAdapterHandle] a seam. What stays unproven here is the
 * framework call itself, and the instrumented suite is the only thing in this repository that can speak
 * to it; nothing in this file should be read as evidence about a handset
 * (`docs/phases/phase-2/validation.md` known issue 2, ADR-P3-014).
 *
 * Deliberate properties, following [com.omnibuds.core.platform.FakeConnectedDeviceSource] rather than
 * inventing a second discipline:
 *  - an unscripted profile is [ProfileAnswerability.NOT_REQUESTED] and enumerates to
 *    [ProfileEnumeration.Unanswered], and an unscripted bond read is [BondedListing.ReadFailed], so a test
 *    cannot accidentally assert against an answer nobody gave. The bond case is the one where a careless
 *    default would be load-bearing: an empty [BondedListing.Reported] is a *census*, a statement that the
 *    phone has paired nothing, and a fake that returned it silently would let every existing test invent
 *    that claim;
 *  - every question is recorded, so "refused before enumerating", "refused before reading the bond list"
 *    and "registered exactly once" are observations rather than intentions;
 *  - [failDisposal] models a platform teardown that throws, which is the case a `finally` block and a
 *    bound profile service are supposed to survive;
 *  - the addresses are the caller's test constants and this class never prints one, because the fake is
 *    not where an identifier should acquire a string representation.
 */
class FakeConnectedDeviceHandle(var present: Boolean = true) : ConnectedDeviceHandle {

    private val answers = mutableMapOf<ObservedProfile, ProfileAnswerability>()
    private val enumerations = mutableMapOf<ObservedProfile, ProfileEnumeration>()
    private val registrations = mutableListOf<FakeRegistration>()

    var answerabilityFailure: Throwable? = null
    var enumerationFailure: Throwable? = null
    var openFailure: Throwable? = null

    /** Scripts the bond-list read. Null means nobody scripted it, which is a refusal, not an empty set. */
    var bondListing: BondedListing? = null

    /** Makes the next bond-list read throw, the way a binder call does when the service has gone away. */
    var bondFailure: Throwable? = null

    /** Makes the next teardown throw, the way unregistering an unknown receiver does on the platform. */
    var failDisposal = false

    /** Captured so a test can push the announcement a platform receiver would have delivered. */
    var emit: ((DeviceAnnouncement) -> Unit)? = null
        private set

    val answerabilityRequests = mutableListOf<ObservedProfile>()
    val enumerationRequests = mutableListOf<ObservedProfile>()
    val openRequests = mutableListOf<List<ObservedProfile>>()

    /** How many times the bond list was actually read, which is how a pre-flight becomes an observation. */
    var bondReads = 0
        private set

    /** How many times a registration reached the platform, across every channel this fake handed out. */
    var receiverUnregistrations = 0
        private set

    var proxyClosures = 0
        private set

    override val adapterPresent: Boolean
        get() = present

    override fun answerability(profile: ObservedProfile): ProfileAnswerability {
        answerabilityRequests += profile
        answerabilityFailure?.let { problem -> throw problem }
        return answers[profile] ?: ProfileAnswerability.NOT_REQUESTED
    }

    override fun enumerate(profile: ObservedProfile): ProfileEnumeration {
        enumerationRequests += profile
        enumerationFailure?.let { problem -> throw problem }
        return enumerations[profile] ?: ProfileEnumeration.Unanswered
    }

    /**
     * Returns the scripted [BondedListing], or the refusal an unscripted fake owes.
     *
     * The default is deliberately *not* an empty reported set. With no scripting there is no answer about
     * this phone's pairings, and [BondedListing.ReadFailed] is the case that says so; defaulting to
     * `Reported(emptyList())` would hand every test that never thinks about pairings a positive claim that
     * the device has none, which is the confusion ADR-P3-009 was written to close.
     */
    override fun bondedDevices(): BondedListing {
        bondReads += 1
        bondFailure?.let { problem -> throw problem }
        return bondListing ?: if (present) BondedListing.ReadFailed else BondedListing.AdapterAbsent
    }

    override fun openAnnouncements(
        profiles: List<ObservedProfile>,
        emit: (DeviceAnnouncement) -> Unit,
    ): PlatformRegistration {
        openRequests += profiles
        openFailure?.let { problem -> throw problem }
        this.emit = emit
        return FakeRegistration(profiles).also { registrations += it }
    }

    /** The channels handed out and not yet disposed, which is how a leaked binding becomes a failed test. */
    fun liveRegistrations(): List<PlatformRegistration> = registrations.filter { registration -> registration.isActive }

    /** Scripts [profile] as answering, and lists [devices] as what it reports. */
    fun answersWith(profile: ObservedProfile, vararg devices: DeviceReport): FakeConnectedDeviceHandle = apply {
        answers[profile] = ProfileAnswerability.ANSWERABLE
        enumerations[profile] = ProfileEnumeration.Reported(devices.toList())
    }

    /** Scripts [profile] as having answered with nobody, which is evidence rather than silence. */
    fun answersWithNobody(profile: ObservedProfile): FakeConnectedDeviceHandle = apply {
        answers[profile] = ProfileAnswerability.ANSWERABLE
        enumerations[profile] = ProfileEnumeration.Reported(emptyList())
    }

    /** Scripts [profile] as the platform having declined the mechanism. */
    fun declined(profile: ObservedProfile): FakeConnectedDeviceHandle = apply {
        answers[profile] = ProfileAnswerability.REFUSED
        enumerations[profile] = ProfileEnumeration.Unanswered
    }

    /** Scripts [profile] as bound but not yet called back, which is not a refusal and not a report. */
    fun awaitingCallback(profile: ObservedProfile): FakeConnectedDeviceHandle = apply {
        answers[profile] = ProfileAnswerability.AWAITING_CALLBACK
        enumerations[profile] = ProfileEnumeration.Unanswered
    }

    /** Scripts the bond list as having been read and answered with [devices], which may be nobody. */
    fun bondedWith(vararg devices: BondedDeviceReport): FakeConnectedDeviceHandle = apply {
        bondListing = BondedListing.Reported(devices.toList())
    }

    /** Scripts the four refusal-shaped cases individually, because each earns a different category. */
    fun bondedListing(listing: BondedListing): FakeConnectedDeviceHandle = apply { bondListing = listing }

    /** A registration that releases the receiver and every proxy the open asked for, once. */
    private inner class FakeRegistration(private val profiles: List<ObservedProfile>) : PlatformRegistration {
        private var active = true

        /** Counts calls, so a test can tell "disposed twice, released once" from "disposed once". */
        var disposeCalls = 0
            private set

        override val isActive: Boolean
            get() = active

        override suspend fun dispose() {
            disposeCalls += 1
            if (!active) return
            if (failDisposal) throw IllegalStateException("simulated platform teardown failure")
            active = false
            registrations -= this
            receiverUnregistrations += 1
            proxyClosures += profiles.size
        }
    }

    companion object {
        /**
         * Builds one device report with the least scripting a test needs.
         *
         * The defaults are the shape a connected, bonded earbud has, because that is the case most tests
         * are about; every field is overridable so an absence can be scripted without a second helper.
         */
        fun device(
            address: String?,
            name: String? = null,
            link: RawLinkState = RawLinkState.CONNECTED,
            bond: RawBondState = RawBondState.BONDED,
        ): DeviceReport = DeviceReport(
            reportedAddress = address,
            reportedName = name,
            link = link,
            bond = bond,
        )

        /**
         * Builds one paired-device report with the least scripting a test needs.
         *
         * There is no link argument, and that is the point: a bond-list entry is a stored pairing key and the
         * platform's own wording for it says it "does not necessarily mean the device is currently
         * connected". A helper that let a test supply a link here would be the seam admitting the inference
         * prompt section 6 refuses, so the shape keeps it unrepresentable (ADR-P3-017).
         */
        fun paired(
            address: String?,
            name: String? = null,
            bond: RawBondState = RawBondState.BONDED,
        ): BondedDeviceReport = BondedDeviceReport(
            reportedAddress = address,
            reportedName = name,
            bond = bond,
        )
    }
}

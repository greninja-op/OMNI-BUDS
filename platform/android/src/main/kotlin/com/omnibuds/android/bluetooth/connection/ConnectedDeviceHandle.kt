package com.omnibuds.android.bluetooth.connection

import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.platform.PlatformRegistration

/**
 * The narrow seam between [AndroidConnectedDeviceSource] and the Android Bluetooth stack.
 *
 * No type in this file names an Android class, and that is the whole reason the file exists. The one
 * implementation permitted to hold framework references is [SystemConnectedDeviceHandle]; everything
 * above this interface is ordinary Kotlin, so the decisions Phase 3 actually makes - what a refused
 * standing costs, what a profile that never bound means for the device list, which record a broadcast
 * may attribute itself to - are testable on a JVM with no radio, exactly as
 * [com.omnibuds.android.bluetooth.adapter.BluetoothAdapterHandle] made the adapter machine testable in
 * Phase 2 (Phase 2 prompt section 9, ADR-P3-003).
 *
 * The seam speaks in *raw* named cases rather than in `:core`'s [com.omnibuds.core.platform.DeviceConnectionState]
 * and [com.omnibuds.core.platform.DeviceBondState], following Phase 2's split between a platform
 * integer and a domain state. The split is not decoration: a mapping that has already thrown away the
 * difference between "the stack said 0" and "the stack said nothing" cannot be tested for it, and that
 * difference is ADR-P3-005's entire subject. So [SystemConnectedDeviceHandle] transcribes numbers into
 * the named cases below, and [AndroidConnectedDeviceSource] decides what they mean.
 *
 * Four members, because four questions are authorised: is there an adapter to ask, can this profile
 * answer, what does it answer, and will it announce changes. Adding a fifth - pair, connect, enable,
 * ask the device for something - would put a capability this phase forbids behind a name that sounds
 * like a read (architecture audit section 10, research section 8.2).
 */
interface ConnectedDeviceHandle {
    /** Whether this phone exposes a Bluetooth adapter at all. False is a real answer, not an error. */
    val adapterPresent: Boolean

    /**
     * Whether [profile] has a mechanism that could answer right now.
     *
     * Asked before enumerating, because the two answers a caller must not confuse - "it has not said
     * anything yet" and "it said there is nothing" - are decided by whether anything was ever in a
     * position to speak. A profile whose service binding is still in flight is
     * [ProfileAnswerability.AWAITING_CALLBACK], which is not a refusal and is not a report of zero
     * devices either.
     */
    fun answerability(profile: ObservedProfile): ProfileAnswerability

    /**
     * The devices [profile] reports, or the fact that it has not reported.
     *
     * [ProfileEnumeration.Unanswered] is a first-class case rather than an empty [ProfileEnumeration.Reported]
     * for the reason ADR-P3-008 gives: a proxy that never binds would otherwise hand the projection an
     * empty list that reads as "these earbuds are not connected", from a mechanism that was never
     * asked. An empty [ProfileEnumeration.Reported] is available at all only when
     * [answerability] said [ProfileAnswerability.ANSWERABLE] first.
     */
    fun enumerate(profile: ObservedProfile): ProfileEnumeration

    /**
     * Registers for the platform's device announcements and binds the service handles [profiles] need.
     *
     * Both halves belong in one call because both halves are the same resource: the bindings are
     * requested when the listener starts, and the registration returned here is the only thing that
     * can release them. A bind started outside this call would be a bound proxy with no owner left to
     * close it, which is the leak ADR-P3-008's consequence names and this phase cannot repair after
     * the fact.
     *
     * Implementations make disposal idempotent and complete: [PlatformRegistration.dispose] releases
     * the receiver *and* every proxy this call bound, once, because the platform throws on an
     * unregister it does not recognise and a profile service left bound outlives the observer that
     * asked for it.
     */
    fun openAnnouncements(
        profiles: List<ObservedProfile>,
        emit: (DeviceAnnouncement) -> Unit,
    ): PlatformRegistration
}

/**
 * What the platform said about one link, in the platform's own terms, before anyone read it as state.
 *
 * [UNREADABLE] is not [DISCONNECTED]. It means a broadcast arrived without a value this code can
 * transcribe, or a number outside the platform's own set, and reporting it as a disconnect would turn
 * a malformed announcement into a claim that a user's earbuds stopped playing (ADR-P0-016).
 */
enum class RawLinkState {
    CONNECTED,
    CONNECTING,
    DISCONNECTING,
    DISCONNECTED,
    UNREADABLE,
}

/** The same discipline on the pairing axis: [UNREADABLE] means no reading, and never [NONE]. */
enum class RawBondState {
    BONDED,
    BONDING,
    NONE,
    UNREADABLE,
}

/**
 * Whether one profile's mechanism is in a position to answer, as the handle last learned.
 *
 * The four cases exist because the two silences are different facts. [NOT_REQUESTED] and
 * [ProfileAnswerability.AWAITING_CALLBACK] both mean "no evidence yet" - one because nothing was asked,
 * the other because the platform's asynchronous binding has not reported back - and [REFUSED] means the
 * platform actively declined or dropped the mechanism. ADR-P3-008 attaches
 * [com.omnibuds.core.platform.ProfileObservationSupport.NOT_ANSWERABLE] to the refusal; the first two stay
 * [com.omnibuds.core.platform.ProfileObservationSupport.UNKNOWN], which is what lets a later round answer
 * without having rewritten an earlier claim.
 */
enum class ProfileAnswerability {
    /** No binding was ever requested for this profile: nothing is in a position to speak. */
    NOT_REQUESTED,

    /** A bind was requested and the platform has not called back. Not a refusal (research 7.1). */
    AWAITING_CALLBACK,

    /** The mechanism answers: a bound proxy, or a route that needs no binding at all. */
    ANSWERABLE,

    /** The platform refused the bind, or the bound service went away. */
    REFUSED,
}

/**
 * How much one answer about a profile's mechanism claims, strongest last.
 *
 * Deliberately not the enum's declaration order, and the difference is the point: a handset that refused
 * a bind is a *weaker* statement than one that has not answered yet, because the refusal is about a
 * mechanism this process tried and the silence is about nothing at all. An implementation that combined
 * answers from more than one listener by ordinal would report "declined" where the truth was "still
 * binding", and ADR-P3-008's rule about a profile that never binds turns on exactly that distinction.
 */
fun ProfileAnswerability.evidenceRank(): Int = when (this) {
    ProfileAnswerability.NOT_REQUESTED -> 0
    ProfileAnswerability.AWAITING_CALLBACK -> 1
    ProfileAnswerability.REFUSED -> 2
    ProfileAnswerability.ANSWERABLE -> 3
}

/**
 * What one profile's enumeration produced.
 *
 * Two cases, and the second one is the point: a list is evidence and the absence of a mechanism is not.
 */
sealed interface ProfileEnumeration {
    /** The profile answered. An empty [devices] here is that profile reporting nobody. */
    data class Reported(val devices: List<DeviceReport>) : ProfileEnumeration

    /** The profile could not answer, so it contributes nothing and withdraws nothing. */
    data object Unanswered : ProfileEnumeration
}

/**
 * One device a profile named, reduced to what Phase 3 is authorised to read.
 *
 * The address and the name are the platform's strings and may be null, because both are documented
 * cache reads that a handset legitimately withholds (research section 5.1); neither is defaulted here.
 * No device class, no UUIDs, no type-derived guess travels through this record, because prompt
 * section 16 forbids the inference and research section 5.2 forecloses the principled version of it.
 */
data class DeviceReport(
    /** The address as reported, or null when the platform named a device it would not identify. */
    val reportedAddress: String?,

    /** The name as reported, or null. Never an empty string standing in for "unread". */
    val reportedName: String?,

    /** This profile's own report of the link, in the platform's terms. */
    val link: RawLinkState,

    /** The pairing report read from the same device object. */
    val bond: RawBondState,
)

/**
 * One announcement as the platform delivered it, before any of it was read as a device fact.
 *
 * [reportedAddress] is nullable rather than filtered out at the receiver: a broadcast that carried no
 * device is something that happened, and the source turns it into a report with
 * [com.omnibuds.core.platform.DeviceObservationKey.NotReported] so the engine can record the refusal.
 * Dropping it in the receiver would be the silent swallow prompt section 11 refuses.
 *
 * [profile] is null on a link-layer announcement, which names no service - the engine reads that
 * absence as "every profile's claim ends", and it is the one inference this layer makes about the
 * transport hierarchy rather than about a device (ADR-P3-015 rule 3).
 */
sealed interface DeviceAnnouncement {
    data class Link(
        val reportedAddress: String?,
        val profile: ObservedProfile?,
        val link: RawLinkState,
    ) : DeviceAnnouncement

    data class Bond(
        val reportedAddress: String?,
        val bond: RawBondState,
    ) : DeviceAnnouncement
}

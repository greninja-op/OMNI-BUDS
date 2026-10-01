package com.omnibuds.core.platform

/**
 * A connection profile the platform will let an app ask about, the API level it became askable at,
 * and why OmniBuds keeps it in the enumeration union.
 *
 * ADR-P3-002 refuses the obvious reuse for this axis twice over, and both refusals hold here:
 * [com.omnibuds.core.common.TransportKind] names channels OmniBuds could open (and has no member for
 * any of these), and [PlatformFeature] answers a *host* question - "could this phone expose it at
 * all" - while this enum answers a *device* question, "which service reports this link as held".
 * The host-side answer stays in [PlatformFeatureSupport] and [PlatformFeature], and the two must
 * never merge: a device fact read out of a capability report is how "this phone has the API" becomes
 * "these earbuds support the feature".
 *
 * ADR-P3-008 makes this list a maintained decision rather than a transcription of somebody's
 * constant table. There is no call that returns "all connected devices", so the union over these
 * entries *is* the definition of what Phase 3 can see, and every profile absent from it is a class
 * of connection this phase will report nothing about. The [reason] exists to stop a later phase from
 * pruning an entry that looks unused: it looks unused precisely because its job is to be present when
 * a device finally appears.
 *
 * Deliberately absent, each for a cited reason rather than by oversight:
 *  - the deprecated health profile, whose service binding the platform refuses outright, so a
 *    member here would claim an observable class of connection that no mechanism can report;
 *  - the set-coordination and assisted-listening audio profiles whose public *constants* exist but
 *    whose proxy types do not, so nothing typed could be asked of the returned object without naming
 *    a non-public class;
 *  - the server role of the attribute protocol, because Phase 3 runs no server and observing one
 *    another app opened is a different question with different keys;
 *  - every hidden profile the reference implementation carries - audio routing, remote control,
 *    telephony, access point, human-interface device role, the old object-push and call-control
 *    surfaces - none of which is in the compile SDK at all, which is a finding rather than a
 *    restriction to work around (research section 3.2).
 */
enum class ObservedProfile(
    /** Stable identifier for reports and diagnostics. Never a framework name (ADR-P2-015). */
    val technicalName: String,

    /**
     * The API level at which this profile became askable, read from the SDK's own table rather than
     * from memory - the discipline ADR-P2-017 established for every number of this kind.
     */
    val introducedApiLevel: Int,

    /** Why this entry is in the union, and what a later phase has to check before removing it. */
    val reason: String,
) {
    HEADSET(
        technicalName = "profile.headset",
        introducedApiLevel = 11,
        reason = "the call-service link is how a paired earbud set is present while calls are possible, " +
            "and it is one of the two classic audio profiles a device can hold open independently",
    ),
    A2DP(
        technicalName = "profile.a2dp",
        introducedApiLevel = 11,
        reason = "media audio streaming is the profile the product's whole reason for existing sits on, " +
            "and the platform documents it as one connected device at a time, so its absence is news",
    ),
    GATT(
        technicalName = "profile.att-protocol",
        introducedApiLevel = 18,
        reason = "the attribute-protocol link is the only way a never-bonded low-energy device can be " +
            "enumerated at all, and it answers from the manager object without a bound service handle",
    ),
    HID_DEVICE(
        technicalName = "profile.hid-device",
        introducedApiLevel = 28,
        reason = "an input-report link is held by devices neither audio profile lists, so omitting it " +
            "would turn 'the union is silent' into 'nobody is connected' for a real class of hardware",
    ),
    HEARING_AID(
        technicalName = "profile.hearing-aid",
        introducedApiLevel = 29,
        reason = "the regulated hearing-access path reports a set rather than one device, and the " +
            "platform gates its binding on capability rather than API level, so it needs its own entry " +
            "to be able to say unanswerable",
    ),
    LE_AUDIO(
        technicalName = "profile.le-audio",
        introducedApiLevel = 33,
        reason = "low-energy audio reports a set, and in the binaural case two independently " +
            "transitioning entries for one physical product - the shape the reconciler exists for",
    ),
    CSIP_SET_COORDINATOR(
        technicalName = "profile.coordinated-set-joiner",
        introducedApiLevel = 33,
        reason = "the coordinated-set role is what makes an LE Audio pair's two device entries " +
            "attributable to one set; without it they are indistinguishable from two unrelated devices",
    ),
    ;

    /**
     * Whether the OS on a device at [deviceSdk] exposes this profile at all.
     *
     * This is an [ApiAvailability] answer about the operating system and nothing else: a profile can
     * exist at an API level and still be unbindable on a particular handset, which is what
     * [ProfileObservationSupport] is for. The two are kept apart for the reason ADR-P2-008 gives -
     * "the SDK has the class" is not "this phone will answer".
     */
    fun availabilityAt(deviceSdk: Int?): ApiAvailability = apiLevelSupports(deviceSdk, introducedApiLevel)

    companion object {
        /**
         * The maintained union ADR-P3-008 requires a name for: the set of profiles a snapshot
         * enumerates, in the order it enumerates them.
         *
         * An Android implementation binds to exactly this list, and [ConnectedDeviceObserver] holds it
         * so that "the platform did not list this device" can only be read as a disconnect when every
         * profile in it actually answered. A narrower list is a smaller claim, so a caller may pass
         * one to the observer - it may not quietly forget one here.
         */
        val enumerationUnion: List<ObservedProfile> = entries.toList()
    }
}

/**
 * Whether the running platform can answer for one profile's device list at all.
 *
 * The research's blunt finding is that a refused read and an empty room are the same value on every
 * enumeration path, so the engine needs to know which profiles *spoke* before it can read anything
 * out of what they said. This type is that question, and ADR-P3-008's rule attaches to it: a profile
 * whose service binding never arrives is [NOT_ANSWERABLE] and contributes no evidence, never an empty
 * device list and never a silent skip.
 *
 * The wording is deliberately about answering rather than support: a handset can implement a profile
 * and still decline the binding (research section 3.3 lists both cases), and a name that promises
 * "the platform does not have this" would be a claim Phase 3 cannot make from a refusal.
 */
enum class ProfileObservationSupport {
    /** Not asked, or asked and the answer did not arrive. Never collapsed into [NOT_ANSWERABLE]. */
    UNKNOWN,

    /** The mechanism bound and answered, so a list from it is evidence and its silence is evidence. */
    ANSWERABLE,

    /**
     * The platform declined the mechanism: no binding, an unsupported profile, or a service that went
     * away. Devices reachable only through it are unobservable, which is a statement about our view.
     */
    NOT_ANSWERABLE,
}

/** Whether a list from this profile means anything. */
fun ProfileObservationSupport.isAnswerable(): Boolean = this == ProfileObservationSupport.ANSWERABLE

/**
 * Per-profile answerability, as one report rather than as a bag of booleans.
 *
 * Held as a map over [ObservedProfile] so a profile the implementation forgot to ask about reads as
 * [ProfileObservationSupport.UNKNOWN] rather than defaulting to an answer, and so the query methods
 * take the caller's own maintained list - the report must not decide what was supposed to be covered.
 */
data class ProfileSupportReport(
    val entries: Map<ObservedProfile, ProfileObservationSupport>,
) {
    /** What the platform said about [profile], or [ProfileObservationSupport.UNKNOWN] when it said nothing. */
    fun supportFor(profile: ObservedProfile): ProfileObservationSupport =
        entries[profile] ?: ProfileObservationSupport.UNKNOWN

    /**
     * The profiles in [enumerated] whose silence proves nothing.
     *
     * Phrased as what did not answer rather than as what did, because the caller's maintained union is
     * the thing being qualified: a report about a profile nobody asked about is not a licence to treat
     * it as covered.
     */
    fun unansweredWithin(enumerated: Collection<ObservedProfile>): Set<ObservedProfile> =
        enumerated.filterTo(mutableSetOf()) { !supportFor(it).isAnswerable() }
}

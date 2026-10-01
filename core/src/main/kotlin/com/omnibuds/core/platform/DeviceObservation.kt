package com.omnibuds.core.platform

/**
 * How a record reached the projection, which is a fact about the observation and not about the device.
 *
 * Section 9's reconciliation question - "was this read in the union, or announced while we were
 * reading it?" - has no answer in the platform's data, and the difference decides which of two
 * reports is newer when they disagree. A model that drops it cannot implement snapshot-and-event
 * reconciliation without guessing, and guessing is what produces a device that appears connected after
 * it was switched off.
 */
enum class ObservationArrival {
    /** Part of the reconciled union taken when the observation started or was refreshed. */
    SNAPSHOT,

    /** Announced by the platform while the observation was running. */
    EVENT,
}

/**
 * Everything the platform said about one device in one round, with nothing added.
 *
 * Every field here is a platform report, and the two rules that keep it that way are the ones a later
 * phase will be tempted to break. The first is that an unread field stays null: no empty string, no
 * whitespace placeholder, no "unknown" literal and no value inferred from a brand-typical name
 * (ADR-P0-016, and [DeviceObservation.reported] is the only construction path that enforces it). The
 * second is that the three axes of ADR-P3-001 are three fields, so a transition can say which one
 * moved - a device in a drawer is [DeviceBondState.BONDED] and [DeviceConnectionState.DISCONNECTED]
 * simultaneously, and no member of either enum can express that on its own.
 *
 * Deliberately absent: manufacturer, model, device class and any earbud-or-bud-side classification.
 * Prompt section 16 forbids the inference, and the research forecloses the principled version of it:
 * the class-of-device value is documented as a hint that "does not reliably describe which profiles or
 * services are actually supported", and its own profile-matching heuristic "tries to err on the side
 * of false positives". A pair of earbuds can report any of four different classes across firmware
 * updates, so a field here that looked like a harmless icon choice would be a claim about hardware
 * that no report supports.
 *
 * Equality on this type is a comparison of *reports*, not a join: [key] alone attributes records to
 * one device, and [mergedWith] refuses to run on keys that cannot join. Two records that differ only
 * in [displayName] are two different things the platform said, which is not the same question as
 * whether they are about the same device.
 */
data class DeviceObservation(
    /** What the platform gave us to attribute this device by. Absence is [DeviceObservationKey.NotReported]. */
    val key: DeviceObservationKey,

    /** Name as reported, or null. Never identity, never a join, never an empty string. */
    val displayName: String?,

    /** The link axis: what the stack says about this device's connection, and nothing else. */
    val link: DeviceConnectionState,

    /** The bond axis: whether a pairing exists. Independent of [link] by construction. */
    val bond: DeviceBondState,

    /** The availability axis: whether the platform would tell us about this device at all. */
    val availability: DeviceAvailability,

    /**
     * The profiles that currently report a live link to this device.
     *
     * A set rather than one value because prompt section 12 requires a device held by two services to
     * be one device, and because an aggregate link state is only honest if the evidence behind it
     * survives: when the audio profile drops but the call profile is still connected, the device is
     * connected, and the only way to say that afterwards is to have kept both names.
     */
    val observedProfiles: Set<ObservedProfile>,

    /** Snapshot or event, which is what makes section 9's ordering decidable. */
    val arrival: ObservationArrival,

    /** When the platform said it, or null when it supplied no reading - never zero (ADR-P1-012). */
    val observedAtEpochMillis: Long?,
) {
    /** Whether this record can be attributed to a device in a later round. False for an unkeyed report. */
    val isAttributable: Boolean
        get() = key.canIdentifyAcrossObservations

    /** True only when the platform both would tell us about this device and says a link is up. */
    val isReportedConnected: Boolean
        get() = availability.isObservable() && link.isConnected()

    /** Whether a name came with the report. Blank text does not count as a name. */
    val hasReportedName: Boolean
        get() = normalisedText(displayName) != null

    /**
     * Fills what this record does not know from [other] and combines what both know.
     *
     * Used for the one case prompt section 12 names: the same device arriving from two profiles in one
     * union. It is only legal on joinable keys, and says so by refusing otherwise, because a merge of
     * two unkeyed reports is the device-welding defect this whole design exists to avoid.
     *
     * Where the two disagree the answer is the one with more evidence behind it, and the ordering is
     * stated rather than left to whichever argument happens to be first: a positive report beats an
     * unread one, a live link beats a link still coming up, and a link coming down beats a link that is
     * already down. Nothing here averages, and nothing here picks a winner between two positive
     * reports of different kinds - a [DeviceBondState.BONDED] record keeps that answer against a
     * [DeviceBondState.NONE] one, because two mechanisms disagreeing is a finding for a human, not
     * something a data class may splice into a hybrid.
     */
    fun mergedWith(other: DeviceObservation): DeviceObservation {
        require(key.joinableWith(other.key)) {
            "two observations that cannot be attributed to one device must not be merged"
        }
        return DeviceObservation(
            key = key,
            displayName = normalisedText(displayName) ?: normalisedText(other.displayName),
            link = strongerLink(link, other.link),
            bond = strongerBond(bond, other.bond),
            availability = strongerAvailability(availability, other.availability),
            observedProfiles = observedProfiles + other.observedProfiles,
            // The merge runs in the order the reports arrived, so the incoming one is the later arrival.
            arrival = other.arrival,
            observedAtEpochMillis = latestReading(observedAtEpochMillis, other.observedAtEpochMillis),
        )
    }

    companion object {
        /**
         * The only construction path a platform adapter should use: it demotes blank reported text to
         * null and keeps every other value exactly as reported.
         *
         * The primary constructor is public because a domain type may not silently rewrite evidence,
         * and a caller who genuinely means an empty name - which is nobody - can say so by passing
         * null through here.
         */
        fun reported(
            key: DeviceObservationKey,
            link: DeviceConnectionState,
            bond: DeviceBondState,
            availability: DeviceAvailability,
            observedProfiles: Set<ObservedProfile> = emptySet(),
            displayName: String? = null,
            arrival: ObservationArrival = ObservationArrival.SNAPSHOT,
            observedAtEpochMillis: Long? = null,
        ): DeviceObservation = DeviceObservation(
            key = key,
            displayName = normalisedText(displayName),
            link = link,
            bond = bond,
            availability = availability,
            observedProfiles = observedProfiles,
            arrival = arrival,
            observedAtEpochMillis = observedAtEpochMillis,
        )

        /**
         * Trims reported text and maps blank to null, so a padded name cannot be read as a known field.
         *
         * [com.omnibuds.core.device.DeviceIdentity] applies the same rule to its own fields and is
         * unreachable from here - layer 1 may not import layer 2 - so this is the second copy of one
         * sentence, which is cheaper than the upward edge that putting the observation next to the
         * identity would have bought (ADR-P3-003's refusal of that placement).
         */
        private fun normalisedText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

        private fun strongerLink(
            first: DeviceConnectionState,
            second: DeviceConnectionState,
        ): DeviceConnectionState = maxOf(first, second, linkStrength)

        private fun strongerBond(first: DeviceBondState, second: DeviceBondState): DeviceBondState =
            if (bondRank(first) >= bondRank(second)) first else second

        private fun strongerAvailability(
            first: DeviceAvailability,
            second: DeviceAvailability,
        ): DeviceAvailability = if (availabilityRank(first) >= availabilityRank(second)) first else second

        private fun bondRank(bond: DeviceBondState): Int = when (bond) {
            DeviceBondState.BONDED -> 3
            DeviceBondState.BONDING -> 2
            DeviceBondState.NONE -> 1
            DeviceBondState.UNKNOWN -> 0
        }

        private fun availabilityRank(availability: DeviceAvailability): Int = when (availability) {
            DeviceAvailability.AVAILABLE -> 2
            DeviceAvailability.UNAVAILABLE -> 1
            DeviceAvailability.UNKNOWN -> 0
        }

        private fun latestReading(first: Long?, second: Long?): Long? = when {
            first == null -> second
            second == null -> first
            else -> maxOf(first, second)
        }

        /**
         * Link states ordered by how much they claim, so a merge cannot lose a live link.
         *
         * A device the audio profile reports as connected and the call profile reports as disconnected
         * is connected, and the general rule behind that case is the one this comparator encodes: the
         * more positive report wins, and [DeviceConnectionState.UNKNOWN] - the report of nobody - is
         * last rather than first, so it never overrules something.
         */
        private val linkStrength: Comparator<DeviceConnectionState> = compareBy { linkRank(it) }

        private fun linkRank(link: DeviceConnectionState): Int = when (link) {
            DeviceConnectionState.UNKNOWN -> 0
            DeviceConnectionState.DISCONNECTED -> 1
            DeviceConnectionState.DISCONNECTING -> 2
            DeviceConnectionState.CONNECTING -> 3
            DeviceConnectionState.CONNECTED -> 4
        }
    }
}

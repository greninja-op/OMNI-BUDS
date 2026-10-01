package com.omnibuds.core.platform

/**
 * What the platform gave us that can attribute two reports to one device, or the explicit fact that
 * it gave us nothing.
 *
 * ADR-P3-010 sets the three rules this type exists to hold, and each of them is a failure mode the
 * alternative shape produces:
 *
 *  - **Absence is a value.** [NotReported] is a member rather than a nullable reference, because a
 *    caller that receives `null` will write `?: someOtherKey` or skip the record, and both of those
 *    turn "the platform named nobody" into a decision about devices. A caller has to handle the case
 *    to get anywhere.
 *  - **Equality is the join.** Two keys are the same device when they are equal and nothing else:
 *    there is no name, no class, no ordering and no proximity in the comparison, because a display
 *    name is a string two products ship identically (prompt section 12) and the engine that merges on
 *    it would silently weld two of a user's devices into one record.
 *  - **Redaction is a property of the type.** [toString] states the kind and the length and cannot
 *    state the value, because the value is a hardware address of a person's belongings and prompt
 *    section 13 forbids it reaching an ordinary log. A bare [String] key would leak through every
 *    interpolation and every exception message with nothing to stop it, which is the lesson
 *    ADR-P2-015 and SEC-LOG-002 already paid for.
 *
 * What this is *not*: identity. [com.omnibuds.core.device.DeviceIdentity] deliberately has no address
 * field, and an address that may be a rotating random value on a low-energy link is not a statement
 * about which device something is (architecture audit section 6, research section 5.4). Anything that
 * outlives this process is a retention decision for a later phase with its own justification.
 */
sealed interface DeviceObservationKey {
    /**
     * Whether this key can attribute one report to one device across reports.
     *
     * [NotReported] answers false, and that answer is load-bearing: two records that both carry
     * [NotReported] are equal as values and are *not* the same device. Any engine that joins on
     * [equals] alone will merge them, which is prompt section 12's prohibited action, so the
     * predicate has to be consulted first. [joinableWith] is that consultation made unavoidable.
     */
    val canIdentifyAcrossObservations: Boolean

    /**
     * A key backed by the link address the platform reported for a remote device.
     *
     * The value is private and unreadable on purpose: equality and hashing are the only way out, so a
     * caller cannot hand an address to a log formatter, a UI string or a persisted field without
     * changing this file. The research records that for a bonded classic device the address is
     * effectively stable while for a never-bonded low-energy one it may rotate, and that this is
     * device-level knowledge rather than documented fact - so the designed failure mode of a wrong
     * answer is "the same product appears twice", never "two products became one".
     */
    class AddressBacked private constructor(private val value: String) : DeviceObservationKey {
        override val canIdentifyAcrossObservations: Boolean
            get() = true

        override fun equals(other: Any?): Boolean = this === other || (other is AddressBacked && other.value == value)

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String =
            "DeviceObservationKey.AddressBacked(kind=link-address, length=${value.length})"

        companion object {
            /**
             * Builds a key from an address as reported, or null when the report was not one.
             *
             * The only route to an [AddressBacked], which is what keeps the blank-text demotion below
             * from being a convention a call site can skip: a value of this type always wraps something
             * a platform actually named.
             */
            fun from(reported: String?): AddressBacked? = reported
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.uppercase()
                ?.let(::AddressBacked)
        }
    }

    /**
     * The platform reported a device without giving anything to attribute it by.
     *
     * Equal to itself as a value, which is exactly why [canIdentifyAcrossObservations] is false: this
     * says "no key was given", not "this is the device with no key".
     */
    data object NotReported : DeviceObservationKey {
        override val canIdentifyAcrossObservations: Boolean
            get() = false

        override fun toString(): String = "DeviceObservationKey.NotReported"
    }

    companion object {
        /**
         * Builds a key from an address as reported, and [NotReported] from anything that is not one.
         *
         * Blank and whitespace-only text are demoted rather than kept, because an empty address string
         * is one of the falsy platform returns Phase 2 recorded (ADR-P2-012): treating it as a real key
         * would hand every device the platform was silent about a shared identity. Surrounding space is
         * trimmed and letters are upper-cased so that the same address reaching us through two
         * mechanisms in two conventions still joins - a normalisation of the *comparison*, performed
         * here once so no call site can do it differently, and not a rewrite of what was observed.
         */
        fun ofReportedAddress(reported: String?): DeviceObservationKey =
            AddressBacked.from(reported) ?: NotReported
    }
}

/**
 * The join rule, stated once, in the one form that cannot be misused.
 *
 * Two keys may attribute reports to one device only when both can identify across observations and
 * are equal. This is what keeps a pair of [DeviceObservationKey.NotReported] records two records
 * (prompt section 12), and it is why the engine's lookups go through here rather than through a map
 * keyed by equality alone.
 */
fun DeviceObservationKey.joinableWith(other: DeviceObservationKey): Boolean =
    canIdentifyAcrossObservations && other.canIdentifyAcrossObservations && this == other

/** True when this key attributes nothing, so a report carrying it cannot be merged with anything. */
fun DeviceObservationKey.isUnattributable(): Boolean = !canIdentifyAcrossObservations

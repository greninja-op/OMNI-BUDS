package com.omnibuds.core.transport

/**
 * GATT attribute identity and constraints, expressed as data a caller supplies — never as a value this
 * layer invents.
 *
 * Phase 1 and Phase 2 withheld every GATT-shaped member because opening a channel was not theirs to do
 * (`transport-boundaries.md` §4, OQ-PROTO-01). Phase 6 owns that mechanics, and the members it adds take
 * their identifiers *from the caller*: PROTO-NOMAGIC-002 forbids a device service or characteristic UUID
 * standing in code as the evidence for a capability, and PROTO-ABST-006 forbids a transport primitive
 * expressing protocol meaning. So these types name the *shape* of an attribute — a UUID, a property set, a
 * payload bound — and hold no vendor value at all; which UUIDs matter and what their bytes mean is the
 * protocol layer's later decision, against a research ladder this phase never reaches.
 */

/** One 16/32/128-bit GATT UUID as text; blank is refused rather than defaulted. */
data class GattUuid(val value: String) {
    init {
        require(value.isNotBlank()) { "a GATT UUID is a non-blank identifier; empty means none was given" }
    }

    /** Upper-cased for comparison, so `180f` and `180F` are one identity (UUIDs are case-insensitive). */
    val normalised: String get() = value.trim().uppercase()
}

/** The operations one characteristic permits, exactly as the attribute's declaration reports them. */
enum class CharacteristicProperty {
    READ,
    WRITE,
    WRITE_NO_RESPONSE,
    NOTIFY,
    INDICATE,
}

/**
 * A characteristic's discovered identity.
 *
 * [properties] is what the *device* declared for this attribute, not what OmniBuds wishes it supported:
 * a write to a characteristic absent from the set is refused by the platform, and the transport reports
 * that refusal (prompt §9's "do not assume every characteristic is writable"). [maxValueBytes] is the
 * payload ceiling for one operation at the current MTU — `null` while the MTU is not yet established,
 * which is distinct from a zero bound.
 */
data class GattCharacteristic(
    val service: GattUuid,
    val characteristic: GattUuid,
    val properties: Set<CharacteristicProperty>,
    val maxValueBytes: Int?,
) {
    val isReadable: Boolean get() = CharacteristicProperty.READ in properties
    val isWritable: Boolean
        get() = CharacteristicProperty.WRITE in properties ||
            CharacteristicProperty.WRITE_NO_RESPONSE in properties
    val isNotifiable: Boolean
        get() = CharacteristicProperty.NOTIFY in properties ||
            CharacteristicProperty.INDICATE in properties
}

/** A discovered service and the characteristics under it. An empty list means "none found", not "unread". */
data class GattService(
    val service: GattUuid,
    val characteristics: List<GattCharacteristic>,
)

/**
 * The negotiated ATT payload size.
 *
 * The "effective" size, because a device and a phone each propose an MTU and the smaller wins: this is the
 * bound on one [GattCharacteristic]'s value, and a payload that exceeds it cannot go in a single write.
 * [MAX_ATT_PAYLOAD_OFFSET] documents the fixed attribute-protocol header that eats into the negotiated MTU
 * so a caller reasoning about how many bytes it may send does not send `mtu` payload bytes and lose one.
 */
data class MtuInfo(
    val negotiatedMtu: Int,
) {
    init {
        require(negotiatedMtu >= MIN_ATT_MTU) {
            "an ATT MTU below $MIN_ATT_MTU is not a real value; MIN_ATT_MTU is the protocol minimum"
        }
    }

    /** Bytes usable for one attribute payload after the fixed ATT header. Never negative. */
    val usablePayloadBytes: Int get() = (negotiatedMtu - MAX_ATT_PAYLOAD_OFFSET).coerceAtLeast(0)

    companion object {
        private const val MIN_ATT_MTU = 23
        private const val MAX_ATT_PAYLOAD_OFFSET = 3
    }
}

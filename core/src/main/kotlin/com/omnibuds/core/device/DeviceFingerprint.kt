package com.omnibuds.core.device

import com.omnibuds.core.common.TransportKind

/**
 * The evidence a device presented, used to decide which device this is and which
 * protocol or transport might apply (Phase 1 prompt sections 7 and 10, master
 * section 7).
 *
 * This is a record of observations, not a conclusion. It answers "what did we see?"
 * for a later identification step; it never answers "what is this device?", because
 * that answer requires a protocol record with an evidence tier behind it
 * (ADR-P0-018, master section 55).
 *
 * Empty collections mean "nothing was seen during this discovery pass". They do not
 * mean "this device has no manufacturer data" or "this device exposes no services" —
 * those are positive establishments and belong in the capability layer as
 * `CapabilityState.UNSUPPORTED`, produced only from evidence
 * (`docs/phases/phase-0/specs.md` section 2.2). [firmware] is null for the same
 * reason: no firmware read has happened yet, which is not a claim that none exists.
 *
 * Deliberately absent: the Bluetooth address ([identityKey] must stay address-free
 * for the same reason — SEC-ID-001, SEC-ID-003, SEC-ID-004), battery, and any
 * timestamp, since none of them identify a device and all of them change between
 * sessions.
 */
data class DeviceFingerprint(
    /** Manufacturer records as observed, in the order they were seen. Empty means nothing seen. */
    val manufacturerData: List<ManufacturerDataEntry> = emptyList(),

    /** Service UUIDs as text, as observed. Empty means nothing seen, not none exist. */
    val serviceUuids: Set<String> = emptySet(),

    /** Characteristic UUIDs as text, keyed by the service they were found under. */
    val characteristicUuids: Map<String, List<String>> = emptyMap(),

    /** Class-of-device value as reported, or null when it was not reported. */
    val deviceClass: Int? = null,

    /**
     * Transports that might reach this device. A [TransportKind.UNKNOWN] inside the
     * set asserts no knowledge, so it contributes nothing to [identityKey].
     */
    val transportCandidates: Set<TransportKind> = emptySet(),

    /** Protocol families that might apply, as candidate names only, with no claim of a match. */
    val protocolCandidates: List<String> = emptyList(),

    /** Version evidence if a firmware read has happened; null while none has. */
    val firmware: FirmwareInfo? = null,
) {
    /** True when this pass produced no evidence in any dimension. */
    val isEntirelyUnobserved: Boolean
        get() = manufacturerData.all { it.isEntirelyUnobserved } &&
            serviceUuids.none { DeviceIdentity.normalised(it) != null } &&
            characteristicUuids.isEmpty() &&
            deviceClass == null &&
            transportCandidates.none { it != TransportKind.UNKNOWN } &&
            protocolCandidates.none { DeviceIdentity.normalised(it) != null }

    /**
     * A deterministic, order-stable key over the structural evidence in this
     * fingerprint, for recognising the same device across reconnects.
     *
     * What goes in is limited to what stays the same across reconnects: manufacturer
     * records, service and characteristic UUIDs, device class, and the transport and
     * protocol candidate sets. Sets and lists are sorted, so discovery arriving in a
     * different order yields the same key. What stays out is everything that moves:
     * [firmware] (an update changes the version, not the device), connection state,
     * battery, and every timestamp — none of which are in this type in the first place.
     *
     * No Bluetooth MAC address can appear in the key. This type holds no address
     * field, and the canonical address separator `':'` is folded out of every token,
     * so an address cannot be smuggled in through a free-text field either. The key
     * is therefore a coarse bucketing device, not a lossless encoding: two
     * fingerprints whose evidence differs only in address-style punctuation collide
     * by design, and code that must tell them apart compares the fields. Identity
     * comes from the fingerprint, never from an address (SEC-ID-003).
     *
     * A fingerprint with nothing observed returns [NOTHING_KNOWN_KEY] verbatim, so
     * "we know nothing" is an explicit, recognisable value rather than a key that
     * looks like a sparse but real device.
     */
    fun identityKey(): String {
        if (isEntirelyUnobserved) return NOTHING_KNOWN_KEY

        val transports = transportCandidates
            .filterNot { it == TransportKind.UNKNOWN }
            .map { it.name }
        val protocols = protocolCandidates
            .mapNotNull { DeviceIdentity.normalised(it) }
            .map { it.uppercase() }

        return buildString {
            append(KEY_PREFIX)
            append(SECTION_SEPARATOR).append("md=").append(manufacturerDataSection())
            append(SECTION_SEPARATOR).append("svc=").append(
                sortedTokens(serviceUuids.map { it.uppercase() }),
            )
            append(SECTION_SEPARATOR).append("chr=").append(characteristicSection())
            append(SECTION_SEPARATOR).append("dc=").append(deviceClass?.toString() ?: "")
            append(SECTION_SEPARATOR).append("tr=").append(sortedTokens(transports))
            append(SECTION_SEPARATOR).append("pc=").append(sortedTokens(protocols))
        }
    }

    /**
     * Manufacturer records as sorted key tokens. Each record is `company.payload`;
     * the payload is escaped on its own so that the `.` between the two halves stays
     * a structural separator rather than evidence text.
     */
    private fun manufacturerDataSection(): String = manufacturerData
        .filterNot { it.isEntirelyUnobserved }
        .map { entry -> manufacturerDataToken(entry) }
        .sorted()
        .joinToString(TOKEN_SEPARATOR)

    private fun manufacturerDataToken(entry: ManufacturerDataEntry): String {
        val company = entry.companyId?.toString() ?: NO_VALUE
        val payload = entry.dataHex?.uppercase()?.let { raw -> keyToken(raw) } ?: NO_VALUE
        return "$company.$payload"
    }

    private fun characteristicSection(): String = characteristicUuids.entries
        .sortedBy { entry -> entry.key.uppercase() }
        .joinToString(TOKEN_SEPARATOR) { entry ->
            val characteristics = entry.value
                .map { characteristic -> keyToken(characteristic.uppercase()) }
                .sorted()
                .joinToString(CHARACTERISTIC_SEPARATOR)
            "${keyToken(entry.key.uppercase())}$PAIR_SEPARATOR$characteristics"
        }

    private fun sortedTokens(values: List<String>): String = values
        .map { value -> keyToken(value) }
        .sorted()
        .joinToString(TOKEN_SEPARATOR)

    /**
     * Makes a token safe to place inside a delimited key: address punctuation folded,
     * separators escaped, blanks collapsed to [NO_VALUE].
     */
    private fun keyToken(raw: String): String {
        val trimmed = DeviceIdentity.normalised(raw) ?: return NO_VALUE
        return trimmed
            .replace(ADDRESS_SEPARATOR, PUNCTUATION_SUBSTITUTE)
            .replace("\\", "\\\\")
            .replace(SECTION_SEPARATOR, "\\;")
            .replace(TOKEN_SEPARATOR, "\\,")
            .replace(PAIR_SEPARATOR, "\\=")
            .replace(CHARACTERISTIC_SEPARATOR, "\\+")
    }

    companion object {
        private const val KEY_PREFIX = "omnibuds-fingerprint/v1"

        /** The key of a fingerprint that observed nothing, stated in the key itself. */
        const val NOTHING_KNOWN_KEY: String = "$KEY_PREFIX=nothing-known"

        private const val SECTION_SEPARATOR = ";"
        private const val TOKEN_SEPARATOR = ","
        private const val PAIR_SEPARATOR = "="
        private const val CHARACTERISTIC_SEPARATOR = "+"
        private const val ADDRESS_SEPARATOR = ":"
        private const val PUNCTUATION_SUBSTITUTE = "-"

        /** Placeholder for an absent half of a manufacturer record; never empty text. */
        private const val NO_VALUE = "-"

        /** A fingerprint holding no evidence at all: nothing was observed, yet. */
        fun empty(): DeviceFingerprint = DeviceFingerprint()
    }
}

package com.omnibuds.core.testing

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.persistence.ProtocolRecordAccess

/**
 * A scripted stand-in for the global protocol knowledge base.
 *
 * **THIS IS NOT PROTOCOL SUPPORT AND NOT A PROTOCOL DATABASE.** It is a test double confined
 * to `src/test`, where a production wiring cannot reach it (Phase 1 prompt sections 26, 27
 * and 53). It holds no commands, no byte sequences, no parsers and no vendor knowledge: the
 * only content it can return is a string a test explicitly typed into it. A test that passes
 * against this type has established that the code under test asks the right question of a
 * protocol seam - nothing whatsoever about whether any real protocol is understood.
 *
 * **It knows nothing by default.** With nothing scripted, every fingerprint matches zero
 * records and every id resolves to `null`, which mirrors the state a real install begins in:
 * an unknown device, looked up honestly, answered with "nothing documented", and therefore
 * read-only (SEC-UNK-001). A test that needs a match must opt in through [scriptCandidates],
 * so "the protocol was identified" can never be the accidental result of a generous fake.
 * That is the difference between this type and the failure mode the phase is graded on: a
 * double that quietly answers with a plausible protocol id lets a caller look like it
 * identified a device when nothing was ever documented.
 *
 * Candidate scripting is keyed on `DeviceFingerprint.identityKey()`, the same address-free key
 * the persistence layer uses, so a test scripts against evidence rather than against an
 * address. Not thread-safe; use from one test coroutine.
 */
class FakeProtocolRecordAccess : ProtocolRecordAccess {

    private val candidatesByKey: MutableMap<String, MutableList<String>> = mutableMapOf()
    private val summariesById: MutableMap<String, String> = mutableMapOf()

    /** The key of every fingerprint passed to [recordsFor], in call order. */
    val queriedKeys: MutableList<String> = mutableListOf()

    /** Every id passed to [record], in call order. */
    val recordLookups: MutableList<String> = mutableListOf()

    /** Verdicts queued for [recordsFor]; consulted only when non-empty. */
    val recordsForVerdicts: ScriptedOutcome<List<String>> = ScriptedOutcome()

    /** Verdicts queued for [record]; consulted only when non-empty. */
    val recordVerdicts: ScriptedOutcome<String?> = ScriptedOutcome()

    /**
     * Declares that [fingerprint]'s evidence matches [protocolIds], in the order a caller may
     * try them.
     *
     * Explicit opt-in: without this call the answer is an empty list, and an empty list is
     * what keeps a caller in read-only mode.
     */
    fun scriptCandidates(fingerprint: DeviceFingerprint, vararg protocolIds: String): FakeProtocolRecordAccess {
        val bucket = candidatesByKey.getOrPut(fingerprint.identityKey()) { mutableListOf() }
        protocolIds.forEach { protocolId -> bucket.add(protocolId) }
        return this
    }

    /**
     * Declares the text [protocolId] resolves to.
     *
     * Deliberately a plain string, matching [ProtocolRecordAccess]: this double holds no
     * commands, no parsers and no protocol definitions, because a fake that could materialise
     * one would let a test look as though it exercised real protocol knowledge. Only what a
     * test typed in can come out.
     */
    fun scriptSummary(protocolId: String, summary: String): FakeProtocolRecordAccess = apply {
        summariesById[protocolId] = summary
    }

    override suspend fun recordsFor(fingerprint: DeviceFingerprint): OperationOutcome<List<String>> {
        val key = fingerprint.identityKey()
        queriedKeys.add(key)
        if (recordsForVerdicts.hasNext) return recordsForVerdicts.next()
        val scripted = candidatesByKey[key]?.toList() ?: emptyList()
        return OperationOutcome.Success(scripted)
    }

    override suspend fun record(protocolId: String): OperationOutcome<String?> {
        recordLookups.add(protocolId)
        if (protocolId.isBlank()) {
            return OperationOutcome.Failure(
                OmniBudsError.of(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = OPERATION_RECORD,
                    detail = "$DOUBLE_MARKER refusing a lookup: a blank protocol id names no record, " +
                        "and resolving one would be inventing knowledge the caller never had",
                ),
            )
        }
        if (recordVerdicts.hasNext) return recordVerdicts.next()
        return OperationOutcome.Success(summariesById[protocolId])
    }

    companion object {
        /** Stamped into every refusal so a leaked double is identifiable in any log line. */
        const val DOUBLE_MARKER: String = "FakeProtocolRecordAccess [test double, no protocol support]"

        private const val OPERATION_RECORD = "persistence.protocol.record"
    }
}

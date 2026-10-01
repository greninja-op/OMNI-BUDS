package com.omnibuds.core.protocol

import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CommandDefinition] as the place the retry policy is declared rather than improvised
 * (Phase 0 `specs.md` section 4 rule 3, section 5.4, PROTO-ERR-003, SEC-RES-004).
 *
 * Tier T1. Fictional command names following the `readBatteryStatus` naming rule of
 * specs.md section 1.5; no bytes and no real protocol operation are implied.
 */
class CommandDefinitionTest {

    @Test
    fun aWriteCannotCarryAReadAttemptBudget() {
        assertFailsWith<IllegalArgumentException> {
            command(effectClass = EffectClass.SIDE_EFFECTING_WRITE, maxReadAttempts = 3)
        }
        assertFailsWith<IllegalArgumentException> {
            command(effectClass = EffectClass.IRREVERSIBLE_WRITE, maxReadAttempts = 1)
        }
    }

    @Test
    fun aWriteCarriesNoAttemptBudgetAtAll() {
        val write = command(effectClass = EffectClass.SIDE_EFFECTING_WRITE, maxReadAttempts = null)

        assertNull(write.maxReadAttempts)
    }

    @Test
    fun aReadMustDeclareAnAttemptBudgetAndCannotEscapeTheCeiling() {
        assertFailsWith<IllegalArgumentException> {
            command(effectClass = EffectClass.READ, maxReadAttempts = null)
        }
        assertFailsWith<IllegalArgumentException> {
            command(effectClass = EffectClass.READ, maxReadAttempts = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            command(
                effectClass = EffectClass.READ,
                maxReadAttempts = CommandDefinition.MAX_READ_ATTEMPTS + 1,
            )
        }

        val bounded = command(effectClass = EffectClass.READ, maxReadAttempts = 2)
        assertEquals(2, bounded.maxReadAttempts)
        assertEquals(1, CommandDefinition.MIN_READ_ATTEMPTS)
        assertEquals(5, CommandDefinition.MAX_READ_ATTEMPTS)
    }

    @Test
    fun aNonPositiveTimeoutIsRefusedWhileAnAbsentBoundStaysAbsent() {
        assertFailsWith<IllegalArgumentException> { command(timeoutMillis = 0) }
        assertFailsWith<IllegalArgumentException> { command(timeoutMillis = -250) }

        val unbounded = command(timeoutMillis = null)
        assertNull(unbounded.timeoutMillis)
    }

    @Test
    fun retryPermissionIsInheritedFromTheEffectClassAndCannotContradictIt() {
        val read = command(effectClass = EffectClass.READ, maxReadAttempts = 2)
        val write = command(effectClass = EffectClass.SIDE_EFFECTING_WRITE, maxReadAttempts = null)

        assertTrue(read.permitsAutomaticRetry)
        assertFalse(read.isWrite)
        assertFalse(write.permitsAutomaticRetry)
        assertTrue(write.isWrite)
    }

    @Test
    fun anInferredCommandIsRecordedButIsNotFitForAControl() {
        val inferred = command(confidence = VerificationLevel.INFERRED)
        val labTested = command(confidence = VerificationLevel.LAB_TESTED)

        // SEC-RES-004: inference is data, not a feature. The record stays usable for
        // parsing and bookkeeping while no control may be built from it.
        assertFalse(inferred.canBeExposedAsControl)
        assertTrue(labTested.canBeExposedAsControl)
        assertEquals(VerificationLevel.INFERRED, inferred.confidence)
    }

    @Test
    fun anOperationIsNamedByItsIdentityAndABlankNameIsRefused() {
        val named = command(id = "readExampleBatteryLevel")

        assertEquals("readExampleBatteryLevel", named.id)
        assertFailsWith<IllegalArgumentException> { command(id = "   ") }
        assertFailsWith<IllegalArgumentException> { command(displayName = "") }
    }

    private fun command(
        id: String = DEFAULT_ID,
        displayName: String = "Example battery level read",
        effectClass: EffectClass = EffectClass.READ,
        timeoutMillis: Long? = 400,
        maxReadAttempts: Int? = DEFAULT_READ_ATTEMPTS,
        confidence: VerificationLevel = VerificationLevel.IMPLEMENTED,
    ): CommandDefinition = CommandDefinition(
        id = id,
        displayName = displayName,
        effectClass = effectClass,
        timeoutMillis = timeoutMillis,
        maxReadAttempts = maxReadAttempts,
        confidence = confidence,
    )

    private companion object {
        const val DEFAULT_ID = "readExampleStatus"
        const val DEFAULT_READ_ATTEMPTS = 2
    }
}

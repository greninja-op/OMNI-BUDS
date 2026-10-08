package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * OB-P12-REQ-021: rollback where a previous confirmed configuration exists.
 */
class CodecControlRollbackTest {

    private val device = testDevice()

    /**
     * Scripted adapter: first select (AAC) verifies; second select (LDAC)
     * applies but observation stays AAC → verification fails → rollback to
     * AAC is attempted and verifies.
     */
    @Test
    fun `failed select rolls back to previous confirmed codec`() = runTest {
        var observedCodec = Codec.AAC
        val adapter = object : CodecControlAdapter {
            override suspend fun apply(operation: CodecOperation): CodecApplyOutcome =
                CodecApplyOutcome.Performed
            override suspend fun observeAfterApply(
                device: com.omnibuds.core.device.DeviceIdentity,
                codec: Codec,
            ): CodecConfiguration? {
                // The "hardware" only ever reports AAC, except we let the
                // rollback verification see AAC (which is the truth).
                return CodecConfiguration(observedCodec)
            }
        }
        val engine = CodecControlEngine(
            FakeResolver(
                mapOf(
                    Codec.AAC to fullControlCapability(Codec.AAC),
                    Codec.LDAC to fullControlCapability(Codec.LDAC),
                ),
            ),
            adapter,
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
        // Step 1: confirm AAC.
        val first = engine.execute(
            CodecOperation.select(
                device, Codec.AAC, "op-1",
                verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
            ),
        )
        assertIs<CodecOperationResult.Verified>(first)
        assertEquals(Codec.AAC, engine.states.value[device]!!.confirmedCodec)

        // Step 2: request LDAC; the platform applies but still reports AAC.
        observedCodec = Codec.AAC
        val second = engine.execute(
            CodecOperation.select(
                device, Codec.LDAC, "op-2",
                verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
            ),
        )
        assertIs<CodecOperationResult.VerificationFailed>(second)

        // Rollback re-selected AAC and verified it: confirmed is AAC again.
        val state = engine.states.value[device]!!
        assertEquals(Codec.AAC, state.confirmedCodec)
        assertEquals(Codec.LDAC, state.requestedCodec)
    }

    @Test
    fun `no previous confirmed state means no rollback - state marked uncertain`() = runTest {
        val adapter = FakeAdapter(
            applyOutcome = CodecApplyOutcome.Performed,
            observed = CodecConfiguration(Codec.AAC), // Mismatch: requested LDAC.
        )
        val engine = CodecControlEngine(
            FakeResolver(mapOf(Codec.LDAC to fullControlCapability(Codec.LDAC))),
            adapter,
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
        val result = engine.execute(
            CodecOperation.select(
                device, Codec.LDAC, "op-1",
                verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
            ),
        )
        assertIs<CodecOperationResult.VerificationFailed>(result)
        // No previous confirmed codec existed; nothing to roll back to.
        val state = engine.states.value[device]!!
        assertEquals(Codec.UNKNOWN, state.confirmedCodec)
    }
}

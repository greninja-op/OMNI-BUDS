package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

/**
 * OB-P12-REQ-023: cancellation safety.
 */
class CodecControlCancellationTest {

    private val device = testDevice()

    @Test
    fun `cancelling the operation propagates CancellationException and commits nothing`() = runTest {
        val adapter = FakeAdapter(
            applyOutcome = CodecApplyOutcome.Performed,
            observed = CodecConfiguration(Codec.LDAC),
            applyDelayMillis = 5_000L,
        )
        val engine = CodecControlEngine(
            FakeResolver(mapOf(Codec.LDAC to fullControlCapability(Codec.LDAC))),
            adapter,
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
        val job = async {
            engine.execute(
                CodecOperation.select(
                    device, Codec.LDAC, "op-1",
                    timeoutMillis = 60_000L,
                    verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
                ),
            )
        }
        delay(50)
        job.cancel()
        assertFailsWith<CancellationException> {
            job.await()
        }
        val state = engine.states.value[device]
        assertTrue(
            state == null || state.confirmedCodec != Codec.LDAC,
            "a cancelled operation must never commit confirmed state",
        )
    }

    @Test
    fun `cancellation before apply leaves no requested residue as confirmed`() = runTest {
        val adapter = FakeAdapter(applyOutcome = CodecApplyOutcome.Performed)
        val engine = CodecControlEngine(
            FakeResolver(mapOf(Codec.LDAC to fullControlCapability(Codec.LDAC))),
            adapter,
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
        // Cancel the surrounding scope immediately: precheck passes, but the
        // device lock acquisition is cancelled.
        val job = async {
            engine.execute(CodecOperation.select(device, Codec.LDAC, "op-1"))
        }
        job.cancel()
        assertFailsWith<CancellationException> { job.await() }
        val state = engine.states.value[device]
        assertTrue(state == null || state.confirmedCodec != Codec.LDAC)
    }
}

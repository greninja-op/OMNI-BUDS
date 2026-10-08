package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

/**
 * OB-P12-REQ-025, OB-P12-REQ-035: per-device serialization, multi-device
 * independence, and multi-device isolation.
 */
class CodecControlConcurrencyTest {

    @Test
    fun `concurrent selects on one device serialize - both run, never interleave`() = runTest {
        val order = mutableListOf<String>()
        val adapter = object : CodecControlAdapter {
            override suspend fun apply(operation: CodecOperation): CodecApplyOutcome {
                order += "start:${operation.operationId}"
                kotlinx.coroutines.delay(50)
                order += "end:${operation.operationId}"
                return CodecApplyOutcome.Performed
            }
            override suspend fun observeAfterApply(
                device: com.omnibuds.core.device.DeviceIdentity,
                codec: Codec,
            ): CodecConfiguration? = CodecConfiguration(codec)
        }
        val device = testDevice()
        val engine = CodecControlEngine(
            FakeResolver(mapOf(Codec.LDAC to fullControlCapability(Codec.LDAC))),
            adapter,
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
        val results = awaitAll(
            async {
                engine.execute(
                    CodecOperation.select(
                        device, Codec.LDAC, "op-1",
                        verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
                    ),
                )
            },
            async {
                engine.execute(
                    CodecOperation.select(
                        device, Codec.LDAC, "op-2",
                        verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
                    ),
                )
            },
        )
        // Both completed (serialized, not raced).
        assertEquals(2, results.size)
        // The applies never interleaved: each start is followed by its end.
        val starts = order.filter { it.startsWith("start:") }
        val ends = order.filter { it.startsWith("end:") }
        assertEquals(2, starts.size)
        assertEquals(2, ends.size)
        for (i in starts.indices) {
            val id = starts[i].removePrefix("start:")
            val startIdx = order.indexOf("start:$id")
            val endIdx = order.indexOf("end:$id")
            assertTrue(endIdx == startIdx + 1, "operation $id was interleaved: $order")
        }
    }

    @Test
    fun `device A LDAC operation leaves device B AAC state unchanged`() = runTest {
        val deviceA = testDevice("Device A")
        val deviceB = testDevice("Device B")
        // Each device observes its own requested codec; state must not leak.
        val sharedAdapter = object : CodecControlAdapter {
            override suspend fun apply(operation: CodecOperation): CodecApplyOutcome =
                CodecApplyOutcome.Performed
            override suspend fun observeAfterApply(
                device: com.omnibuds.core.device.DeviceIdentity,
                codec: Codec,
            ): CodecConfiguration? = CodecConfiguration(
                codec = if (device == deviceA) Codec.LDAC else Codec.AAC,
            )
        }
        val shared = CodecControlEngine(
            FakeResolver(
                mapOf(
                    Codec.LDAC to fullControlCapability(Codec.LDAC),
                    Codec.AAC to fullControlCapability(Codec.AAC),
                ),
            ),
            sharedAdapter,
            FakeLiveness(),
            clockMillis = { 1_000L },
        )
        shared.execute(
            CodecOperation.select(
                deviceA, Codec.LDAC, "op-a",
                verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
            ),
        )
        shared.execute(
            CodecOperation.select(
                deviceB, Codec.AAC, "op-b",
                verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
            ),
        )
        // Reverse: operate on B, verify A unchanged.
        shared.execute(
            CodecOperation.select(
                deviceB, Codec.AAC, "op-b2",
                verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
            ),
        )
        val stateA = shared.states.value[deviceA]!!
        val stateB = shared.states.value[deviceB]!!
        assertEquals(Codec.LDAC, stateA.confirmedCodec)
        assertEquals(Codec.AAC, stateB.confirmedCodec)
    }
}

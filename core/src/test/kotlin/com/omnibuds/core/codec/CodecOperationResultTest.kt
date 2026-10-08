package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OB-P12-REQ-007: the structured result hierarchy.
 */
class CodecOperationResultTest {

    @Test
    fun `only Verified confirms`() {
        val verified = CodecOperationResult.Verified("op-1", Codec.LDAC, null)
        assertTrue(verified.isConfirmed)

        val others: List<CodecOperationResult> = listOf(
            CodecOperationResult.Accepted("op-1"),
            CodecOperationResult.Applied("op-1", Codec.LDAC, null),
            CodecOperationResult.AppliedUnverified("op-1", Codec.LDAC, null),
            CodecOperationResult.Rejected("op-1", "reason"),
            CodecOperationResult.Unsupported("op-1", Codec.LDAC),
            CodecOperationResult.NotSelectable("op-1", Codec.LDAC),
            CodecOperationResult.NotConfigurable("op-1", Codec.LDAC),
            CodecOperationResult.NotObservable("op-1", Codec.LDAC),
            CodecOperationResult.DeviceDisconnected("op-1"),
            CodecOperationResult.PlatformUnavailable("op-1", "reason"),
            CodecOperationResult.VerificationFailed("op-1", Codec.LDAC, Codec.AAC),
            CodecOperationResult.TimedOut("op-1"),
            CodecOperationResult.Failed(
                "op-1",
                OmniBudsError.of(OmniBudsErrorCategory.CODEC_OPERATION_FAILED, "op-1", "x"),
            ),
        )
        others.forEach {
            assertFalse(it.isConfirmed, "$it must not confirm")
        }
    }

    @Test
    fun `terminal refusals are classified`() {
        val refusals: List<CodecOperationResult> = listOf(
            CodecOperationResult.Unsupported("op-1", Codec.LDAC),
            CodecOperationResult.NotSelectable("op-1", Codec.LDAC),
            CodecOperationResult.NotConfigurable("op-1", Codec.LDAC),
            CodecOperationResult.NotObservable("op-1", Codec.LDAC),
            CodecOperationResult.PlatformUnavailable("op-1", "reason"),
        )
        refusals.forEach { assertTrue(it.isTerminalRefusal, "$it should be a terminal refusal") }

        val nonRefusals: List<CodecOperationResult> = listOf(
            CodecOperationResult.Verified("op-1", Codec.LDAC, null),
            CodecOperationResult.TimedOut("op-1"),
            CodecOperationResult.DeviceDisconnected("op-1"),
        )
        nonRefusals.forEach { assertFalse(it.isTerminalRefusal, "$it should not be a refusal") }
    }

    @Test
    fun `every result carries its operationId`() {
        val results: List<CodecOperationResult> = listOf(
            CodecOperationResult.Accepted("op-42"),
            CodecOperationResult.Verified("op-42", Codec.LDAC, null),
            CodecOperationResult.TimedOut("op-42"),
            CodecOperationResult.DeviceDisconnected("op-42"),
        )
        results.forEach {
            assertTrue(it.operationId == "op-42", "result $it lost its operationId")
        }
    }
}

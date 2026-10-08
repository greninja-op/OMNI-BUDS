package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.QualityMode
import com.omnibuds.core.common.SideEffectClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * OB-P12-REQ-006: CodecOperation is a validated value.
 */
class CodecOperationTest {

    private val device = testDevice()

    @Test
    fun `select requires no configuration`() {
        assertFailsWith<IllegalArgumentException> {
            CodecOperation(
                operationId = "op-1",
                device = device,
                codec = Codec.LDAC,
                type = CodecOperationType.SELECT_CODEC,
                requestedConfiguration = CodecConfiguration.empty(Codec.LDAC),
                expectedTransport = com.omnibuds.core.audio.AudioTransportKind.UNKNOWN,
                timeoutMillis = null,
                verificationStrategy = CodecVerificationStrategy.NONE,
                sideEffect = SideEffectClass.SIDE_EFFECTING,
            )
        }
    }

    @Test
    fun `configure requires a configuration`() {
        assertFailsWith<IllegalArgumentException> {
            CodecOperation(
                operationId = "op-1",
                device = device,
                codec = Codec.LDAC,
                type = CodecOperationType.CONFIGURE_CODEC,
                requestedConfiguration = null,
                expectedTransport = com.omnibuds.core.audio.AudioTransportKind.UNKNOWN,
                timeoutMillis = null,
                verificationStrategy = CodecVerificationStrategy.NONE,
                sideEffect = SideEffectClass.SIDE_EFFECTING,
            )
        }
    }

    @Test
    fun `UNKNOWN codec is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CodecOperation.select(device, Codec.UNKNOWN, "op-1")
        }
    }

    @Test
    fun `blank operationId is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CodecOperation.select(device, Codec.AAC, "  ")
        }
    }

    @Test
    fun `non-positive timeout is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CodecOperation.select(device, Codec.AAC, "op-1", timeoutMillis = 0L)
        }
    }

    @Test
    fun `refresh is read-only by construction`() {
        val op = CodecOperation.refresh(device, Codec.AAC, "op-1")
        assertEquals(SideEffectClass.READ_ONLY_SAFE, op.sideEffect)
        assertEquals(CodecOperationType.REFRESH_STATE, op.type)
    }

    @Test
    fun `select is side-effecting by construction`() {
        val op = CodecOperation.select(device, Codec.LDAC, "op-1")
        assertEquals(SideEffectClass.SIDE_EFFECTING, op.sideEffect)
    }

    @Test
    fun `configure carries its configuration`() {
        val config = CodecConfiguration(Codec.LDAC, qualityMode = QualityMode.BALANCED)
        val op = CodecOperation.configure(device, config, "op-1")
        assertEquals(config, op.requestedConfiguration)
        assertEquals(Codec.LDAC, op.codec)
    }

    @Test
    fun `wrong side effect class is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CodecOperation(
                operationId = "op-1",
                device = device,
                codec = Codec.AAC,
                type = CodecOperationType.REFRESH_STATE,
                requestedConfiguration = null,
                expectedTransport = com.omnibuds.core.audio.AudioTransportKind.UNKNOWN,
                timeoutMillis = null,
                verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
                sideEffect = SideEffectClass.SIDE_EFFECTING,
            )
        }
    }
}

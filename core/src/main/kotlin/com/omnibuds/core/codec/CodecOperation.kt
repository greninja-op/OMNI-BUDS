package com.omnibuds.core.codec

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.common.SideEffectClass
import com.omnibuds.core.device.DeviceIdentity

/**
 * One codec operation the control engine is asked to perform.
 *
 * Phase 12 (OB-P12-REQ-006): the operation is a value, not a callback or a
 * future. It describes the attempt; the outcome travels back as a
 * [CodecOperationResult]. [operationId] correlates the attempt with its result
 * and must be supplied by the caller — never minted from wall-clock or
 * randomness inside `:core`, so a test drives it (the same rule as
 * `FeatureOperation.operationId`).
 *
 * [REFRESH_STATE][CodecOperationType.REFRESH_STATE] is read-only
 * ([SideEffectClass.READ_ONLY_SAFE]); every other type is
 * [SideEffectClass.SIDE_EFFECTING] and is never retried blindly: a timed-out
 * write is followed by a re-observation, never by a second write.
 */
data class CodecOperation(
    val operationId: String,
    val device: DeviceIdentity,
    val codec: Codec,
    val type: CodecOperationType,
    /** Present only for [CodecOperationType.CONFIGURE_CODEC]. */
    val requestedConfiguration: CodecConfiguration?,
    val expectedTransport: AudioTransportKind,
    /** This attempt's wait bound in milliseconds, or null to inherit the default. */
    val timeoutMillis: Long?,
    val verificationStrategy: CodecVerificationStrategy,
    val sideEffect: SideEffectClass,
) {
    init {
        require(operationId.isNotBlank()) {
            "operationId correlates the attempt with its outcome; a blank one cannot be attributed"
        }
        require(codec != Codec.UNKNOWN) {
            "a codec operation against UNKNOWN has no target; identify the codec first"
        }
        require(timeoutMillis == null || timeoutMillis > 0) {
            "timeoutMillis must be unreported (null) or positive, was $timeoutMillis"
        }
        when (type) {
            CodecOperationType.CONFIGURE_CODEC -> require(requestedConfiguration != null) {
                "a CONFIGURE_CODEC operation without a requested configuration has nothing to apply"
            }
            CodecOperationType.SELECT_CODEC,
            CodecOperationType.ENABLE_CODEC,
            CodecOperationType.DISABLE_CODEC,
            CodecOperationType.RESET_CONFIGURATION,
            CodecOperationType.REFRESH_STATE,
            -> require(requestedConfiguration == null) {
                "a $type operation carries no requested configuration; got $requestedConfiguration"
            }
        }
        val expected = if (type == CodecOperationType.REFRESH_STATE) {
            SideEffectClass.READ_ONLY_SAFE
        } else {
            SideEffectClass.SIDE_EFFECTING
        }
        require(sideEffect == expected) {
            "a $type operation is $expected by construction; $sideEffect contradicts its nature"
        }
    }

    companion object {
        /** The wait bound an operation inherits when it declares none. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 10_000L

        /** Request the device use [codec] instead of the current one. */
        fun select(
            device: DeviceIdentity,
            codec: Codec,
            operationId: String,
            expectedTransport: AudioTransportKind = AudioTransportKind.UNKNOWN,
            timeoutMillis: Long? = null,
            verificationStrategy: CodecVerificationStrategy = CodecVerificationStrategy.NONE,
        ): CodecOperation = CodecOperation(
            operationId = operationId,
            device = device,
            codec = codec,
            type = CodecOperationType.SELECT_CODEC,
            requestedConfiguration = null,
            expectedTransport = expectedTransport,
            timeoutMillis = timeoutMillis,
            verificationStrategy = verificationStrategy,
            sideEffect = SideEffectClass.SIDE_EFFECTING,
        )

        /** Request [configuration] be applied for [CodecConfiguration.codec]. */
        fun configure(
            device: DeviceIdentity,
            configuration: CodecConfiguration,
            operationId: String,
            expectedTransport: AudioTransportKind = AudioTransportKind.UNKNOWN,
            timeoutMillis: Long? = null,
            verificationStrategy: CodecVerificationStrategy = CodecVerificationStrategy.NONE,
        ): CodecOperation = CodecOperation(
            operationId = operationId,
            device = device,
            codec = configuration.codec,
            type = CodecOperationType.CONFIGURE_CODEC,
            requestedConfiguration = configuration,
            expectedTransport = expectedTransport,
            timeoutMillis = timeoutMillis,
            verificationStrategy = verificationStrategy,
            sideEffect = SideEffectClass.SIDE_EFFECTING,
        )

        /** Re-observe codec state. Read-only; always permitted. */
        fun refresh(
            device: DeviceIdentity,
            codec: Codec,
            operationId: String,
        ): CodecOperation = CodecOperation(
            operationId = operationId,
            device = device,
            codec = codec,
            type = CodecOperationType.REFRESH_STATE,
            requestedConfiguration = null,
            expectedTransport = AudioTransportKind.UNKNOWN,
            timeoutMillis = null,
            verificationStrategy = CodecVerificationStrategy.PLATFORM_OBSERVATION,
            sideEffect = SideEffectClass.READ_ONLY_SAFE,
        )
    }
}

package com.omnibuds.core.feature

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.SideEffectClass
import com.omnibuds.core.config.ConfigurationValue

/**
 * The kinds of feature operation the engine can express.
 *
 * Phase 9 implements [READ] and [WRITE]. [SUBSCRIBE], [UNSUBSCRIBE] and [RESET]
 * are declared vocabulary so the model does not have to be reshaped when later
 * phases wire protocol events and device resets; attempting one in Phase 9 fails
 * explicitly with [FeatureErrorCode.OPERATION_NOT_IMPLEMENTED] rather than
 * pretending to work. Firmware updates are not an operation type and never will
 * be (Phase 9 prompt section 20).
 */
enum class FeatureOperationType {
    READ,
    WRITE,
    SUBSCRIBE,
    UNSUBSCRIBE,
    RESET,
}

/**
 * One feature operation the engine was asked to perform.
 *
 * The operation is a value, not a callback or a future: it describes the attempt,
 * and the attempt's outcome travels back as an
 * [com.omnibuds.core.common.OperationOutcome]. [operationId] correlates the attempt
 * with its error records and must be supplied by the caller — never minted from
 * wall-clock or randomness inside `:core`, so a test drives it (the same rule as
 * [com.omnibuds.core.protocol.ProtocolCommand.correlationId]).
 */
data class FeatureOperation(
    val operationId: String,
    val feature: FeatureId,
    val type: FeatureOperationType,
    /** The value to write; present only for [FeatureOperationType.WRITE]. */
    val requestedValue: ConfigurationValue?,
    /** This attempt's wait bound in milliseconds, or null to inherit the default. */
    val timeoutMillis: Long?,
    val sideEffect: SideEffectClass,
) {

    init {
        require(operationId.isNotBlank()) {
            "operationId correlates the attempt with its outcome; a blank one cannot be attributed"
        }
        require(timeoutMillis == null || timeoutMillis > 0) {
            "timeoutMillis must be unreported (null) or positive, was $timeoutMillis"
        }
        when (type) {
            FeatureOperationType.WRITE -> require(requestedValue != null) {
                "a WRITE operation without a requested value has nothing to ask the device for"
            }

            FeatureOperationType.READ,
            FeatureOperationType.SUBSCRIBE,
            FeatureOperationType.UNSUBSCRIBE,
            FeatureOperationType.RESET,
            -> require(requestedValue == null) {
                "a $type operation carries no requested value; got $requestedValue"
            }
        }
        val expected = if (type == FeatureOperationType.READ) {
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

        /** A read of [feature]'s current value. */
        fun read(
            feature: FeatureId,
            operationId: String,
            timeoutMillis: Long? = null,
        ): FeatureOperation = FeatureOperation(
            operationId = operationId,
            feature = feature,
            type = FeatureOperationType.READ,
            requestedValue = null,
            timeoutMillis = timeoutMillis,
            sideEffect = SideEffectClass.READ_ONLY_SAFE,
        )

        /** A request to set [feature] to [value]. */
        fun write(
            feature: FeatureId,
            value: ConfigurationValue,
            operationId: String,
            timeoutMillis: Long? = null,
        ): FeatureOperation = FeatureOperation(
            operationId = operationId,
            feature = feature,
            type = FeatureOperationType.WRITE,
            requestedValue = value,
            timeoutMillis = timeoutMillis,
            sideEffect = SideEffectClass.SIDE_EFFECTING,
        )
    }
}

package com.omnibuds.core.feature

import com.omnibuds.core.capability.CapabilitySnapshot
import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.CapabilityState

/**
 * The verdict of the pre-operation validation pipeline.
 *
 * [Valid] means every prerequisite held and the operation may be constructed;
 * [Invalid] names the first prerequisite that failed, in pipeline order, with a
 * diagnostic detail. Only the first failure is reported: prerequisites are
 * ordered so the most fundamental problem surfaces (an unknown feature is
 * reported before its value is ever examined).
 */
sealed interface FeatureValidation {

    /** Every prerequisite held; the operation may be attempted. */
    data object Valid : FeatureValidation

    /** A prerequisite failed; the hardware command must not be sent. */
    data class Invalid(val code: FeatureErrorCode, val detail: String) : FeatureValidation
}

/**
 * The ten-step gate every feature operation passes before the engine touches
 * the protocol port.
 *
 * 1. The feature has a definition — otherwise nothing can interpret it.
 * 2. Discovery did not positively rule it out, and established *something*.
 * 3. The derived access permits this operation type.
 * 4. The requested value matches the definition's shape and constraints.
 * 5. Dependencies are satisfied for this operation type.
 * 6. No declared conflict blocks this operation type.
 * 7. The bound protocol implements the operation.
 * 8. A transport is established for the feature.
 * 9. The operation value itself is constructed (owned by [FeatureOperation]).
 * 10. Execution, owned by [FeatureEngine], never by this validator.
 *
 * Any failure returns [FeatureValidation.Invalid] and the hardware command is
 * never sent. The validator reads state; it changes nothing and performs no
 * I/O, which is what makes it unit-testable against pure fixtures.
 */
class FeatureValidator {

    fun validate(
        operation: FeatureOperation,
        definition: FeatureDefinition?,
        snapshot: CapabilitySnapshot,
        port: FeatureProtocolPort,
        currentStates: Map<FeatureId, FeatureState>,
        allRelations: List<FeatureRelation> = definition?.relations.orEmpty(),
    ): FeatureValidation {
        // 1. The feature must be a known, drivable contract.
        if (definition == null) {
            return invalid(
                FeatureErrorCode.FEATURE_UNKNOWN,
                "${operation.feature.qualifiedName} has no feature definition; " +
                    "an undefined feature cannot be operated on",
                operation,
            )
        }

        // 2. Capability gating: unknown is not unsupported, and neither may be operated on.
        val record = snapshot.capabilities[operation.feature]
        val state = record?.state ?: CapabilityState.UNKNOWN
        if (state == CapabilityState.UNSUPPORTED) {
            return invalid(
                FeatureErrorCode.FEATURE_UNSUPPORTED,
                "${operation.feature.qualifiedName} was positively established as unsupported " +
                    "on this device; the engine reports the absence honestly",
                operation,
            )
        }
        if (state == CapabilityState.UNKNOWN) {
            return invalid(
                FeatureErrorCode.FEATURE_UNKNOWN,
                "${operation.feature.qualifiedName} has no established capability state; " +
                    "unknown is not a licence to operate",
                operation,
            )
        }

        // 3. Access: the derived access must permit this operation type.
        val access = accessOf(
            record ?: FeatureCapability.unknown(operation.feature),
            snapshot.availabilityOf(operation.feature),
        )
        if (!access.permits(operation.type)) {
            val code = when {
                access == FeatureAccess.UNAVAILABLE -> FeatureErrorCode.FEATURE_UNAVAILABLE
                operation.type == FeatureOperationType.WRITE -> FeatureErrorCode.FEATURE_READ_ONLY
                else -> FeatureErrorCode.FEATURE_WRITE_UNSUPPORTED
            }
            return invalid(
                code,
                "${operation.feature.qualifiedName} has access $access, which does not permit " +
                    "a ${operation.type} operation",
                operation,
            )
        }

        // 4. Value validation, for the operations that carry a value.
        val requested = operation.requestedValue
        if (requested != null) {
            val detail = definition.validationDetail(requested)
            if (detail != null) {
                return invalid(
                    FeatureErrorCode.INVALID_VALUE,
                    "${operation.feature.qualifiedName}: refusing invalid value: $detail",
                    operation,
                )
            }
        }

        // 5 & 6. Dependencies and conflicts, per the definition's declared relations.
        // Cycle detection sees every known relation, not just this definition's.
        val report = FeatureDependencyEvaluator.evaluate(
            feature = operation.feature,
            ownRelations = definition.relations,
            allRelations = allRelations,
            capabilities = snapshot.capabilities,
            currentStates = currentStates,
        )
        val blocking = report.issues.filter { issue ->
            when (operation.type) {
                FeatureOperationType.WRITE -> issue.blocksWrite
                else -> issue.blocksRead
            }
        }
        val firstBlocking = blocking.firstOrNull()
        if (firstBlocking != null) {
            return invalid(firstBlocking.code, firstBlocking.detail, operation)
        }

        // 7. The bound protocol must implement the operation. Declared-but-unimplemented
        // operation types fail explicitly rather than pretending.
        when (operation.type) {
            FeatureOperationType.READ -> if (!port.supportsRead) {
                return invalid(
                    FeatureErrorCode.PROTOCOL_UNAVAILABLE,
                    "the bound protocol does not implement feature reads",
                    operation,
                )
            }

            FeatureOperationType.WRITE -> if (!port.supportsWrite) {
                return invalid(
                    FeatureErrorCode.PROTOCOL_UNAVAILABLE,
                    "the bound protocol does not implement feature writes",
                    operation,
                )
            }

            FeatureOperationType.SUBSCRIBE,
            FeatureOperationType.UNSUBSCRIBE,
            FeatureOperationType.RESET,
            -> return invalid(
                FeatureErrorCode.OPERATION_NOT_IMPLEMENTED,
                "${operation.type} is declared vocabulary but is not implemented in Phase 9; " +
                    "subscriptions arrive when protocol events are wired, resets with a later phase",
                operation,
            )
        }

        // 8. A transport must be established for the feature; UNKNOWN transport means
        // "not determined here", never "there is no transport", so the operation waits.
        val transport = record?.transport ?: TransportKind.UNKNOWN
        if (transport == TransportKind.UNKNOWN) {
            return invalid(
                FeatureErrorCode.TRANSPORT_UNAVAILABLE,
                "${operation.feature.qualifiedName} has no established transport; " +
                    "an operation with nowhere to travel is not sent",
                operation,
            )
        }

        // 9 & 10. Construction is owned by FeatureOperation's factory; execution by the engine.
        return FeatureValidation.Valid
    }

    private fun invalid(
        code: FeatureErrorCode,
        detail: String,
        operation: FeatureOperation,
    ): FeatureValidation.Invalid {
        // The detail is diagnostic text; operation ids are safe to log (SEC-LOG-002:
        // no Bluetooth addresses, device names or manufacturer data — none appear here).
        return FeatureValidation.Invalid(code, "[${operation.operationId}] $detail")
    }
}

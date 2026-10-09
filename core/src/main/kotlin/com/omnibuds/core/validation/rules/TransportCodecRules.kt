package com.omnibuds.core.validation.rules

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.validation.ValidationCategory
import com.omnibuds.core.validation.ValidationInput
import com.omnibuds.core.validation.ValidationResult
import com.omnibuds.core.validation.ValidationRule
import com.omnibuds.core.validation.ValidationSeverity
import com.omnibuds.core.validation.ValidationStatus

/**
 * Phase 14 (OB-P14-REQ-005, OB-P14-REQ-006): transport and codec rules.
 */

/**
 * P14-TRANSPORT-001: the observed transport must be internally coherent —
 * a transport claim requires the transport to be known, and media vs
 * communication paths must not be conflated.
 */
object TransportCoherenceRule : ValidationRule {
    override val ruleId = "P14-TRANSPORT-001"
    override val description =
        "Transport, profile, and media/communication path are consistent and distinct."
    override val category = ValidationCategory.TRANSPORT

    override fun evaluate(input: ValidationInput): ValidationResult {
        val transport = input.transport
        if (transport == null || transport == AudioTransportKind.UNKNOWN) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.NOT_OBSERVABLE,
                severity = ValidationSeverity.INFO,
                reason = "Transport is unknown; the platform did not expose it.",
                limitations = listOf("Transport observation unavailable"),
            )
        }
        // Media vs communication: a communication route on a media transport
        // (or vice versa) is a contradiction.
        val isCommRoute = input.isCommunicationRoute
        val isCommTransport = transport == AudioTransportKind.HFP
        if (isCommRoute != null && isCommRoute != isCommTransport) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INVALID,
                severity = ValidationSeverity.ERROR,
                evidenceReferences = listOf("AudioTransportKind", "route.isCommunication"),
                observedAtMillis = input.timestampMillis,
                reason = "Communication route flag ($isCommRoute) contradicts transport $transport; " +
                    "media and communication paths must stay distinct.",
            )
        }
        return ValidationResult(
            validationId = ruleId,
            device = input.device,
            sessionGeneration = input.sessionGeneration,
            category = category,
            status = ValidationStatus.VALID,
            severity = ValidationSeverity.INFO,
            observedAtMillis = input.timestampMillis,
            reason = "Transport $transport is coherent with the route observation.",
        )
    }
}

/**
 * P14-CODEC-001: codec/transport association must respect domain invariants.
 * LC3 belongs to LE Audio; A2DP codecs belong to Classic A2DP.
 */
object CodecTransportAssociationRule : ValidationRule {
    override val ruleId = "P14-CODEC-001"
    override val description =
        "Codec identity is consistent with the observed transport."
    override val category = ValidationCategory.CODEC

    private val leAudioCodecs = setOf(Codec.LC3)
    private val a2dpCodecs = setOf(
        Codec.SBC, Codec.AAC, Codec.APTX, Codec.APTX_HD,
        Codec.APTX_ADAPTIVE, Codec.APTX_LOSSLESS, Codec.LDAC,
    )

    override fun evaluate(input: ValidationInput): ValidationResult {
        val codec = input.codec
        val transport = input.transport
        if (codec == null || codec == Codec.UNKNOWN) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INCONCLUSIVE,
                severity = ValidationSeverity.INFO,
                reason = "Codec unknown; association cannot be checked.",
            )
        }
        if (transport == null || transport == AudioTransportKind.UNKNOWN) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INCONCLUSIVE,
                severity = ValidationSeverity.INFO,
                reason = "Transport unknown; codec association cannot be checked.",
            )
        }
        val violation = when {
            codec in leAudioCodecs && transport != AudioTransportKind.LE_AUDIO ->
                "LC3 requires LE Audio; observed transport is $transport."
            codec in a2dpCodecs && transport == AudioTransportKind.LE_AUDIO ->
                "$codec is a Classic A2DP codec; it cannot run over LE Audio."
            codec in a2dpCodecs && transport == AudioTransportKind.HFP ->
                "$codec is a media codec; HFP is the communication path."
            else -> null
        }
        return if (violation != null) {
            ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INVALID,
                severity = ValidationSeverity.ERROR,
                evidenceReferences = listOf("Codec", "AudioTransportKind"),
                observedAtMillis = input.timestampMillis,
                reason = violation,
                suggestedAction = "Re-examine the transport/codec correlation; one source is wrong.",
            )
        } else {
            ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                observedAtMillis = input.timestampMillis,
                reason = "$codec is consistent with transport $transport.",
            )
        }
    }
}

/**
 * P14-CODEC-002: codec identity alone never establishes active audio.
 * A codec at SUPPORTED/AVAILABLE/ENABLED/NEGOTIATED without an active route
 * is not "currently active".
 */
object CodecActiveClaimRule : ValidationRule {
    override val ruleId = "P14-CODEC-002"
    override val description =
        "A codec is reported active only with an active route and current evidence."
    override val category = ValidationCategory.CODEC

    override fun evaluate(input: ValidationInput): ValidationResult {
        val codec = input.codec
        if (codec == null || codec == Codec.UNKNOWN) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INCONCLUSIVE,
                severity = ValidationSeverity.INFO,
                reason = "No codec claimed; nothing to validate.",
            )
        }
        // The rule validates the *claim structure*, not the signal: a codec
        // presented as active while the route is inactive is a model error.
        val routeActive = input.routeActive
        val negotiation = input.negotiationState
        if (routeActive == false &&
            negotiation == com.omnibuds.core.quality.NegotiationState.ACTIVE
        ) {
            return ValidationResult(
                validationId = ruleId,
                device = input.device,
                sessionGeneration = input.sessionGeneration,
                category = category,
                status = ValidationStatus.INVALID,
                severity = ValidationSeverity.ERROR,
                observedAtMillis = input.timestampMillis,
                reason = "Negotiation state is ACTIVE but the route is inactive; " +
                    "negotiated does not imply active.",
            )
        }
        return ValidationResult(
            validationId = ruleId,
            device = input.device,
            sessionGeneration = input.sessionGeneration,
            category = category,
            status = ValidationStatus.VALID,
            severity = ValidationSeverity.INFO,
            observedAtMillis = input.timestampMillis,
            reason = "Codec activity claim is structurally consistent.",
        )
    }
}

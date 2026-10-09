package com.omnibuds.core.access

import com.omnibuds.core.device.DeviceFingerprint

/**
 * Deterministic device classifier.
 *
 * Phase 21 (OB-P21-REQ-002/003): pure function of fingerprint + registry
 * state. Ambiguity is preserved, never resolved by picking a candidate.
 */
object DeviceClassifier {

    /**
     * Classify a device.
     *
     * @param fingerprint the observed device fingerprint.
     * @param matchedAdapters adapter IDs with a definite match (0..n).
     * @param ambiguous true when any adapter reports ambiguity.
     * @param protocolVerified true when the matched protocol implementation
     * has passed validation.
     */
    fun classify(
        fingerprint: DeviceFingerprint,
        matchedAdapters: List<String>,
        ambiguous: Boolean,
        protocolVerified: Boolean,
    ): DeviceAccessState {
        // Ambiguity dominates: never pick a candidate.
        if (ambiguous) {
            return DeviceAccessState(
                classification = DeviceClassification.AMBIGUOUS_IDENTITY,
                identityConfidence = IdentityConfidence.LOW,
                protocolVerified = false,
                writeAuthorized = false,
            )
        }

        // Nothing observed at all.
        if (fingerprint.isEntirelyUnobserved) {
            return DeviceAccessState.unknown()
        }

        // Conflicting matches: treat as ambiguous (safest).
        if (matchedAdapters.size > 1) {
            return DeviceAccessState(
                classification = DeviceClassification.AMBIGUOUS_IDENTITY,
                identityConfidence = IdentityConfidence.LOW,
                protocolVerified = false,
                writeAuthorized = false,
            )
        }

        // No match: partially identified if we saw something, else unknown.
        if (matchedAdapters.isEmpty()) {
            val confidence = if (hasIdentitySignals(fingerprint)) {
                IdentityConfidence.MEDIUM
            } else {
                IdentityConfidence.LOW
            }
            return DeviceAccessState(
                classification = if (hasIdentitySignals(fingerprint)) {
                    DeviceClassification.PARTIALLY_IDENTIFIED
                } else {
                    DeviceClassification.UNKNOWN_DEVICE
                },
                identityConfidence = confidence,
                protocolVerified = false,
                writeAuthorized = false,
            )
        }

        // Exactly one match.
        return if (protocolVerified) {
            DeviceAccessState(
                classification = DeviceClassification.KNOWN_DEVICE_SUPPORTED,
                identityConfidence = IdentityConfidence.HIGH,
                protocolVerified = true,
                // Write authorization is per-capability; the device-level
                // flag is a necessary but not sufficient condition.
                // Default false here; the access policy decides per operation.
                writeAuthorized = false,
            )
        } else {
            DeviceAccessState(
                classification = DeviceClassification.KNOWN_PROTOCOL_UNVERIFIED,
                identityConfidence = IdentityConfidence.HIGH,
                protocolVerified = false,
                writeAuthorized = false,
            )
        }
    }

    private fun hasIdentitySignals(fingerprint: DeviceFingerprint): Boolean =
        fingerprint.manufacturerData.isNotEmpty() ||
            fingerprint.serviceUuids.isNotEmpty() ||
            fingerprint.deviceClass != null
}

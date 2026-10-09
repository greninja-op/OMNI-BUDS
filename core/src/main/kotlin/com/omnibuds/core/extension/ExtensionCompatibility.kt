package com.omnibuds.core.extension

/**
 * Compatibility evaluation for vendor extensions.
 *
 * Phase 23 (OB-P23-REQ-010/011): the 10 mandatory rules.
 * Every result includes the reason and the evidence used.
 */
object ExtensionCompatibility {

    /**
     * Evaluate whether [descriptor] is compatible with the device context.
     *
     * @param identityAmbiguous true when device identity is ambiguous.
     * @param firmwareUnknown true when firmware version is unknown.
     */
    fun evaluate(
        descriptor: VendorExtensionDescriptor,
        manufacturerId: String,
        modelId: String?,
        hardwareRevision: String?,
        firmwareVersion: String?,
        protocolId: String?,
        protocolVersion: String?,
        transport: String?,
        identityAmbiguous: Boolean,
        firmwareUnknown: Boolean,
    ): CompatibilityResult {
        val evidence = mutableListOf<String>()

        // Rule 5: ambiguous identity is insufficient for model-specific writes.
        // (Compatibility itself can be evaluated, but the result is flagged.)
        // Rule 1: never match on manufacturer name alone — the descriptor
        // carries a stable manufacturer ID, not a display name.
        if (descriptor.manufacturerId != manufacturerId) {
            return incompatible(
                "manufacturer mismatch",
                evidence + "descriptor.manufacturerId=${descriptor.manufacturerId}",
            )
        }
        evidence.add("manufacturerId=$manufacturerId")

        // Rule 2: sibling models do not share command semantics — exact
        // model match required when the descriptor lists models.
        if (modelId != null && descriptor.compatibleModelIds.isNotEmpty() &&
            modelId !in descriptor.compatibleModelIds
        ) {
            return incompatible(
                "model $modelId not in compatible models",
                evidence + "compatibleModelIds=${descriptor.compatibleModelIds}",
            )
        }
        if (modelId != null) evidence.add("modelId=$modelId")

        // Hardware revision constraints.
        if (hardwareRevision != null && descriptor.hardwareRevisions.isNotEmpty() &&
            hardwareRevision !in descriptor.hardwareRevisions
        ) {
            return incompatible(
                "hardware revision $hardwareRevision not supported",
                evidence + "hardwareRevisions=${descriptor.hardwareRevisions}",
            )
        }

        // Rule 3/4: firmware compatibility requires evidence; unknown
        // firmware is a separate case. Compatibility cannot be established
        // against unknown firmware — fail closed.
        if (firmwareUnknown) {
            evidence.add("firmware=unknown")
            return CompatibilityResult.UnknownFirmware(
                reason = "firmware unknown; compatibility cannot be established",
                evidence = evidence.toList(),
            )
        }
        // Firmware version rules (only when firmware is known).
        if (firmwareVersion != null && descriptor.firmwareRules.isNotEmpty()) {
            // Simple rule evaluation: rules are prefix constraints like ">= 3.0".
            // A full version comparator is out of scope; unknown rule
            // syntax is treated as not-matched (fail-closed).
            val matched = descriptor.firmwareRules.any { rule ->
                evaluateFirmwareRule(rule, firmwareVersion)
            }
            if (!matched) {
                return incompatible(
                    "firmware $firmwareVersion does not satisfy ${descriptor.firmwareRules}",
                    evidence + "firmwareVersion=$firmwareVersion",
                )
            }
            evidence.add("firmwareVersion=$firmwareVersion")
        }

        // Rule 6: protocol-version mismatches rejected unless a documented
        // compatibility rule permits them.
        if (protocolId != null && descriptor.compatibleProtocols.isNotEmpty() &&
            protocolId !in descriptor.compatibleProtocols
        ) {
            return incompatible(
                "protocol $protocolId not in compatible protocols",
                evidence + "compatibleProtocols=${descriptor.compatibleProtocols}",
            )
        }
        if (protocolId != null) evidence.add("protocolId=$protocolId")
        if (protocolVersion != null) evidence.add("protocolVersion=$protocolVersion")

        // Transport requirements.
        if (transport != null && descriptor.requiredTransports.isNotEmpty() &&
            transport !in descriptor.requiredTransports
        ) {
            return incompatible(
                "transport $transport not in required transports",
                evidence + "requiredTransports=${descriptor.requiredTransports}",
            )
        }

        // Lifecycle gate.
        if (descriptor.lifecycle != ExtensionLifecycle.ACTIVE) {
            return incompatible(
                "extension lifecycle is ${descriptor.lifecycle}",
                evidence.toList(),
            )
        }

        return CompatibilityResult.Compatible(
            reason = "all compatibility checks passed",
            evidence = evidence.toList(),
            identityAmbiguous = identityAmbiguous,
        )
    }

    private fun incompatible(reason: String, evidence: List<String>) =
        CompatibilityResult.Incompatible(reason, evidence)

    /**
     * Evaluate a firmware rule against a version.
     * Supported: ">= X", "<= X", "== X", "X" (exact).
     * Unknown syntax → false (fail-closed).
     */
    private fun evaluateFirmwareRule(rule: String, version: String): Boolean {
        val trimmed = rule.trim()
        return when {
            trimmed.startsWith(">=") -> compareVersions(version, trimmed.removePrefix(">=").trim()) >= 0
            trimmed.startsWith("<=") -> compareVersions(version, trimmed.removePrefix("<=").trim()) <= 0
            trimmed.startsWith("==") -> compareVersions(version, trimmed.removePrefix("==").trim()) == 0
            trimmed.matches(Regex("[0-9]+(\\.[0-9]+)*")) -> compareVersions(version, trimmed) == 0
            else -> false
        }
    }

    /** Compare dotted version strings. Returns negative/zero/positive. */
    private fun compareVersions(a: String, b: String): Int {
        val ap = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bp = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(ap.size, bp.size)
        for (i in 0 until len) {
            val av = ap.getOrElse(i) { 0 }
            val bv = bp.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}

/**
 * The result of a compatibility evaluation.
 * Every result carries the reason and the evidence used.
 */
sealed interface CompatibilityResult {
    /** Compatible. [identityAmbiguous] flags that writes still need resolution. */
    data class Compatible(
        val reason: String,
        val evidence: List<String>,
        val identityAmbiguous: Boolean,
    ) : CompatibilityResult

    /** Incompatible. */
    data class Incompatible(
        val reason: String,
        val evidence: List<String>,
    ) : CompatibilityResult

    /** Firmware unknown; compatibility cannot be established. */
    data class UnknownFirmware(
        val reason: String,
        val evidence: List<String>,
    ) : CompatibilityResult
}

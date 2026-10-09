package com.omnibuds.core.sdk.validation

import com.omnibuds.core.sdk.api.CommunityCapabilityDeclaration
import com.omnibuds.core.sdk.api.CommunityEvidenceRecord
import com.omnibuds.core.sdk.api.CommunityOperationDefinition
import com.omnibuds.core.sdk.api.OperationTier
import com.omnibuds.core.sdk.api.SdkVersion
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel

/**
 * Metadata package structure submitted by a community integration.
 *
 * Designed for pure static inspection without executing untrusted bytecode.
 */
data class CommunityMetadataPackage(
    val adapterId: String,
    val displayName: String,
    val sdkVersion: String,
    val integrationVersion: String,
    val evidenceRecord: CommunityEvidenceRecord,
    val capabilities: List<CommunityCapabilityDeclaration>,
    val operations: List<CommunityOperationDefinition>,
)

/**
 * Offline static package validator.
 *
 * Inspects community integration metadata without executing user code.
 * Deterministically rejects malformed, incomplete, contradictory, or unauthorized claims.
 */
object CommunityPackageValidator {

    private val ADAPTER_ID_REGEX = Regex("^[a-z0-9]+(\\.[a-z0-9_-]+)+$")

    fun validate(pkg: CommunityMetadataPackage): PackageValidationReport {
        val findings = mutableListOf<ValidationFinding>()

        // 1. Adapter Identifier Validation
        if (pkg.adapterId.isBlank()) {
            findings.add(
                ValidationFinding(
                    ValidationSeverity.ERROR,
                    "SDK-VAL-001",
                    "adapterId must not be blank",
                    "adapterId",
                ),
            )
        } else if (!ADAPTER_ID_REGEX.matches(pkg.adapterId)) {
            findings.add(
                ValidationFinding(
                    ValidationSeverity.ERROR,
                    "SDK-VAL-002",
                    "adapterId '${pkg.adapterId}' does not match required format 'namespace.name'",
                    "adapterId",
                ),
            )
        }

        // 2. Display Name Validation
        if (pkg.displayName.isBlank()) {
            findings.add(
                ValidationFinding(
                    ValidationSeverity.ERROR,
                    "SDK-VAL-003",
                    "displayName must not be blank",
                    "displayName",
                ),
            )
        }

        // 3. SDK Version Compatibility Check
        val parsedSdk = SdkVersion.parse(pkg.sdkVersion)
        if (parsedSdk == null) {
            findings.add(
                ValidationFinding(
                    ValidationSeverity.ERROR,
                    "SDK-VAL-004",
                    "sdkVersion '${pkg.sdkVersion}' is not a valid semantic version (major.minor.patch)",
                    "sdkVersion",
                ),
            )
        } else if (parsedSdk.major != SdkVersion.CURRENT.major) {
            findings.add(
                ValidationFinding(
                    ValidationSeverity.ERROR,
                    "SDK-VAL-005",
                    "Incompatible SDK major version: ${parsedSdk.major}. Host supports major ${SdkVersion.CURRENT.major}",
                    "sdkVersion",
                ),
            )
        }

        // 4. Evidence Record Provenance Inspection
        val evidence = pkg.evidenceRecord
        if (evidence.integrationId != pkg.adapterId) {
            findings.add(
                ValidationFinding(
                    ValidationSeverity.ERROR,
                    "SDK-VAL-006",
                    "evidenceRecord.integrationId '${evidence.integrationId}' does not match package adapterId '${pkg.adapterId}'",
                    "evidenceRecord.integrationId",
                ),
            )
        }
        if (evidence.declaredVerificationLevel > VerificationLevel.LAB_TESTED) {
            findings.add(
                ValidationFinding(
                    ValidationSeverity.ERROR,
                    "SDK-VAL-007",
                    "Self-declared verification level '${evidence.declaredVerificationLevel}' is prohibited. Maximum allowed is LAB_TESTED",
                    "evidenceRecord.declaredVerificationLevel",
                ),
            )
        }
        if (evidence.supportedModels.isEmpty()) {
            findings.add(
                ValidationFinding(
                    ValidationSeverity.WARNING,
                    "SDK-VAL-008",
                    "supportedModels is empty; adapter may not match any physical models",
                    "evidenceRecord.supportedModels",
                ),
            )
        }

        // 5. Capability Duplicate and Contradiction Checks
        val seenFeatures = mutableSetOf<String>()
        for (cap in pkg.capabilities) {
            val featKey = cap.featureId.qualifiedName
            if (!seenFeatures.add(featKey)) {
                findings.add(
                    ValidationFinding(
                        ValidationSeverity.ERROR,
                        "SDK-VAL-009",
                        "Duplicate capability declaration for feature: $featKey",
                        "capabilities.$featKey",
                    ),
                )
            }
            if (cap.claimedState == CapabilityState.PERSISTENCE_VERIFIED) {
                findings.add(
                    ValidationFinding(
                        ValidationSeverity.ERROR,
                        "SDK-VAL-010",
                        "Capability '$featKey' claims PERSISTENCE_VERIFIED which cannot be declared by a community package",
                        "capabilities.$featKey",
                    ),
                )
            }
            if (cap.dependencies.contains(cap.featureId)) {
                findings.add(
                    ValidationFinding(
                        ValidationSeverity.ERROR,
                        "SDK-VAL-011",
                        "Capability '$featKey' circularly declares dependency on itself",
                        "capabilities.$featKey.dependencies",
                    ),
                )
            }
            if (cap.conflicts.contains(cap.featureId)) {
                findings.add(
                    ValidationFinding(
                        ValidationSeverity.ERROR,
                        "SDK-VAL-012",
                        "Capability '$featKey' declares conflict with itself",
                        "capabilities.$featKey.conflicts",
                    ),
                )
            }
        }

        // 6. Operation Schema and Dependency Consistency
        val seenOperations = mutableSetOf<String>()
        val declaredFeatureIds = pkg.capabilities.map { it.featureId }.toSet()
        for (op in pkg.operations) {
            if (!seenOperations.add(op.operationId)) {
                findings.add(
                    ValidationFinding(
                        ValidationSeverity.ERROR,
                        "SDK-VAL-013",
                        "Duplicate operation declaration: ${op.operationId}",
                        "operations.${op.operationId}",
                    ),
                )
            }
            if (op.targetFeature !in declaredFeatureIds) {
                findings.add(
                    ValidationFinding(
                        ValidationSeverity.ERROR,
                        "SDK-VAL-014",
                        "Operation '${op.operationId}' targets undeclared feature '${op.targetFeature.qualifiedName}'",
                        "operations.${op.operationId}.targetFeature",
                    ),
                )
            }
        }

        val isValid = findings.none { it.severity == ValidationSeverity.ERROR }
        val summary = if (isValid) {
            "Package '${pkg.adapterId}' validated successfully with ${findings.size} findings."
        } else {
            "Package '${pkg.adapterId}' failed validation with ${findings.count { it.severity == ValidationSeverity.ERROR }} error(s)."
        }

        return PackageValidationReport(
            isValid = isValid,
            findings = findings,
            scannedIntegrationId = pkg.adapterId,
            summary = summary,
        )
    }
}

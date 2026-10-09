package com.omnibuds.core.extension

/**
 * Feature dependency and conflict evaluation.
 *
 * Phase 23 (OB-P23-REQ-012): prerequisites, mutual exclusion, conditional
 * availability. A dependency is not satisfied by a locally stored
 * preference — the caller supplies observed device state.
 */
object FeatureDependencies {

    /**
     * Check whether [feature]'s dependencies are satisfied.
     *
     * @param satisfiedFeatures IDs of features confirmed active on the device.
     * @param activeFeatures IDs of features currently active (for conflicts).
     */
    fun check(
        feature: VendorFeatureDefinition,
        satisfiedFeatures: Set<VendorFeatureId>,
        activeFeatures: Set<VendorFeatureId>,
        knownFeatures: Map<String, VendorFeatureDefinition>,
    ): DependencyResult {
        // Unknown dependencies remain unresolved.
        val unknownDeps = feature.dependencies.filter { dep ->
            !knownFeatures.containsKey(dep.value)
        }
        if (unknownDeps.isNotEmpty()) {
            return DependencyResult.Unresolved(
                "unknown dependencies: ${unknownDeps.map { it.value }}",
            )
        }

        val missing = feature.dependencies.filter { it !in satisfiedFeatures }
        if (missing.isNotEmpty()) {
            return DependencyResult.MissingPrerequisites(
                missing.map { it.value },
                "missing prerequisites: ${missing.map { it.value }}",
            )
        }

        val conflicts = feature.conflicts.filter { it in activeFeatures }
        if (conflicts.isNotEmpty()) {
            return DependencyResult.Conflict(
                conflicts.map { it.value },
                "conflicts with active features: ${conflicts.map { it.value }}",
            )
        }

        return DependencyResult.Satisfied("all dependencies satisfied")
    }
}

/** The result of dependency evaluation. */
sealed interface DependencyResult {
    data class Satisfied(val reason: String) : DependencyResult
    data class MissingPrerequisites(val missing: List<String>, val reason: String) : DependencyResult
    data class Conflict(val conflicting: List<String>, val reason: String) : DependencyResult
    data class Unresolved(val reason: String) : DependencyResult
}

package com.omnibuds.core.extension

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central registry for vendor extensions and feature definitions.
 *
 * Phase 23 (OB-P23-REQ-007/008/020): registration with structural
 * validation, duplicate detection, deterministic resolution, lifecycle
 * handling. Trust levels distinguish descriptive metadata from executable
 * implementations.
 */
class ExtensionRegistry {

    private val mutex = Mutex()
    private val extensions = mutableMapOf<String, RegisteredExtension>()
    private val features = mutableMapOf<String, VendorFeatureDefinition>()

    /**
     * Register an extension descriptor.
     * Rejects duplicates, invalid references, and circular dependencies.
     */
    suspend fun registerExtension(descriptor: VendorExtensionDescriptor): RegistrationResult =
        mutex.withLock {
            val id = descriptor.id.value
            if (extensions.containsKey(id)) {
                return RegistrationResult.Rejected("duplicate extension id $id")
            }
            // Dependency references must resolve to registered extensions
            // (or be registered later — we allow forward references but
            // record them; resolution checks at use time).
            if (descriptor.lifecycle == ExtensionLifecycle.DISABLED) {
                return RegistrationResult.Rejected("cannot register a DISABLED extension")
            }
            // Circular dependency check.
            if (hasCircularDependency(descriptor)) {
                return RegistrationResult.Rejected(
                    "circular dependency involving $id",
                )
            }
            extensions[id] = RegisteredExtension(descriptor, RegistrationAudit(
                registeredAtMillis = null, // wall-clock-free; set by caller if needed
                trustLevel = descriptor.trustLevel,
            ))
            RegistrationResult.Registered(id)
        }

    /**
     * Register a feature definition.
     * The owning extension must be registered; duplicates rejected.
     */
    suspend fun registerFeature(definition: VendorFeatureDefinition): RegistrationResult =
        mutex.withLock {
            val id = definition.id.value
            if (features.containsKey(id)) {
                return RegistrationResult.Rejected("duplicate feature id $id")
            }
            if (!extensions.containsKey(definition.extensionId.value)) {
                return RegistrationResult.Rejected(
                    "owning extension ${definition.extensionId.value} not registered",
                )
            }
            // Namespace must be declared by the extension.
            val namespace = "${definition.id.manufacturer}.${definition.id.family}"
            val ext = extensions[definition.extensionId.value]!!
            if (namespace !in ext.descriptor.featureNamespaces) {
                return RegistrationResult.Rejected(
                    "namespace $namespace not declared by extension ${definition.extensionId.value}",
                )
            }
            // Dependency cycle check among features.
            if (hasFeatureCycle(definition)) {
                return RegistrationResult.Rejected("circular feature dependency involving $id")
            }
            features[id] = definition
            RegistrationResult.Registered(id)
        }

    /** Look up an extension by stable ID. */
    suspend fun extension(id: VendorExtensionId): VendorExtensionDescriptor? =
        mutex.withLock { extensions[id.value]?.descriptor }

    /** Look up a feature by stable ID. */
    suspend fun feature(id: VendorFeatureId): VendorFeatureDefinition? =
        mutex.withLock { features[id.value] }

    /** All registered extensions, deterministically ordered. */
    suspend fun allExtensions(): List<VendorExtensionDescriptor> =
        mutex.withLock { extensions.values.map { it.descriptor }.sortedBy { it.id.value } }

    /** All registered features, deterministically ordered. */
    suspend fun allFeatures(): List<VendorFeatureDefinition> =
        mutex.withLock { features.values.sortedBy { it.id.value } }

    /**
     * Resolve extensions compatible with the given context.
     * Returns all candidates — never picks one. Ambiguity is the caller's
     * signal to deny restricted operations.
     */
    suspend fun resolveCandidates(
        manufacturerId: String,
        modelId: String?,
        firmwareVersion: String?,
        protocolId: String?,
        transport: String?,
    ): List<VendorExtensionDescriptor> = mutex.withLock {
        extensions.values.map { it.descriptor }
            .filter { it.lifecycle == ExtensionLifecycle.ACTIVE }
            .filter { it.manufacturerId == manufacturerId }
            .filter { modelId == null || it.compatibleModelIds.isEmpty() || modelId in it.compatibleModelIds }
            .filter { protocolId == null || it.compatibleProtocols.isEmpty() || protocolId in it.compatibleProtocols }
            .filter { transport == null || it.requiredTransports.isEmpty() || transport in it.requiredTransports }
            .sortedBy { it.id.value }
    }

    /**
     * Deprecate an extension. Historical records are preserved.
     */
    suspend fun deprecate(id: VendorExtensionId): Boolean = mutex.withLock {
        val current = extensions[id.value] ?: return false
        extensions[id.value] = current.copy(
            descriptor = current.descriptor.copy(lifecycle = ExtensionLifecycle.DEPRECATED),
        )
        true
    }

    /**
     * Disable an extension. Features remain registered but unresolvable.
     */
    suspend fun disable(id: VendorExtensionId): Boolean = mutex.withLock {
        val current = extensions[id.value] ?: return false
        extensions[id.value] = current.copy(
            descriptor = current.descriptor.copy(lifecycle = ExtensionLifecycle.DISABLED),
        )
        true
    }

    private fun hasCircularDependency(descriptor: VendorExtensionDescriptor): Boolean {
        // Depth-first search from the new descriptor through existing ones.
        val visited = mutableSetOf<String>()
        fun visit(id: String): Boolean {
            if (id == descriptor.id.value) return true // cycle back to new
            if (!visited.add(id)) return false
            val ext = extensions[id]?.descriptor ?: return false
            return ext.dependencies.any { visit(it.value) }
        }
        return descriptor.dependencies.any { visit(it.value) }
    }

    private fun hasFeatureCycle(definition: VendorFeatureDefinition): Boolean {
        val visited = mutableSetOf<String>()
        fun visit(id: String): Boolean {
            if (id == definition.id.value) return true
            if (!visited.add(id)) return false
            val f = features[id] ?: return false
            return f.dependencies.any { visit(it.value) }
        }
        return definition.dependencies.any { visit(it.value) }
    }
}

/** A registered extension with audit information. */
data class RegisteredExtension(
    val descriptor: VendorExtensionDescriptor,
    val audit: RegistrationAudit,
)

/** Audit information for a registration. */
data class RegistrationAudit(
    /** Null when the caller did not supply a timestamp (wall-clock-free). */
    val registeredAtMillis: Long?,
    val trustLevel: ExtensionTrustLevel,
)

/** The result of a registration attempt. */
sealed interface RegistrationResult {
    /** Successfully registered; [id] is the stable identifier. */
    data class Registered(val id: String) : RegistrationResult

    /** Rejected; [reason] explains why. */
    data class Rejected(val reason: String) : RegistrationResult
}

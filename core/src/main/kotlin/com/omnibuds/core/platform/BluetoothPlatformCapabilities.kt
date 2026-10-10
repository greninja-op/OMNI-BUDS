package com.omnibuds.core.platform

import com.omnibuds.core.common.TransportKind

/**
 * A snapshot of what this phone and this Android version can expose.
 *
 * [apiLevel] is nullable and every feature defaults to unknown, because a capability report written
 * before the platform has answered is exactly the kind of confident wrong document that misleads
 * later phases. Nothing in here makes a claim about any headset, and nothing in here may be used to
 * decide what a device supports - that is the capability engine's job against a real device
 * (Phase 2 prompt section 5.5).
 */
data class BluetoothPlatformCapabilities(
    /** The Android API level reported by the platform, or null when it was not obtained. */
    val apiLevel: Int?,

    /** Whether a Bluetooth adapter exists on this device, as reported by the platform. */
    val adapterPresent: ApiAvailability,

    /** Per-feature support, kept sparse: an absent entry means "not determined", not "unsupported". */
    private val features: Map<PlatformFeature, PlatformFeatureSupport>,

    /** Permission standings the platform actually reported, keyed the same way. */
    val permissionStatus: Map<BluetoothPermission, PermissionState>,

    /** transports the platform says it could offer, empty when nothing was probed. */
    val candidateTransports: Set<TransportKind>,

    /** The runtime host platform type. Defaults to UNKNOWN until probed. */
    val platformType: PlatformType = PlatformType.UNKNOWN,
) {
    /** Copy-on-write safe view of the feature map. */
    val supportedFeatures: Map<PlatformFeature, PlatformFeatureSupport>
        get() = features.toMap()

    init {
        apiLevel?.let { level ->
            require(level > 0) { "an apiLevel must be a positive API level, was $level" }
        }
    }

    /** The recorded standing of [feature], or [PlatformFeatureSupport.unknown] if never probed. */
    fun supportFor(feature: PlatformFeature): PlatformFeatureSupport =
        features[feature] ?: PlatformFeatureSupport.unknown()

    /** Features with at least a usable standing. Never inferred from absence of others. */
    val usableFeatures: Set<PlatformFeature>
        get() = features.filter { (_, support) -> support.isUsable }.keys

    /** True only where the report is based on real platform readings rather than defaults. */
    val isObserved: Boolean
        get() = apiLevel != null || adapterPresent != ApiAvailability.UNKNOWN || features.isNotEmpty()

    fun withFeature(feature: PlatformFeature, support: PlatformFeatureSupport): BluetoothPlatformCapabilities =
        copy(features = features + (feature to support))

    companion object {
        /** The state before anything has been asked of the platform. */
        fun unobserved(): BluetoothPlatformCapabilities = BluetoothPlatformCapabilities(
            apiLevel = null,
            adapterPresent = ApiAvailability.UNKNOWN,
            features = emptyMap(),
            permissionStatus = emptyMap(),
            candidateTransports = emptySet(),
        )
    }
}

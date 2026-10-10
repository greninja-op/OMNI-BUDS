package com.omnibuds.core.platform

import com.omnibuds.core.common.TransportKind

/**
 * Immutable metadata describing the host platform environment, OS version, architecture,
 * and available Bluetooth capabilities.
 *
 * Core uses this to evaluate platform constraints honestly without probing host OS
 * internals directly.
 */
data class PlatformDescriptor(
    val platformType: PlatformType,
    val osName: String? = null,
    val osVersion: String? = null,
    val architecture: String? = null,
    val apiLevel: Int? = null,
    val candidateTransports: Set<TransportKind> = emptySet(),
    val capabilities: BluetoothPlatformCapabilities = BluetoothPlatformCapabilities.unobserved(),
) {
    val isDesktop: Boolean get() = platformType.isDesktop
    val isMobile: Boolean get() = platformType.isMobile

    init {
        apiLevel?.let { level ->
            require(level > 0) { "apiLevel must be positive, was $level" }
        }
    }

    companion object {
        fun unobserved(): PlatformDescriptor = PlatformDescriptor(
            platformType = PlatformType.UNKNOWN,
            osName = null,
            osVersion = null,
            architecture = null,
            apiLevel = null,
            candidateTransports = emptySet(),
            capabilities = BluetoothPlatformCapabilities.unobserved(),
        )
    }
}

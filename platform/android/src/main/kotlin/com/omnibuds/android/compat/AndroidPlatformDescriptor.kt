package com.omnibuds.android.compat

import android.os.Build
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformType

/**
 * Creates platform descriptors for Android hosts based on actual OS Build metadata.
 */
object AndroidPlatformDescriptor {

    /**
     * Constructs a [PlatformDescriptor] from the running device's actual OS properties.
     */
    fun current(capabilities: BluetoothPlatformCapabilities = BluetoothPlatformCapabilities.unobserved()): PlatformDescriptor {
        return PlatformDescriptor(
            platformType = PlatformType.ANDROID,
            osName = "Android",
            osVersion = Build.VERSION.RELEASE,
            architecture = Build.SUPPORTED_ABIS.firstOrNull(),
            apiLevel = Build.VERSION.SDK_INT,
            candidateTransports = capabilities.candidateTransports,
            capabilities = capabilities.copy(platformType = PlatformType.ANDROID),
        )
    }

    /**
     * Constructs an offline or testing [PlatformDescriptor] for a given [apiLevel].
     */
    fun fromApiLevel(
        apiLevel: Int,
        osVersion: String? = null,
        capabilities: BluetoothPlatformCapabilities = BluetoothPlatformCapabilities.unobserved(),
    ): PlatformDescriptor {
        return PlatformDescriptor(
            platformType = PlatformType.ANDROID,
            osName = "Android",
            osVersion = osVersion,
            architecture = null,
            apiLevel = apiLevel,
            candidateTransports = capabilities.candidateTransports,
            capabilities = capabilities.copy(platformType = PlatformType.ANDROID),
        )
    }
}

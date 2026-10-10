package com.omnibuds.android.compat

import com.omnibuds.core.platform.PlatformIdentifierSource
import java.util.UUID

/**
 * Android implementation of [PlatformIdentifierSource] using standard UUID generation.
 */
class AndroidPlatformIdentifierSource : PlatformIdentifierSource {
    override fun generateOperationId(prefix: String?): String {
        val tag = prefix?.takeIf { it.isNotBlank() } ?: "android-op"
        return "$tag-${UUID.randomUUID()}"
    }

    override fun generateNonce(): String {
        return UUID.randomUUID().toString()
    }
}

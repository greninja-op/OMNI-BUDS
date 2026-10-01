package com.omnibuds.android.bluetooth.permission

import android.content.Context
import android.content.pm.PackageManager
import com.omnibuds.core.platform.BluetoothPermission

/**
 * Reads one permission's standing from the platform, in the app's own process.
 *
 * `null` means the platform did not answer - it is not a denial, and treating it as one would turn a
 * read failure into "you refused", which is the same class of error as reporting an unread adapter as
 * off (ADR-P0-016).
 */
fun interface PermissionStandingReader {
    /** True granted, false not granted, null not determinable. */
    fun isGranted(manifestName: String): Boolean?
}

/**
 * The production reader.
 *
 * `Context.checkSelfPermission(String)` answers for the calling process and needs no prompt, which is
 * what makes `PERMISSION_STATUS_INSPECTION` authorised in Phase 2 while asking the user for anything
 * is not (Phase 2 prompt sections 5.3, 6). It exists from API 23, and this module's floor is 26, so
 * no version branch is needed.
 */
class SystemPermissionStandingReader(private val context: Context) : PermissionStandingReader {
    override fun isGranted(manifestName: String): Boolean? =
        runCatching {
            context.checkSelfPermission(manifestName) == PackageManager.PERMISSION_GRANTED
        }.getOrNull()
}

/** The same question, asked about a typed permission rather than a raw name. */
fun PermissionStandingReader.isGranted(permission: BluetoothPermission): Boolean? =
    isGranted(permission.manifestName)

package com.omnibuds.core.platform

/**
 * One reason the app would need one permission for one operation on one Android band.
 *
 * The [reason] is mandatory and is the project's answer to prompt section 5.3's prohibition on
 * requesting permissions without a documented justification: a requirement that cannot state why
 * it exists cannot be constructed. [required] separates a hard dependency from an advisory or
 * conditional one, because an over-declared requirement is a privacy harm, not a safety margin.
 */
data class PermissionRequirement(
    val operation: BluetoothOperation,
    val permission: BluetoothPermission,
    val appliesTo: ApiRange,
    val required: Boolean,
    val reason: String,
) {
    init {
        require(reason.isNotBlank()) {
            "a permission requirement must state why it exists; an unjustified request is prohibited"
        }
    }

    /** True when this record is the one that applies on a device at [sdkInt]. */
    fun appliesAt(sdkInt: Int): Boolean = appliesTo.contains(sdkInt)
}

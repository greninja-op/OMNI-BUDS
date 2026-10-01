package com.omnibuds.core.platform

/**
 * What is known about one permission's standing with the user.
 *
 * The set is deliberately wider than granted/denied. Phase 2 prompt section 5.3 forbids two
 * specific failures that a two-valued model causes: claiming permanent denial when the platform
 * did not establish it, and silently bypassing a denial by treating it as absence of requirement.
 * Hence [UNKNOWN] and [NOT_REQUESTED] are distinct from [DENIED], and [DENIED_PERMANENTLY] is
 * only reachable from a platform signal that actually supports it - never inferred from a single
 * refusal.
 */
enum class PermissionState {
    /** The operation does not need this permission on this Android version. */
    NOT_REQUIRED,

    /** Required, but no request has been made in this session. */
    NOT_REQUESTED,

    /** Granted by the platform. */
    GRANTED,

    /** Refused at least once; the platform has not established that it is final. */
    DENIED,

    /** The platform reported an outcome that supports treating further prompts as useless. */
    DENIED_PERMANENTLY,

    /** The user must act outside the app (settings, or a prompt the app cannot issue). */
    REQUIRES_USER_ACTION,

    /** Could not be determined - not a denial, and not a grant. */
    UNKNOWN,
}

/** Whether a permission in this state may be treated as held for a dependent operation. */
fun PermissionState.isGranted(): Boolean = this == PermissionState.GRANTED

/**
 * Whether the app may proceed as if the permission were absent, rather than reporting a refusal.
 *
 * Only [NOT_REQUIRED] and a genuine grant allow that. Every other state must surface an outcome to
 * the user, because pretending otherwise is how a permission denial becomes a silent failure.
 */
fun PermissionState.allowsSilentProceeding(): Boolean = this == PermissionState.NOT_REQUIRED

/** Whether the state is a refusal of any kind, temporary or final. */
fun PermissionState.isRefused(): Boolean =
    this == PermissionState.DENIED ||
    this == PermissionState.DENIED_PERMANENTLY ||
    this == PermissionState.REQUIRES_USER_ACTION

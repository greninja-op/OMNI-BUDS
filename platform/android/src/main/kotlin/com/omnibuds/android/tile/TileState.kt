package com.omnibuds.android.tile

/**
 * Platform-independent tile presentation state.
 *
 * Phase 25 (OB-P25-REQ-003): deterministic mapping target. This model has
 * no Android dependencies so the mapping is fully unit-testable.
 */
data class TileState(
    /** Whether the tile appears active. */
    val active: Boolean,
    /** Primary label — never a fabricated hardware value. */
    val label: String,
    /** Secondary text, or null. Never sensitive data. */
    val subtitle: String?,
    /** Whether the tile accepts clicks. */
    val clickable: Boolean,
    /** Machine-readable state for tests and diagnostics. */
    val kind: TileKind,
)

/**
 * The distinct tile conditions.
 */
enum class TileKind {
    /** No device tracked. */
    NO_DEVICE,

    /** Device known but not connected. */
    DISCONNECTED,

    /** Connected but identity not established. */
    UNIDENTIFIED,

    /** Capability discovery in progress. */
    DISCOVERING,

    /** Ready but no verified controllable feature. */
    READY_NO_ACTION,

    /** Ready with an available verified action. */
    READY_WITH_ACTION,

    /** A hardware operation is in flight. */
    OPERATION_PENDING,

    /** Device temporarily unavailable. */
    UNAVAILABLE,

    /** State unknown or stale — honest, not guessed. */
    UNKNOWN,

    /** Last operation failed. */
    FAILED,
}

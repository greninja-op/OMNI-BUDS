package com.omnibuds.core.common

/**
 * The canonical failure categories for OmniBuds operations.
 *
 * This is the superset required by ADR-P0-012, the three categories the Phase 1 prompt
 * adds (ADR-P1-006), and the eight platform-failure categories Phase 2 needs (ADR-P2-004). A category names what is known to be wrong; it never carries a
 * fabricated value, and an unread measurement stays unknown instead of becoming a
 * zero (ADR-P0-016).
 */
enum class OmniBudsErrorCategory(
    /** Whether and how the failed operation may be attempted again. */
    val retryClass: RetryClass,
    /**
     * Whether the device's true state is now uncertain, meaning capability and
     * state records must be re-discovered before another write is permitted.
     */
    val invalidatesSession: Boolean,
) {
    BLUETOOTH_DISABLED(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = false),
    PERMISSION_DENIED(RetryClass.NEVER_RETRY, invalidatesSession = false),
    DEVICE_DISCONNECTED(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    TRANSPORT_UNAVAILABLE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = false),
    GATT_FAILURE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    RFCOMM_FAILURE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    PROTOCOL_MISMATCH(RetryClass.NEVER_RETRY, invalidatesSession = true),
    UNSUPPORTED_FEATURE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    READ_FAILED(RetryClass.SAFE_TO_RETRY, invalidatesSession = false),
    WRITE_REJECTED(RetryClass.NEVER_RETRY, invalidatesSession = true),
    VERIFICATION_FAILED(RetryClass.NEVER_RETRY, invalidatesSession = true),
    TIMEOUT(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    FIRMWARE_MISMATCH(RetryClass.NEVER_RETRY, invalidatesSession = false),
    CODEC_UNAVAILABLE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    UNKNOWN_DEVICE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    INVALID_STATE(RetryClass.NEVER_RETRY, invalidatesSession = false),

    // Phase 2 platform-failure categories, ADR-P2-004. Cancellation is deliberately absent:
    // it is an OperationOutcome case, not an error (ADR-P1-004). An unreadable adapter state is
    // not a category either - BluetoothAdapterState.UNKNOWN carries that meaning, and a category
    // for it would let the same fact be reported two ways that can disagree.
    ADAPTER_UNAVAILABLE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = false),
    UNSUPPORTED_OPERATION(RetryClass.NEVER_RETRY, invalidatesSession = false),
    PLATFORM_API_UNAVAILABLE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    CONNECTION_UNAVAILABLE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    RESOURCE_UNAVAILABLE(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = false),
    PLATFORM_EXCEPTION(RetryClass.RETRY_AFTER_REREAD, invalidatesSession = true),
    UNKNOWN_FAILURE(RetryClass.NEVER_RETRY, invalidatesSession = true),

    // Phase 10 audio-observation categories, ADR-P10-007. Observation is
    // read-only, so none of these invalidate a device session: a failed read
    // leaves the device state exactly as uncertain as it was, which is to say
    // not at all — the snapshot simply keeps its previous values.
    /**
     * Reading Bluetooth profiles or audio devices failed. Retrying is a fresh
     * read with no side effects, so a bounded retry is safe — but the engine
     * itself never polls; the retry decision belongs to the host.
     */
    AUDIO_OBSERVATION_FAILED(RetryClass.SAFE_TO_RETRY, invalidatesSession = false),
    /**
     * LE Audio was requested on a platform that cannot offer it (API < 33, or
     * the API level undetermined). Never retried: the answer will not change
     * without an OS upgrade.
     */
    LE_AUDIO_UNAVAILABLE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    /**
     * Observation sources contradicted each other and the reconciler preserved
     * UNKNOWN rather than guessing. Not a failure of any device — the
     * diagnostic on the snapshot names the sources — so never retried and the
     * session untouched.
     */
    AUDIO_STATE_CONFLICT(RetryClass.NEVER_RETRY, invalidatesSession = false),
    /**
     * Codec observation failed (platform read threw, permission denied
     * mid-read). Safe to retry: codec reads are side-effect-free, like audio
     * reads (ADR-P10-007 precedent). Never invalidates the session.
     */
    CODEC_OBSERVATION_FAILED(RetryClass.SAFE_TO_RETRY, invalidatesSession = false),
    /**
     * The codec state is not observable on this platform (no public API
     * exposes it). Never retried: the answer will not change without an OS
     * upgrade. Distinct from "unsupported" — the codec may exist; OmniBuds
     * simply cannot see its state.
     */
    CODEC_NOT_OBSERVABLE(RetryClass.NEVER_RETRY, invalidatesSession = false),
    /**
     * A codec runtime record aged out or its device disconnected. Safe to
     * retry by re-observing; the stale record is kept explicitly stale, never
     * silently current.
     */
    CODEC_STATE_STALE(RetryClass.SAFE_TO_RETRY, invalidatesSession = false),
}

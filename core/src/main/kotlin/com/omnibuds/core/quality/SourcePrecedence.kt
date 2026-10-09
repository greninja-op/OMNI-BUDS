package com.omnibuds.core.quality

/**
 * The documented source precedence for audio quality resolution.
 *
 * Phase 13 (OB-P13-REQ-019, OB-P13-REQ-021): when sources disagree, the higher
 * precedence wins. Vendor data is never placed above runtime platform evidence
 * without justification. The ordering:
 *
 * 1. [VERIFIED_RUNTIME_OBSERVATION] — a runtime fact confirmed by
 *    re-observation (e.g. Phase 12 VERIFIED control state).
 * 2. [PLATFORM_RUNTIME_METADATA] — what the platform reports right now
 *    (transport snapshot, audio-device observation).
 * 3. [VERIFIED_DEVICE_PROTOCOL] — a verified vendor protocol read-back.
 *    (No verified protocol exists today; this rank is reserved.)
 * 4. [CAPABILITY_DATABASE] — declared capabilities (SUPPORTED rung).
 * 5. [STATIC_INFERENCE] — transport-family implications (e.g. LE Audio
 *    implies an LE codec family, never a specific codec).
 * 6. [UNKNOWN] — no information.
 *
 * Higher [rank] wins. Ties are resolved toward the fresher timestamp; if
 * timestamps tie, the conflict is flagged ([AudioQualityState.hasConflict])
 * rather than silently chosen.
 */
enum class SourcePrecedence(val rank: Int) {
    VERIFIED_RUNTIME_OBSERVATION(6),
    PLATFORM_RUNTIME_METADATA(5),
    VERIFIED_DEVICE_PROTOCOL(4),
    CAPABILITY_DATABASE(3),
    STATIC_INFERENCE(2),
    UNKNOWN(1),
}

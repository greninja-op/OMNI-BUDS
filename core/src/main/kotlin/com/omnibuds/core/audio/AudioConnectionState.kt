package com.omnibuds.core.audio

/**
 * The connection state of one Bluetooth audio transport, as observed — never commanded.
 *
 * This is the state axis of the Phase 10 audio transport engine (OB-P10-REQ-004,
 * OB-P10-REQ-006). Every value describes what the platform reported about a
 * transport; nothing here opens, closes, suspends or resumes one. Observation is
 * not control (master Phase 10 rule 2): an observer that could move a transport
 * between these states would be a controller wearing an observer's name.
 *
 * Three distinctions are load-bearing and must not be collapsed:
 *
 * - [CONNECTED] is not [ACTIVE]. A transport can be connected while the system
 *   routes audio elsewhere, or while nothing is playing at all.
 * - [ACTIVE] is not "currently playing media". Active means the platform has
 *   this transport in the active audio path; silence on an active transport is
 *   still active.
 * - [UNKNOWN] is not [DISCONNECTED]. Unknown means the platform has not told us
 *   yet, or the sources disagree. Treating an unread state as disconnected would
 *   fabricate a negative claim (ADR-P0-016).
 *
 * The ordering of the enum entries carries no ranking: a declaration order is
 * not a priority, and Phase 10 defines no transport priority at all
 * (OB-P10-REQ-014). The same device may expose several transports at once, and
 * each is tracked independently.
 */
enum class AudioConnectionState {
    /**
     * The platform has not reported a state, or the reporting sources disagree
     * and reconciliation refused to guess (OB-P10-REQ-016). This is the initial
     * state of every freshly observed transport and the honest answer whenever
     * evidence is absent or contradictory.
     */
    UNKNOWN,

    /**
     * The transport is not connected. This is a positive observation of absence
     * (the platform said "not connected"), never a default for "not read yet" —
     * that is [UNKNOWN].
     */
    DISCONNECTED,

    /** A connection is being established, as reported by the platform. */
    CONNECTING,

    /**
     * Connected, but not necessarily carrying audio. An A2DP profile can sit in
     * this state for hours while the phone plays through its speaker.
     */
    CONNECTED,

    /**
     * The platform currently routes audio through this transport. This says
     * nothing about whether media is playing right now, and nothing about which
     * codec is in use — codec state is Phase 11 territory.
     */
    ACTIVE,

    /**
     * The transport was active and the platform has parked it without
     * disconnecting — for example an HFP link held open while no call is up, or
     * a transport the system suspended in favour of another route. Suspended is
     * recoverable without reconnecting, which is what distinguishes it from
     * [DISCONNECTED].
     */
    SUSPENDED,

    /** A disconnection is in progress, as reported by the platform. */
    DISCONNECTING,
}

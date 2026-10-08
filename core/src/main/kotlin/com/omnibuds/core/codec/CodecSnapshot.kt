package com.omnibuds.core.codec

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecCapability
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.device.DeviceIdentity

/**
 * The normalized codec picture for one device at one moment.
 *
 * Immutable: each observation replaces the previous snapshot; nothing mutates
 * in place and no mutable collections escape. One snapshot per device — codec
 * state from device A never appears in device B's snapshot (Phase 11 §22, §35).
 *
 * The snapshot answers, per device:
 * - which codecs are known and at which [CodecState] rung ([capabilities]),
 * - what the codec is doing right now ([runtimeState]),
 * - whether the platform can even observe the answers ([observability]),
 * - what the platform will not tell us ([limitations]).
 */
data class CodecSnapshot(
    /** Schema version; readers must fail fast on mismatch. */
    val schemaVersion: Int = SCHEMA_VERSION,
    /** Epoch millis when this snapshot was produced. */
    val timestampMillis: Long,
    /** The device this snapshot describes. */
    val deviceId: DeviceIdentity,
    /** The transport these codec claims belong to (A2DP vs LE Audio). */
    val transport: AudioTransportKind,
    /**
     * One capability record per known codec. Immutable list; never merged
     * across devices.
     */
    val capabilities: List<CodecCapability>,
    /**
     * The live runtime state, or null when nothing was observed (distinct from
     * "observed idle" — null means no observation, not a negative claim).
     */
    val runtimeState: CodecRuntimeState?,
    /**
     * Platform observability for codec state on this device/transport.
     * [CodecObservability.NOT_OBSERVABLE] with UNKNOWN states is "unexposed",
     * never "unsupported".
     */
    val observability: CodecObservability,
    /**
     * Explicit platform limitations, e.g. "active A2DP codec has no public
     * observation API below API 35". Never hidden to look more complete.
     */
    val limitations: List<String>,
    /** Bounded machine-readable notes about uncertainty. */
    val diagnostics: List<CodecDiagnostic>,
) {
    /**
     * The codec currently carrying audio, or null when the platform did not
     * identify one. Null is "unreported", never "nothing playing".
     */
    val activeCodec: Codec?
        get() = runtimeState
            ?.takeIf { it.state == CodecState.ACTIVE }
            ?.codec

    companion object {
        const val SCHEMA_VERSION: Int = 1

        /** Maximum diagnostics kept per snapshot; oldest are dropped. */
        const val MAX_DIAGNOSTICS: Int = 8
    }
}

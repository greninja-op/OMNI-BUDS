package com.omnibuds.android.bluetooth.audio.codec

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecCapability
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.codec.CodecObservationSource
import com.omnibuds.core.codec.CodecRuntimeState
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Android implementation of [CodecObservationSource].
 *
 * Translates platform codec information into domain models. Honest about
 * limits:
 *
 * - Local supported codecs: observable on API 35+ via
 *   `BluetoothA2dp.getSupportedCodecTypes()` → [CodecState.SUPPORTED] with
 *   [EvidenceConfidence.OBSERVED]. Below API 35: UNKNOWN + NOT_OBSERVABLE.
 * - Active/negotiated codec: NO public Android API exposes it at any API
 *   level → UNKNOWN + NOT_OBSERVABLE, always. This is a platform limitation,
 *   never a device limitation.
 * - LE Audio (LC3) runtime state: no public getter on `BluetoothLeAudio` →
 *   NOT_OBSERVABLE.
 * - aptX Adaptive / aptX Lossless: no platform constant → identity exists in
 *   the domain but is NOT_OBSERVABLE via public APIs.
 *
 * Permission-first: without BLUETOOTH_CONNECT the handle returns empty, which
 * becomes UNKNOWN capabilities — never a throw, never a false negative.
 */
class AndroidCodecObservationSource(
    private val handle: CodecObservationHandle,
) : CodecObservationSource {

    override suspend fun readCapabilities(device: DeviceIdentity): List<CodecCapability> {
        val raw = handle.readLocalSupportedCodecIds()
        if (raw.isEmpty()) {
            // Nothing observable: report every known codec as UNKNOWN with
            // NOT_OBSERVABLE. This is "unexposed", never "unsupported".
            return Codec.entries.map { codec ->
                CodecCapability(
                    codec = codec,
                    state = CodecState.UNKNOWN,
                    configurable = false,
                    evidence = CodecEvidence(
                        CodecEvidenceSource.ANDROID_FRAMEWORK,
                        EvidenceConfidence.UNKNOWN,
                        detail = "no public codec API exposed a capability list",
                    ),
                    observability = CodecObservability.NOT_OBSERVABLE,
                )
            }
        }
        val supported = raw.mapNotNull { info ->
            val codec = when (info.idKind) {
                CodecIdKind.CODEC_ID ->
                    com.omnibuds.android.bluetooth.audio.codec.mapping.codecFromCodecId(
                        info.platformCodecId,
                    )
                CodecIdKind.SOURCE_CODEC_TYPE ->
                    com.omnibuds.android.bluetooth.audio.codec.mapping.codecFromSourceCodecType(
                        info.platformCodecId,
                    )
            } ?: return@mapNotNull null
            CodecCapability(
                codec = codec,
                // getSupportedCodecTypes() reports the LOCAL phone's codecs:
                // SUPPORTED rung, OBSERVED confidence. It says nothing about
                // the peer device — per-endpoint support is not merged.
                state = CodecState.SUPPORTED,
                configurable = false,
                evidence = CodecEvidence(
                    CodecEvidenceSource.ANDROID_FRAMEWORK,
                    EvidenceConfidence.OBSERVED,
                    detail = "BluetoothA2dp.getSupportedCodecTypes()",
                ),
                observability = CodecObservability.OBSERVABLE,
            )
        }
        val supportedCodecs = supported.map { it.codec }.toSet()
        val unknown = Codec.entries
            .filter { it != Codec.UNKNOWN && it !in supportedCodecs }
            .map { codec ->
                CodecCapability(
                    codec = codec,
                    state = CodecState.UNKNOWN,
                    configurable = false,
                    evidence = CodecEvidence.unknown(),
                    observability = CodecObservability.NOT_OBSERVABLE,
                )
            }
        return supported + unknown
    }

    override suspend fun readRuntimeState(device: DeviceIdentity): CodecRuntimeState? {
        // No public Android API exposes the active/negotiated codec.
        // Returning null is "unobserved" — the engine records the limitation
        // explicitly rather than inventing a codec.
        return null
    }

    override fun observeRuntimeStates(device: DeviceIdentity): Flow<CodecRuntimeState> {
        // No codec-change broadcast is public; there is nothing event-driven
        // to observe. The engine's refresh() path is the observation mechanism.
        return emptyFlow()
    }

    companion object {
        /** The transport these observations belong to. */
        val TRANSPORT: AudioTransportKind = AudioTransportKind.CLASSIC_A2DP
    }
}

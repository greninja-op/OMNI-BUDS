package com.omnibuds.android.bluetooth.audio.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.codec.CodecApplyOutcome
import com.omnibuds.core.codec.CodecConfiguration
import com.omnibuds.core.codec.CodecControlAdapter
import com.omnibuds.core.codec.CodecOperation
import com.omnibuds.core.device.DeviceIdentity

/**
 * The Android platform seam for codec control operations.
 *
 * Phase 12 (OB-P12-REQ-034): this adapter is honest about what public Android
 * APIs can actually do. No public Android API — on any API level — exposes
 * codec *selection* or codec *configuration* to a third-party app:
 *
 * - `BluetoothA2dp` has no `setCodecConfig`, no `setCodecPriority`, no
 *   `selectCodec` in the public SDK.
 * - `BluetoothLeAudio` has no codec setter in the public SDK.
 * - `BluetoothCodecConfig.setCodecPriority` existed only as a hidden/system
 *   API and is not part of the public contract OmniBuds may use.
 *
 * Therefore [apply] always returns [CodecApplyOutcome.NotAvailable] with the
 * reason naming the missing mechanism. This is not a stub to be "filled in
 * later" with a hack — it is the correct behavior until and unless Android
 * ships a public codec-control API or a verified vendor protocol provides a
 * legitimate path (which would arrive as a separate adapter behind the same
 * [CodecControlAdapter] interface).
 *
 * [observeAfterApply] delegates to the Phase 11 observation source so
 * verification re-observes through the same honest path.
 */
class AndroidCodecControlAdapter(
    private val observationSource: AndroidCodecObservationSource,
) : CodecControlAdapter {

    override suspend fun apply(operation: CodecOperation): CodecApplyOutcome =
        CodecApplyOutcome.NotAvailable(
            reason = "No public Android API exposes codec ${operation.type.name.lowercase()} " +
                "for ${operation.codec}; codec control requires a platform API or " +
                "verified vendor protocol that does not exist on this device.",
        )

    override suspend fun observeAfterApply(
        device: DeviceIdentity,
        codec: Codec,
    ): CodecConfiguration? {
        // Re-observe through the Phase 11 path. The runtime state is
        // NOT_OBSERVABLE on public APIs (readRuntimeState returns null), so
        // this returns null — and the engine correctly treats that as
        // "cannot verify".
        observationSource.readRuntimeState(device) ?: return null
        // Unreachable on public APIs: the runtime state is never observable.
        // If a future platform exposes it, map the active codec here.
        return null
    }
}

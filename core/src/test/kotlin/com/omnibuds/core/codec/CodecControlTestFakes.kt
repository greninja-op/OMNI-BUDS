package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.delay

/** Shared fakes for codec control engine tests. */

fun testDevice(name: String = "Test Buds"): DeviceIdentity =
    DeviceIdentity(manufacturer = "Test", model = name, displayName = name)

fun testEvidence(detail: String = "test"): CodecEvidence = CodecEvidence(
    source = CodecEvidenceSource.UNKNOWN,
    confidence = EvidenceConfidence.UNKNOWN,
    observedAtMillis = 0L,
    detail = detail,
)

/** A capability with every control dimension enabled. */
fun fullControlCapability(codec: Codec): CodecControlCapability =
    CodecControlCapability(
        codec = codec,
        observable = true,
        supported = true,
        selectable = true,
        configurable = true,
        verifiable = true,
        evidence = testEvidence("full control"),
    )

/** The honest Android reality: nothing selectable/configurable/verifiable. */
fun androidRealityCapability(codec: Codec, supported: Boolean = true): CodecControlCapability =
    CodecControlCapability(
        codec = codec,
        observable = true,
        supported = supported,
        selectable = false,
        configurable = false,
        verifiable = false,
        evidence = testEvidence("no public API for control"),
    )

class FakeResolver(
    private val capabilities: Map<Codec, CodecControlCapability> = emptyMap(),
    private val default: (Codec) -> CodecControlCapability = { androidRealityCapability(it) },
) : CodecControlCapabilityResolver {
    val resolved = mutableListOf<Pair<DeviceIdentity, Codec>>()
    override suspend fun resolve(device: DeviceIdentity, codec: Codec): CodecControlCapability {
        resolved += device to codec
        return capabilities[codec] ?: default(codec)
    }
}

class FakeAdapter(
    var applyOutcome: CodecApplyOutcome = CodecApplyOutcome.Performed,
    var observed: CodecConfiguration? = null,
    var applyDelayMillis: Long = 0L,
) : CodecControlAdapter {
    val applied = mutableListOf<CodecOperation>()
    val observedCalls = mutableListOf<Pair<DeviceIdentity, Codec>>()

    override suspend fun apply(operation: CodecOperation): CodecApplyOutcome {
        applied += operation
        if (applyDelayMillis > 0) delay(applyDelayMillis)
        return applyOutcome
    }

    override suspend fun observeAfterApply(
        device: DeviceIdentity,
        codec: Codec,
    ): CodecConfiguration? {
        observedCalls += device to codec
        return observed
    }
}

class FakeLiveness(var connected: Boolean = true) : DeviceLiveness {
    override suspend fun isConnected(device: DeviceIdentity): Boolean = connected
}

fun controlEngine(
    resolver: CodecControlCapabilityResolver = FakeResolver(),
    adapter: CodecControlAdapter = FakeAdapter(),
    liveness: DeviceLiveness = FakeLiveness(),
    now: Long = 1_000L,
): CodecControlEngine = CodecControlEngine(
    resolver = resolver,
    adapter = adapter,
    liveness = liveness,
    clockMillis = { now },
)

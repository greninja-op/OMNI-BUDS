package com.omnibuds.core.validation

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.quality.NegotiationState
import com.omnibuds.core.validation.rules.CodecActiveClaimRule
import com.omnibuds.core.validation.rules.CodecTransportAssociationRule
import com.omnibuds.core.validation.rules.DeviceIdentityMatchRule
import com.omnibuds.core.validation.rules.DisconnectedRouteRule
import com.omnibuds.core.validation.rules.FreshnessCoherenceRule
import com.omnibuds.core.validation.rules.ParameterDomainRule
import com.omnibuds.core.validation.rules.RouteConsistencyRule
import com.omnibuds.core.validation.rules.StaleCodecRule
import com.omnibuds.core.validation.rules.TransportCoherenceRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Phase 14 (§21A–G): the concrete validation rules.
 */
class ValidationRuleTest {

    private val device = DeviceIdentity(displayName = "Test Buds")

    private fun input(
        transport: AudioTransportKind? = AudioTransportKind.CLASSIC_A2DP,
        transportConnected: Boolean? = true,
        routeActive: Boolean? = false,
        audioDeviceAvailable: Boolean? = true,
        isCommunicationRoute: Boolean? = false,
        codec: Codec? = Codec.AAC,
        codecState: CodecState? = CodecState.ACTIVE,
        codecFreshness: CodecFreshness? = CodecFreshness.CURRENT,
        negotiationState: NegotiationState? = NegotiationState.ACTIVE,
        sampleRateHz: Int? = 48000,
        bitDepth: Int? = 16,
    ) = ValidationInput(
        device = device,
        sessionGeneration = 0L,
        timestampMillis = 1000L,
        transport = transport,
        transportConnected = transportConnected,
        routeActive = routeActive,
        audioDeviceAvailable = audioDeviceAvailable,
        isCommunicationRoute = isCommunicationRoute,
        codec = codec,
        codecState = codecState,
        codecFreshness = codecFreshness,
        codecSupported = true,
        qualityState = null,
        negotiationState = negotiationState,
        sampleRateHz = sampleRateHz,
        bitDepth = bitDepth,
        channelMode = "STEREO",
        adaptiveState = null,
    )

    // --- Device association ---

    @Test
    fun `disconnected device with active route is invalid`() {
        val r = DisconnectedRouteRule.evaluate(
            input(transportConnected = false, routeActive = true),
        )
        assertEquals(ValidationStatus.INVALID, r.status)
        assertEquals(ValidationSeverity.CRITICAL, r.severity)
    }

    @Test
    fun `disconnected device without route is valid`() {
        val r = DisconnectedRouteRule.evaluate(
            input(transportConnected = false, routeActive = false),
        )
        assertEquals(ValidationStatus.VALID, r.status)
    }

    @Test
    fun `unknown connection is inconclusive`() {
        val r = DisconnectedRouteRule.evaluate(input(transportConnected = null))
        assertEquals(ValidationStatus.INCONCLUSIVE, r.status)
    }

    @Test
    fun `stale codec is stale not invalid`() {
        val r = StaleCodecRule.evaluate(input(codecFreshness = CodecFreshness.STALE))
        assertEquals(ValidationStatus.STALE, r.status)
    }

    // --- Transport ---

    @Test
    fun `unknown transport is not-observable`() {
        val r = TransportCoherenceRule.evaluate(input(transport = AudioTransportKind.UNKNOWN))
        assertEquals(ValidationStatus.NOT_OBSERVABLE, r.status)
    }

    @Test
    fun `communication route on media transport is invalid`() {
        val r = TransportCoherenceRule.evaluate(
            input(
                transport = AudioTransportKind.CLASSIC_A2DP,
                isCommunicationRoute = true,
            ),
        )
        assertEquals(ValidationStatus.INVALID, r.status)
    }

    @Test
    fun `hfp with communication route is valid`() {
        val r = TransportCoherenceRule.evaluate(
            input(
                transport = AudioTransportKind.HFP,
                isCommunicationRoute = true,
            ),
        )
        assertEquals(ValidationStatus.VALID, r.status)
    }

    // --- Codec ---

    @Test
    fun `lc3 over le audio is valid`() {
        val r = CodecTransportAssociationRule.evaluate(
            input(transport = AudioTransportKind.LE_AUDIO, codec = Codec.LC3),
        )
        assertEquals(ValidationStatus.VALID, r.status)
    }

    @Test
    fun `lc3 as a2dp codec is invalid`() {
        val r = CodecTransportAssociationRule.evaluate(
            input(transport = AudioTransportKind.CLASSIC_A2DP, codec = Codec.LC3),
        )
        assertEquals(ValidationStatus.INVALID, r.status)
    }

    @Test
    fun `ldac over le audio is invalid`() {
        val r = CodecTransportAssociationRule.evaluate(
            input(transport = AudioTransportKind.LE_AUDIO, codec = Codec.LDAC),
        )
        assertEquals(ValidationStatus.INVALID, r.status)
    }

    @Test
    fun `aac over hfp is invalid`() {
        val r = CodecTransportAssociationRule.evaluate(
            input(transport = AudioTransportKind.HFP, codec = Codec.AAC),
        )
        assertEquals(ValidationStatus.INVALID, r.status)
    }

    @Test
    fun `unknown codec is inconclusive`() {
        val r = CodecTransportAssociationRule.evaluate(input(codec = Codec.UNKNOWN))
        assertEquals(ValidationStatus.INCONCLUSIVE, r.status)
    }

    @Test
    fun `active negotiation with inactive route is invalid`() {
        val r = CodecActiveClaimRule.evaluate(
            input(routeActive = false, negotiationState = NegotiationState.ACTIVE),
        )
        assertEquals(ValidationStatus.INVALID, r.status)
    }

    // --- Route ---

    @Test
    fun `available but unselected device is valid`() {
        val r = RouteConsistencyRule.evaluate(
            input(audioDeviceAvailable = true, routeActive = false),
        )
        assertEquals(ValidationStatus.VALID, r.status)
    }

    @Test
    fun `unobservable route is not-observable`() {
        val r = RouteConsistencyRule.evaluate(
            input(audioDeviceAvailable = null, routeActive = null),
        )
        assertEquals(ValidationStatus.NOT_OBSERVABLE, r.status)
    }

    // --- Parameters ---

    @Test
    fun `valid parameters pass`() {
        val r = ParameterDomainRule.evaluate(input(sampleRateHz = 48000, bitDepth = 16))
        assertEquals(ValidationStatus.VALID, r.status)
    }

    @Test
    fun `uncommon but legal parameters pass`() {
        val r = ParameterDomainRule.evaluate(input(sampleRateHz = 88200, bitDepth = 24))
        assertEquals(ValidationStatus.VALID, r.status)
    }

    @Test
    fun `missing parameters are valid`() {
        val r = ParameterDomainRule.evaluate(input(sampleRateHz = null, bitDepth = null))
        assertEquals(ValidationStatus.VALID, r.status)
    }

    @Test
    fun `impossible sample rate is invalid`() {
        val r = ParameterDomainRule.evaluate(input(sampleRateHz = -1))
        assertEquals(ValidationStatus.INVALID, r.status)
    }

    // --- Freshness ---

    @Test
    fun `current freshness is valid`() {
        val r = FreshnessCoherenceRule.evaluate(input(codecFreshness = CodecFreshness.CURRENT))
        assertEquals(ValidationStatus.VALID, r.status)
    }

    @Test
    fun `unknown freshness is inconclusive`() {
        val r = FreshnessCoherenceRule.evaluate(input(codecFreshness = CodecFreshness.UNKNOWN))
        assertEquals(ValidationStatus.INCONCLUSIVE, r.status)
    }

    // --- Device identity ---

    @Test
    fun `missing quality state is inconclusive`() {
        val r = DeviceIdentityMatchRule.evaluate(input())
        assertEquals(ValidationStatus.INCONCLUSIVE, r.status)
    }
}

package com.omnibuds.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [IdentityEngine]'s matching, ambiguity and confidence behaviour under prompt §8, §9 and §13, and
 * §19's matching matrix (exact, likely, manufacturer-only, unknown, ambiguous, insufficient,
 * conflicting, malformed).
 *
 * Every rule here is an inert fixture built with a fake evidence citation; none claims a real
 * product (ADR-P5-006 - the production registry ships empty, tested in `PhaseFiveRegistryTest`).
 * Tier T1.
 */
class IdentityEngineTest {

    private val sony = ManufacturerIdentity("sony", "Sony")
    private val bose = ManufacturerIdentity("bose", "Bose")

    private fun nameRule(
        manufacturer: ManufacturerIdentity,
        model: ModelIdentity?,
        normalized: String,
        confidence: IdentificationConfidence,
    ): IdentificationRule = IdentificationRule(
        ruleId = "rule-" + manufacturer.canonicalId + "-" + (model?.modelId ?: "mfr"),
        manufacturer = manufacturer,
        model = model,
        conditions = listOf(MatchCondition.NormalizedNameEquals(normalized)),
        confidence = confidence,
        ruleVersion = 1,
        evidence = EvidenceSource.VENDOR_PUBLICATION,
        evidenceReference = "fixture-citation",
        knownLimitations = "fixture rule; establishes nothing about a real device",
    )

    private fun registryOf(vararg rules: IdentificationRule): DeviceIdentityRegistry =
        DeviceIdentityRegistry.builtIn(
            manufacturers = listOf(sony, bose),
            rules = rules.toList(),
        )

    private fun name(value: String): IdentitySignal = IdentitySignal.observed(
        kind = IdentitySignalKind.REPORTED_NAME,
        value = value,
        source = SignalSource.PLATFORM,
        reliability = SignalReliability.VENDOR_REPORTED_TEXT,
    )

    private fun service(uuid: String): IdentitySignal = IdentitySignal.observed(
        kind = IdentitySignalKind.SERVICE_UUID,
        value = uuid,
        source = SignalSource.PLATFORM,
        reliability = SignalReliability.PLATFORM_ATTRIBUTE,
    )

    @Test
    fun theProductionEmptyRegistryIdentifiesNothing() {
        val engine = IdentityEngine(DeviceIdentityRegistry.empty())

        val result = engine.identify(listOf(name("Example Buds")))

        assertIs<IdentificationResult.Unknown>(result)
        assertFalse(result.isIdentified)
    }

    @Test
    fun identicalSignalsProduceAnIdenticalResultAndKey() {
        val rule = nameRule(sony, ModelIdentity("sony", "wf", "WF"), "example buds", IdentificationConfidence.MODERATE)
        val engine = IdentityEngine(registryOf(rule))
        val signals = listOf(name("Example Buds"), service("0000180f-0000-1000-8000-00805f9b34fb"))

        val first = engine.identify(signals)
        val second = engine.identify(signals)

        assertEquals(first, second, "identification is a pure function of its evidence")
        assertEquals(engine.buildFingerprint(signals).identityKey(), engine.buildFingerprint(signals).identityKey())
    }

    @Test
    fun twoIndependentKindsAtHighProduceAnExactMatch() {
        val rule = IdentificationRule(
            ruleId = "exact-two-kind",
            manufacturer = sony,
            model = ModelIdentity("sony", "wf", "WF"),
            conditions = listOf(
                MatchCondition.NormalizedNameEquals("example buds"),
                MatchCondition.CachedServiceUuidEquals("0000180F-0000-1000-8000-00805F9B34FB"),
            ),
            confidence = IdentificationConfidence.HIGH,
            ruleVersion = 1,
            evidence = EvidenceSource.STANDARDS_ASSIGNMENT,
            evidenceReference = "fixture-citation",
            knownLimitations = "fixture",
        )
        val engine = IdentityEngine(registryOf(rule))

        val result = engine.identify(
            listOf(name("Example Buds"), service("0000180f-0000-1000-8000-00805f9b34fb")),
        )

        assertIs<IdentificationResult.Exact>(result)
        assertEquals(IdentificationConfidence.HIGH, result.confidence)
        assertTrue(result.isIdentified)
    }

    @Test
    fun aHighRuleBackedByOneKindIsCappedAndLandsAtLikely() {
        // ADR-P5-004: one name is one kind however many signals carry it, so HIGH must not survive.
        val rule = nameRule(
            sony,
            ModelIdentity("sony", "wf", "WF"),
            "example buds",
            IdentificationConfidence.HIGH,
        )
        val engine = IdentityEngine(registryOf(rule))

        val result = engine.identify(listOf(name("Example Buds")))

        assertIs<IdentificationResult.Likely>(result)
        assertEquals(IdentificationConfidence.MODERATE, result.confidence)
    }

    @Test
    fun aModellessRuleEstablishesTheManufacturerOnly() {
        val rule = nameRule(sony, model = null, normalized = "example buds", confidence = IdentificationConfidence.MODERATE)
        val engine = IdentityEngine(registryOf(rule))

        val result = engine.identify(listOf(name("Example Buds")))

        assertIs<IdentificationResult.ManufacturerOnly>(result)
        assertTrue(result.isIdentified)
    }

    @Test
    fun disagreeingSurvivorsStayAmbiguousAndNameNoWinner() {
        val sonyRule = nameRule(sony, null, "example buds", IdentificationConfidence.MODERATE)
        val boseRule = nameRule(bose, null, "example buds", IdentificationConfidence.MODERATE)
        val engine = IdentityEngine(registryOf(sonyRule, boseRule))

        val result = engine.identify(listOf(name("Example Buds")))

        assertIs<IdentificationResult.Ambiguous>(result)
        assertFalse(result.isIdentified)
        assertEquals(2, result.candidates.size)
        assertFalse(result.maySupportProtocolResolution)
    }

    @Test
    fun noRuleFiringIsUnknownNotAFailure() {
        val rule = nameRule(sony, null, "totally other name", IdentificationConfidence.MODERATE)
        val engine = IdentityEngine(registryOf(rule))

        val result = engine.identify(listOf(name("Example Buds")))

        assertIs<IdentificationResult.Unknown>(result)
    }

    @Test
    fun onlyUnusableSignalsIsInsufficientEvidenceNotUnknown() {
        val rule = nameRule(sony, null, "example buds", IdentificationConfidence.MODERATE)
        val engine = IdentityEngine(registryOf(rule))

        val result = engine.identify(
            listOf(IdentitySignal.unavailable(IdentitySignalKind.MANUFACTURER_DATA)),
        )

        assertIs<IdentificationResult.InsufficientEvidence>(result)
        assertFalse(result.isIdentified)
    }

    @Test
    fun malformedInputBecomesInvalidEvidenceAndNeverThrows() {
        val rule = nameRule(sony, null, "example buds", IdentificationConfidence.MODERATE)
        val engine = IdentityEngine(registryOf(rule))
        val malformed = IdentitySignal.observed(
            kind = IdentitySignalKind.REPORTED_NAME,
            value = "x".repeat(IdentitySignal.MAX_VALUE_LENGTH + 1),
            source = SignalSource.PLATFORM,
            reliability = SignalReliability.VENDOR_REPORTED_TEXT,
        )

        val result = engine.identify(listOf(malformed))

        assertIs<IdentificationResult.InvalidEvidence>(result)
        assertEquals(listOf(IdentitySignalKind.REPORTED_NAME), result.rejectedKinds)
    }

    @Test
    fun aPartialRuleMatchDoesNotFireOnTheSatisfiedHalfAlone() {
        val rule = IdentificationRule(
            ruleId = "needs-both",
            manufacturer = sony,
            model = ModelIdentity("sony", "wf", "WF"),
            conditions = listOf(
                MatchCondition.NormalizedNameEquals("example buds"),
                MatchCondition.CachedServiceUuidEquals("0000180f-0000-1000-8000-00805f9b34fb"),
            ),
            confidence = IdentificationConfidence.HIGH,
            ruleVersion = 1,
            evidence = EvidenceSource.VENDOR_PUBLICATION,
            evidenceReference = "fixture",
            knownLimitations = "fixture",
        )
        val engine = IdentityEngine(registryOf(rule))

        // Name present, service absent: the rule cannot fire, and a missing signal is not a negative.
        val result = engine.identify(listOf(name("Example Buds")))

        assertIs<IdentificationResult.Unknown>(result)
    }

    @Test
    fun theFingerprintCarriesOnlyDimensionsWithAProducer() {
        val engine = IdentityEngine(DeviceIdentityRegistry.empty())

        val fingerprint = engine.buildFingerprint(
            listOf(
                name("Example Buds"),
                service("0000180f-0000-1000-8000-00805f9b34fb"),
            ),
        )

        assertTrue(fingerprint.serviceUuids.isNotEmpty())
        assertTrue(fingerprint.manufacturerData.isEmpty(), "Phase 5 cannot read advertisement data")
        assertTrue(fingerprint.characteristicUuids.isEmpty(), "characteristic discovery is forbidden here")
        assertTrue(fingerprint.transportCandidates.isEmpty(), "no transport is established by a passive read")
    }

    @Test
    fun everyOutcomeTravelsWithTheRegistryVersions() {
        val engine = IdentityEngine(DeviceIdentityRegistry.builtIn(registryVersion = 7, ruleSetVersion = 9))

        val result = engine.identify(listOf(name("Example Buds")))

        assertEquals(7, result.registryVersion)
        assertEquals(9, result.ruleSetVersion)
    }
}

package com.omnibuds.core.vendor.apple

import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.ManufacturerDataEntry
import com.omnibuds.core.vendor.MatchResult
import com.omnibuds.core.vendor.VendorAdapterContractTest
import com.omnibuds.core.vendor.VendorEvidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val APPLE = 0x004C
private const val OTHER = 0x000F // some other company

private fun fingerprint(
    companyId: Int? = null,
    deviceClass: Int? = null,
) = DeviceFingerprint(
    manufacturerData = if (companyId == null) emptyList()
    else listOf(ManufacturerDataEntry(companyId = companyId)),
    deviceClass = deviceClass,
)

class AppleAirpodsAdapterTest {

    private val adapter = AppleAirpodsAdapter()

    @Test
    fun `apple company id plus audio class matches family`() {
        val result = adapter.match(fingerprint(APPLE, 0x240404))
        assertTrue(result is MatchResult.Matched)
        assertEquals(
            AppleAirpodsAdapter.ADAPTER_ID,
            (result as MatchResult.Matched).adapterId,
        )
    }

    @Test
    fun `apple company id without audio class is ambiguous`() {
        // e.g. an iPhone or Mac: Apple manufacturer, not an audio device.
        val result = adapter.match(fingerprint(APPLE, 0x5A020C))
        assertTrue(result is MatchResult.Ambiguous)
    }

    @Test
    fun `apple company id without class is ambiguous`() {
        val result = adapter.match(fingerprint(APPLE, null))
        assertTrue(result is MatchResult.Ambiguous)
    }

    @Test
    fun `non-apple company id never matches`() {
        val result = adapter.match(fingerprint(OTHER, 0x240404))
        assertTrue(result is MatchResult.NotMatched)
    }

    @Test
    fun `audio class without apple company id never matches`() {
        // A non-Apple headset must not route to the Apple adapter.
        val result = adapter.match(fingerprint(OTHER, 0x240404))
        assertTrue(result is MatchResult.NotMatched)
    }

    @Test
    fun `unobserved fingerprint never matches`() {
        assertTrue(adapter.match(DeviceFingerprint()) is MatchResult.NotMatched)
    }

    @Test
    fun `matching is deterministic`() {
        val fp = fingerprint(APPLE, 0x240404)
        val first = adapter.match(fp)
        val second = adapter.match(fp)
        assertEquals(first::class, second::class)
    }

    @Test
    fun `adapter exposes evidence records`() {
        assertTrue(AppleAirpodsAdapter.evidence.isNotEmpty())
    }

    @Test
    fun `null identity evidence matches nothing`() {
        val adapter = AppleAirpodsAdapter(identityEvidence = null)
        assertTrue(
            adapter.match(fingerprint(APPLE, 0x240404))
                is MatchResult.NotMatched,
        )
    }

    @Test
    fun `default identity evidence loads from resource`() {
        val evidence = AirpodsIdentityEvidence.loadDefault()
        assertTrue(evidence != null)
        assertTrue(evidence!!.appleCompanyId > 0)
        assertTrue(evidence.provenance.isNotBlank())
    }

    @Test
    fun `all apple-specific controls are declared unsupported`() {
        val unsupported = AirpodsCapabilityScope.UnsupportedFeature.entries
        assertTrue(unsupported.size >= 10)
        assertTrue(
            unsupported.all { it.reason.isNotBlank() },
            "every unsupported feature must document its reason",
        )
    }

    @Test
    fun `read-only observations never include controls`() {
        val observations = AirpodsCapabilityScope.ReadOnlyObservation.entries
        assertTrue(observations.isNotEmpty())
        assertTrue(
            observations.none {
                it.name.contains("SET") || it.name.contains("CONTROL")
            },
        )
    }

    @Test
    fun `aggregate battery without provenance stays unknown`() {
        val state = AirpodsCapabilityScope.batteryFromAndroidAggregate(
            aggregatePercent = 80,
            sourceDistinguishesBuds = false,
        )
        assertEquals(null, state.leftLevel)
        assertEquals(null, state.rightLevel)
        assertEquals(null, state.caseLevel)
    }

    @Test
    fun `missing battery value stays unknown`() {
        val state = AirpodsCapabilityScope.batteryFromAndroidAggregate(
            aggregatePercent = null,
            sourceDistinguishesBuds = false,
        )
        assertEquals(null, state.leftLevel)
        assertEquals(null, state.rightLevel)
        assertEquals(null, state.caseLevel)
    }
}

class AirpodsContractTest : VendorAdapterContractTest() {
    override fun adapter() = AppleAirpodsAdapter()
    override fun evidence(): List<VendorEvidence> = AppleAirpodsAdapter.evidence
}

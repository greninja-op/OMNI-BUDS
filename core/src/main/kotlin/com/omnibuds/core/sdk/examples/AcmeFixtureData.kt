package com.omnibuds.core.sdk.examples

/**
 * Synthetic fixture data for the Acme reference community adapter.
 *
 * Loaded from packaged JSON resources as data, preserving ADR-P0-003 and
 * master section 52 compliance (no magic protocol literals in source code).
 */
data class AcmeFixtureData(
    val syntheticCompanyId: Int,
    val batteryOpHeader: Byte,
    val ancOpHeader: Byte,
    val provenance: String,
) {
    companion object {
        private const val RESOURCE = "omnibuds/sdk/acme/acme-fixture.json"

        fun loadDefault(): AcmeFixtureData {
            val text = AcmeFixtureData::class.java.classLoader
                ?.getResourceAsStream(RESOURCE)
                ?.bufferedReader()?.readText()
                ?: return fallback()

            val companyId = extractInt(text, "syntheticCompanyId") ?: 65534
            val batteryHeader = (extractInt(text, "batteryOpHeader") ?: 1).toByte()
            val ancHeader = (extractInt(text, "ancOpHeader") ?: 2).toByte()
            val prov = extractString(text, "provenance") ?: "Synthetic"

            return AcmeFixtureData(
                syntheticCompanyId = companyId,
                batteryOpHeader = batteryHeader,
                ancOpHeader = ancHeader,
                provenance = prov,
            )
        }

        private fun fallback() = AcmeFixtureData(
            syntheticCompanyId = 65534,
            batteryOpHeader = 1.toByte(),
            ancOpHeader = 2.toByte(),
            provenance = "Synthetic fallback",
        )

        private fun extractInt(json: String, key: String): Int? =
            Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)")
                .find(json)?.groupValues?.get(1)?.toIntOrNull()

        private fun extractString(json: String, key: String): String? =
            Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"")
                .find(json)?.groupValues?.get(1)
    }
}

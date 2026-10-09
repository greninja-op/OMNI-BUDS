package com.omnibuds.core.vendor.apple

/**
 * Identity evidence for the AirPods family adapter (Phase 42).
 *
 * The Bluetooth SIG assigned numbers used for family-level matching
 * are evidence-bearing data, so they live in
 * `omnibuds/vendor/apple/airpods-identity.json` as data — not as
 * code literals (ADR-P0-003, master section 52).
 *
 * Loading never throws: a missing or invalid resource yields null,
 * and the adapter then matches nothing (fail closed).
 */
data class AirpodsIdentityEvidence(
    /** Bluetooth SIG company ID for Apple, Inc. */
    val appleCompanyId: Int,
    /** Class-of-device major-class mask (bits 8-12). */
    val codMajorMask: Int,
    /** Masked class-of-device value for the audio/video major class. */
    val codMajorAudioVideo: Int,
    /** Provenance of the assigned numbers. */
    val provenance: String,
) {
    companion object {
        private const val RESOURCE =
            "omnibuds/vendor/apple/airpods-identity.json"

        /**
         * Loads the evidence from the packaged JSON resource.
         * Returns null when the resource is missing or invalid; the
         * adapter treats that as "no identity evidence", matching
         * nothing rather than guessing.
         */
        fun loadDefault(): AirpodsIdentityEvidence? {
            val text = AirpodsIdentityEvidence::class.java.classLoader
                ?.getResourceAsStream(RESOURCE)
                ?.bufferedReader()?.readText()
                ?: return null
            val companyId = extractInt(text, "appleCompanyId") ?: return null
            val mask = extractInt(text, "codMajorMask") ?: return null
            val audioVideo = extractInt(text, "codMajorAudioVideo")
                ?: return null
            val provenance = extractString(text, "provenance") ?: return null
            if (companyId <= 0 || mask <= 0 || audioVideo < 0) return null
            return AirpodsIdentityEvidence(
                appleCompanyId = companyId,
                codMajorMask = mask,
                codMajorAudioVideo = audioVideo,
                provenance = provenance,
            )
        }

        private fun extractInt(json: String, key: String): Int? =
            Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)")
                .find(json)?.groupValues?.get(1)?.toIntOrNull()

        private fun extractString(json: String, key: String): String? =
            Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"")
                .find(json)?.groupValues?.get(1)
    }
}

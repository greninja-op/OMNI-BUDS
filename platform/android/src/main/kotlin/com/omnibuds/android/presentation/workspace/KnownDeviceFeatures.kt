package com.omnibuds.android.presentation.workspace

/**
 * Retail feature knowledge for well-known earbud/headphone models.
 *
 * This table drives UI *visibility only*: a control section is shown for a
 * device when the model is publicly known to have that feature. It never
 * claims a control works — actually driving a feature still requires the
 * brand's own protocol, and the UI says so wherever that is missing.
 *
 * Unknown models return null: the workspace then shows only what has been
 * genuinely observed for the device (standard media, battery, codec),
 * because an unlisted model must not be credited with features it may
 * not have.
 */
object KnownDeviceFeatures {

    data class Features(
        val anc: Boolean = false,
        val transparency: Boolean = false,
        val eq: Boolean = false,
        val spatialAudio: Boolean = false,
        val wearDetection: Boolean = false,
        val multipoint: Boolean = false,
    )

    // The retail table now lives in ProductCatalog (identity + imagery +
    // features in one place); this object keeps the feature-only API the
    // workspace has always used, resolved from the same catalog.
    fun forDeviceName(name: String?): Features? =
        ProductCatalog.resolve(name).features

    /** Whether this workspace control belongs to a known feature of the model. */
    fun has(features: Features, featureId: String): Boolean = when (featureId) {
        "anc_mode" -> features.anc
        "transparency_mode" -> features.transparency
        "eq_preset" -> features.eq
        "spatial_audio" -> features.spatialAudio
        "wear_detection" -> features.wearDetection
        "multipoint" -> features.multipoint
        else -> false
    }
}

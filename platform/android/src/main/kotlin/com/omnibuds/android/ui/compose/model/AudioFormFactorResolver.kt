package com.omnibuds.android.ui.compose.model

import android.content.Context
import android.content.SharedPreferences

/**
 * Universal Hardware Form-Factor Resolver.
 *
 * Inspects device names, BLE appearance, and MAC addresses to deterministically
 * categorize connected audio hardware into one of the 9 [AudioFormFactor] categories.
 *
 * Also provides persistence for user-chosen manual overrides via [SharedPreferences].
 */
object AudioFormFactorResolver {

    private const val PREFS_NAME = "omnibuds_form_factors"
    private const val KEY_PREFIX_FORM_FACTOR = "ff_"
    private const val KEY_PREFIX_COMBINED_VIEW = "combined_"

    // Strict keyword maps ordered from most specific to general
    private val OPEN_BACK_KEYWORDS = listOf("open-back", "open back", "deva", "ananda", "grado", "sundara", "hd 6")
    private val BONE_KEYWORDS = listOf("openrun", "bone", "aeropex", "shokz", "aftershokz", "suunto wing")
    private val OPEN_CLIP_KEYWORDS = listOf("freeclip", "ultra open", "bose open", "clip")
    private val OPEN_HOOK_KEYWORDS = listOf("powerbeats pro", "powerbeats", "aerofit", "soundgear", "openfit", "ear (open)", "nothing ear open")
    private val OPEN_RING_KEYWORDS = listOf("linkbuds", "wf-l900", "buds live")
    private val NECKBAND_KEYWORDS = listOf("wireless", "bullets", "rockerz", "neckband", "wi-", "enco m", "edifier w210bt", "flex")
    private val OVER_EAR_KEYWORDS = listOf("wh-", "airpods max", "quietcomfort", "qc45", "qc ultra", "momentum 4", "accentum", "major", "monitor", "crusher", "studio3", "tour one", "tune 7", "ch720n", "ult wear", "headphones", "cans")
    private val HALF_IN_EAR_KEYWORDS = listOf("airpods 3", "airpods 4", "ear (stick)", "air 3", "air 4", "air3", "air4")
    private val BEAN_KEYWORDS = listOf("galaxy buds", "buds2", "buds fe", "buds+", "wf-1000", "pixel buds", "momentum true", "c500", "w320tn")
    private val STEM_KEYWORDS = listOf("t300", "buds t", "airpods", "buds air", "oneplus buds", "ear (", "nothing ear", "freebuds", "oppo enco", "redmi buds", "tune 130", "wave")

    /**
     * Resolves the [AudioFormFactor] for a device. Checks manual override first,
     * then resolves dynamically via device name and appearance byte.
     */
    fun resolve(
        context: Context?,
        address: String?,
        deviceName: String?,
        appearance: Int? = null,
    ): AudioFormFactor {
        if (context != null && !address.isNullOrBlank()) {
            val overridden = getOverriddenFormFactor(context, address)
            if (overridden != null) return overridden
        }

        val name = (deviceName ?: "").lowercase().trim()

        // 1. Open-Back Audiophile Headphones
        if (OPEN_BACK_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.HEADSET_OPEN_BACK
        }

        // 2. Bone Conduction Headsets
        if (BONE_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.BONE_CONDUCTION
        }

        // 3. Open-Ear Cuffs / Clips
        if (OPEN_CLIP_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.OPEN_EAR_CLIP
        }

        // 4. Open-Ear Hooks
        if (OPEN_HOOK_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.OPEN_EAR_HOOK
        }

        // 5. Open-Ring Donut Buds
        if (OPEN_RING_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.BUD_OPEN_RING
        }

        // 6. Wireless Neckbands
        if (NECKBAND_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.NECKBAND
        }

        // 7. Over-Ear / On-Ear Headsets (Closed-Back)
        if (OVER_EAR_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.HEADSET_OVER_ON_EAR
        }

        // 8. Half In-Ear / Open Stem Buds
        if (HALF_IN_EAR_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.STEM_HALF_IN_EAR
        }

        // 9. Stemless Bean / Pill Buds
        if (BEAN_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.BUD_BEAN_IN_EAR
        }

        // 10. Stem In-Ear (realme Buds T300, AirPods Pro, OnePlus Buds Pro)
        if (STEM_KEYWORDS.any { name.contains(it) }) {
            return AudioFormFactor.STEM_IN_EAR
        }

        // Fallback based on BLE Appearance (0x2A01) if available
        // Generic Headset: 0x0941, Generic Headphones: 0x0942
        if (appearance == 0x0942) return AudioFormFactor.HEADSET_OVER_ON_EAR

        // Default universal fallback for modern audio gear is STEM_IN_EAR
        return AudioFormFactor.STEM_IN_EAR
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getOverriddenFormFactor(context: Context, address: String): AudioFormFactor? {
        val stored = getPrefs(context).getString(KEY_PREFIX_FORM_FACTOR + address.uppercase(), null) ?: return null
        return try {
            AudioFormFactor.valueOf(stored)
        } catch (_: Exception) {
            null
        }
    }

    fun setOverriddenFormFactor(context: Context, address: String, factor: AudioFormFactor) {
        getPrefs(context).edit().putString(KEY_PREFIX_FORM_FACTOR + address.uppercase(), factor.name).apply()
    }

    fun clearOverriddenFormFactor(context: Context, address: String) {
        getPrefs(context).edit().remove(KEY_PREFIX_FORM_FACTOR + address.uppercase()).apply()
    }

    fun getCombinedViewPreference(context: Context, address: String): Boolean {
        return getPrefs(context).getBoolean(KEY_PREFIX_COMBINED_VIEW + address.uppercase(), false)
    }

    fun setCombinedViewPreference(context: Context, address: String, combined: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PREFIX_COMBINED_VIEW + address.uppercase(), combined).apply()
    }
}


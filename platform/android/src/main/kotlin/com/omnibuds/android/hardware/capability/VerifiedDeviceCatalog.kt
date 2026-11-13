package com.omnibuds.android.hardware.capability

private val VerifiedDriverBrands = setOf(
    "CMF by Nothing",
    "Nothing",
    "OnePlus",
    "realme",
    "OPPO",
    "Samsung",
    "Xiaomi",
    "Apple",
)

private object VerifiedFeatureMap {
    private val mapping = mapOf(
        "noise-control.anc" to "anc_mode",
        "noise-control.adaptive" to "anc_mode",
        "noise-control.transparency" to "transparency_mode",
        "noise-control.ambient" to "transparency_mode",
        "equalization.equalizer" to "eq_preset",
        "equalization.custom-eq" to "eq_preset",
        "equalization.presets" to "eq_preset",
        "connectivity.multipoint" to "multipoint",
        "sensing.wear-detection" to "wear_detection",
        "spatial.audio" to "spatial_audio",
        "spatial.head-tracking" to "spatial_audio",
        "audio.spatial" to "spatial_audio",
        "audio.spatial-audio" to "spatial_audio",
        "audio.immersive" to "spatial_audio",
        "audio.360-reality-audio" to "spatial_audio",
        "gaming.low-latency" to "game_mode",
        "low-latency" to "game_mode",
        "vendor.samsung.gaming-mode" to "game_mode",
        "vendor.google.low-latency-gaming" to "game_mode",
        "vendor.realme.volume-enhancer" to "volume_enhancer",
        "equalization.volume-eq" to "volume_enhancer",
        "vendor.cmf.ultra-bass" to "ultra_bass",
        "ultra-bass" to "ultra_bass",
        "gesture-customization" to "touch_controls",
        "touch-controls" to "touch_controls",
    )

    fun map(featureIds: Set<String>): Set<String> =
        featureIds.mapNotNull { mapping[it] }.toSet()
}

/**
 * A verified model profile derived from the research dataset.
 *
 * This is intentionally smaller than [StrictBrandProfile]: it carries the
 * facts that affect presentation, especially the official app's battery
 * contract. Protocol drivers and capabilities remain in their existing layers.
 */
data class VerifiedDeviceProfile(
    val slug: String,
    val manufacturer: String,
    val model: String,
    val officialAppName: String,
    val aliases: List<String>,
    val batteryContract: BatteryDisplayContract,
    /** Official-app feature ids from the research record. */
    val featureIds: Set<String> = emptySet(),
) {
    /**
     * Capability ids the official app exposes for this model, mapped to the
     * workspace control ids. Empty means "not verified"; callers fall back to
     * the legacy profile.
     */
    val capabilityIds: Set<String>
        get() {
            val mapped = VerifiedFeatureMap.map(featureIds)
            if (mapped.isNotEmpty()) return mapped
            return when (slug) {
            "cmf-buds" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "multipoint",
                "wear_detection", "ultra_bass", "touch_controls",
            )
            "realme-buds-t300" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection", "volume_enhancer", "game_mode",
                "touch_controls",
            )
            "galaxy-buds3-pro" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "wear_detection", "game_mode", "touch_controls",
            )
            "sony-wf-1000xm5" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection",
            )
            "bose-quietcomfort-ultra-earbuds" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection",
            )
            "airpods-pro-2-usb-c" -> setOf(
                "anc_mode", "transparency_mode", "spatial_audio", "wear_detection",
            )
            "jbl-live-beam-3" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection", "touch_controls",
            )
            "sennheiser-momentum-true-wireless-4" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "multipoint",
                "wear_detection", "touch_controls",
            )
            "nothing-ear-a" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "multipoint",
                "wear_detection", "touch_controls",
            )
            "oneplus-buds-pro-3" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection", "game_mode", "touch_controls",
            )
            "xiaomi-buds-5-pro" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint",
            )
            "pixel-buds-pro-2" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection", "game_mode", "touch_controls",
            )
            "galaxy-buds3" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "wear_detection", "game_mode", "touch_controls",
            )
            "airpods-4" -> setOf(
                "transparency_mode", "spatial_audio", "wear_detection",
            )
            "airpods-4-anc" -> setOf(
                "anc_mode", "transparency_mode", "spatial_audio", "wear_detection",
            )
            "sony-wh-1000xm5" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection",
            )
            "bose-quietcomfort-ultra-headphones" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection",
            )
            "jbl-tour-pro-3" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "multipoint", "wear_detection", "touch_controls",
            )
            "sennheiser-accentum-plus-wireless" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "multipoint",
                "wear_detection",
            )
            "soundcore-liberty-5-pro" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset", "spatial_audio",
                "wear_detection",
            )
            "edifier-neobuds-pro-2" -> setOf(
                "anc_mode", "transparency_mode", "eq_preset",
            )
            else -> emptySet()
            }
        }

    /**
     * Features OmniBuds can actually drive with a verified control path.
     * Official-app-only features for brands without a driver stay out of the
     * actionable capability set so the UI never shows fake controls.
     */
    val writableCapabilityIds: Set<String>
        get() = if (manufacturer in VerifiedDriverBrands) {
            VerifiedControlCatalog.actionable(capabilityIds)
        } else {
            emptySet()
        }
}

/**
 * Pilot verified catalog (12 mainstream models).
 *
 * Matching is exact after normalisation so "CMF Buds" never captures
 * "CMF Buds Pro". Unknown names return null; the caller keeps its existing
 * generic behaviour.
 */
object VerifiedDeviceCatalog {

    private val pilotProfiles = listOf(
        VerifiedDeviceProfile(
            slug = "cmf-buds",
            manufacturer = "CMF by Nothing",
            model = "CMF Buds",
            officialAppName = "Nothing X",
            aliases = listOf("cmf buds"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_CASE_OPEN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.HIDDEN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "realme-buds-t300",
            manufacturer = "realme",
            model = "realme Buds T300",
            officialAppName = "realme Link",
            aliases = listOf("realme buds t300", "buds t300"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_DOCKED,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.HIDDEN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "galaxy-buds3-pro",
            manufacturer = "Samsung",
            model = "Galaxy Buds3 Pro",
            officialAppName = "Galaxy Wearable",
            aliases = listOf("galaxy buds3 pro", "galaxy buds 3 pro", "buds3 pro"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ALWAYS,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "sony-wf-1000xm5",
            manufacturer = "Sony",
            model = "WF-1000XM5",
            officialAppName = "Sony Sound Connect",
            aliases = listOf("wf 1000xm5", "wf1000xm5", "sony wf 1000xm5"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ALWAYS,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "bose-quietcomfort-ultra-earbuds",
            manufacturer = "Bose",
            model = "QuietComfort Ultra Earbuds",
            officialAppName = "Bose app",
            aliases = listOf("quietcomfort ultra earbuds", "qc ultra earbuds", "bose qc ultra earbuds"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.COMBINED,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ALWAYS,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "airpods-pro-2-usb-c",
            manufacturer = "Apple",
            model = "AirPods Pro 2 (USB-C)",
            officialAppName = "Apple Settings",
            aliases = listOf(
                "airpods pro 2",
                "airpods pro 2nd generation",
                "airpods pro 2 usb c",
                "airpods pro 2 lightning",
            ),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.COMBINED,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_CASE_OPEN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "jbl-live-beam-3",
            manufacturer = "JBL",
            model = "Live Beam 3",
            officialAppName = "JBL Headphones",
            aliases = listOf("jbl live beam 3", "live beam 3"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_DOCKED,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "sennheiser-momentum-true-wireless-4",
            manufacturer = "Sennheiser",
            model = "MOMENTUM True Wireless 4",
            officialAppName = "Sennheiser Smart Control",
            aliases = listOf("momentum true wireless 4", "momentum tws 4", "mtw4"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.COMBINED,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ALWAYS,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "nothing-ear-a",
            manufacturer = "Nothing",
            model = "Nothing Ear (a)",
            officialAppName = "Nothing X",
            aliases = listOf("nothing ear a", "nothing ear (a)"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_CASE_OPEN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.HIDDEN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "oneplus-buds-pro-3",
            manufacturer = "OnePlus",
            model = "OnePlus Buds Pro 3",
            officialAppName = "HeyMelody",
            aliases = listOf("oneplus buds pro 3", "buds pro 3"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_DOCKED,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "xiaomi-buds-5-pro",
            manufacturer = "Xiaomi",
            model = "Xiaomi Buds 5 Pro",
            officialAppName = "Xiaomi Earbuds",
            aliases = listOf("xiaomi buds 5 pro", "buds 5 pro"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_DOCKED,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "pixel-buds-pro-2",
            manufacturer = "Google",
            model = "Pixel Buds Pro 2",
            officialAppName = "Google Pixel Buds",
            aliases = listOf("pixel buds pro 2"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ALWAYS,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "galaxy-buds3",
            manufacturer = "Samsung",
            model = "Galaxy Buds3",
            officialAppName = "Galaxy Wearable",
            aliases = listOf("galaxy buds3", "galaxy buds 3", "buds3"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ALWAYS,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "airpods-4",
            manufacturer = "Apple",
            model = "AirPods 4",
            officialAppName = "Apple Settings",
            aliases = listOf("airpods 4"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.COMBINED,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_CASE_OPEN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "airpods-4-anc",
            manufacturer = "Apple",
            model = "AirPods 4 with Active Noise Cancellation",
            officialAppName = "Apple Settings",
            aliases = listOf(
                "airpods 4 anc",
                "airpods 4 with anc",
                "airpods 4 with active noise cancellation",
            ),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.COMBINED,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_CASE_OPEN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "sony-wh-1000xm5",
            manufacturer = "Sony",
            model = "WH-1000XM5",
            officialAppName = "Sony Sound Connect",
            aliases = listOf("wh 1000xm5", "wh1000xm5", "sony wh 1000xm5"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.COMBINED,
                caseDisplay = BatteryCaseDisplay.HIDDEN,
                caseVisibility = BatteryCaseVisibility.UNKNOWN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.HIDDEN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "bose-quietcomfort-ultra-headphones",
            manufacturer = "Bose",
            model = "QuietComfort Ultra Headphones",
            officialAppName = "Bose app",
            aliases = listOf("quietcomfort ultra headphones", "qc ultra headphones"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.COMBINED,
                caseDisplay = BatteryCaseDisplay.HIDDEN,
                caseVisibility = BatteryCaseVisibility.UNKNOWN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.HIDDEN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "jbl-tour-pro-3",
            manufacturer = "JBL",
            model = "Tour Pro 3",
            officialAppName = "JBL Headphones",
            aliases = listOf("jbl tour pro 3", "tour pro 3"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_DOCKED,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "sennheiser-accentum-plus-wireless",
            manufacturer = "Sennheiser",
            model = "ACCENTUM Plus Wireless",
            officialAppName = "Sennheiser Smart Control Plus",
            aliases = listOf("accentum plus wireless", "accentum plus"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.COMBINED,
                caseDisplay = BatteryCaseDisplay.HIDDEN,
                caseVisibility = BatteryCaseVisibility.UNKNOWN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.HIDDEN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "soundcore-liberty-5-pro",
            manufacturer = "Anker Soundcore",
            model = "soundcore Liberty 5 Pro",
            officialAppName = "soundcore",
            aliases = listOf("soundcore liberty 5 pro", "liberty 5 pro"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ALWAYS,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
        VerifiedDeviceProfile(
            slug = "edifier-neobuds-pro-2",
            manufacturer = "Edifier",
            model = "Edifier NeoBuds Pro 2",
            officialAppName = "Edifier Connect",
            aliases = listOf("edifier neobuds pro 2", "neobuds pro 2"),
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ALWAYS,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.LAST_KNOWN,
            ),
        ),
    )

    private val profiles: List<VerifiedDeviceProfile> =
        pilotProfiles + GeneratedVerifiedDevices.profiles

    /** Exact, normalised match against any supplied name/alias. */
    fun resolve(vararg candidates: String?): VerifiedDeviceProfile? {
        val names = candidates
            .filterNotNull()
            .map(::normalize)
            .filter { it.isNotEmpty() }
        if (names.isEmpty()) return null
        return profiles.firstOrNull { profile ->
            profile.aliases.any { alias -> names.contains(normalize(alias)) }
        }
    }

    fun all(): List<VerifiedDeviceProfile> = profiles

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}

package com.omnibuds.android.presentation.workspace

/**
 * Product identity catalog — Iteration 2.
 *
 * Resolves an advertised Bluetooth device name to a product identity:
 * manufacturer, model, category, VERIFIED feature knowledge, and —
 * only where a genuinely licensed photograph exists — a bundled photo
 * with full provenance metadata.
 *
 * Image policy (corrected in Iteration 2):
 *  - No AI-generated or synthetic product imagery anywhere.
 *  - A bundled photograph exists only when its license permits
 *    redistribution; source, license and attribution are recorded on
 *    the entry and surfaced in the app (About + hero caption).
 *  - Products without a licensed photo get NO image: the UI renders a
 *    neutral vector category artwork, never a substitute photograph.
 *  - A user-supplied photo of their own device (stored per device)
 *    takes precedence over catalog imagery at render time.
 *
 * Matching (corrected in Iteration 2): token-based, specificity-scored.
 * Exact entries require brand-qualified keys, and a variant guard
 * rejects an exact match when the name carries a variant token the key
 * does not (e.g. "CMF Buds Pro" is not "CMF Buds"). Where the variant
 * or generation cannot be distinguished from the advertised name, the
 * entry is family-level and confidence says so.
 *
 * Feature knowledge here drives section VISIBILITY only; it never
 * claims a control works.
 */
object ProductCatalog {

    enum class Category(val label: String) {
        TWS("True wireless earbuds"),
        OVER_EAR("Over-ear headphones"),
        NECKBAND("Neckband earphones"),
        UNKNOWN("Audio device"),
    }

    enum class Confidence { OVERRIDE, EXACT, FAMILY, CATEGORY_FALLBACK }

    data class ProductInfo(
        val manufacturer: String?,
        val model: String?,
        val category: Category,
        /** Bundled licensed photograph asset name, or null for category artwork. */
        val imageAsset: String?,
        /** Attribution line for [imageAsset], shown beside the image and in About. */
        val imageAttribution: String?,
        val features: KnownDeviceFeatures.Features?,
        val confidence: Confidence,
        val catalogId: String?,
    )

    data class Entry(
        val id: String,
        val matchKeys: List<String>,
        val manufacturer: String,
        val model: String,
        val category: Category,
        val imageAsset: String? = null,
        val imageSource: String? = null,
        val imageLicense: String? = null,
        val imageAttribution: String? = null,
        val features: KnownDeviceFeatures.Features? = null,
        /** True only when the advertised name can pin this exact model. */
        val exact: Boolean = false,
    )

    private fun features(
        anc: Boolean = false,
        transparency: Boolean = false,
        eq: Boolean = false,
        spatial: Boolean = false,
    ) = KnownDeviceFeatures.Features(
        anc = anc, transparency = transparency, eq = eq, spatialAudio = spatial,
        wearDetection = false, multipoint = false,
    )

    private val entries: List<Entry> = listOf(
        // -- Exact models (brand-qualified keys) -------------------------
        Entry(
            id = "realme_buds_t300",
            matchKeys = listOf("realme buds t300", "realme t300"),
            manufacturer = "realme", model = "Buds T300",
            category = Category.TWS, exact = true,
            imageAsset = "product_realme_buds_t300",
            imageAttribution = "realme Buds T300",
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "realme_buds_air_5_pro",
            matchKeys = listOf("realme buds air 5 pro", "buds air 5 pro", "realme air 5 pro"),
            manufacturer = "realme", model = "Buds Air 5 Pro",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "realme_buds_air_6_pro",
            matchKeys = listOf("realme buds air 6 pro", "buds air 6 pro", "realme air 6 pro"),
            manufacturer = "realme", model = "Buds Air 6 Pro",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "realme_buds_air_7_pro",
            matchKeys = listOf("realme buds air 7 pro", "buds air 7 pro", "realme air 7 pro"),
            manufacturer = "realme", model = "Buds Air 7 Pro",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "oneplus_buds_pro_3",
            matchKeys = listOf("oneplus buds pro 3", "buds pro 3"),
            manufacturer = "OnePlus", model = "Buds Pro 3",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "oneplus_buds_3",
            matchKeys = listOf("oneplus buds 3", "buds 3"),
            manufacturer = "OnePlus", model = "Buds 3",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "airpods_pro_2",
            matchKeys = listOf("airpods pro 2", "airpods pro (2nd", "airpods pro 2nd gen"),
            manufacturer = "Apple", model = "AirPods Pro 2",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "sony_wf_1000xm5",
            matchKeys = listOf("wf-1000xm5", "sony wf-1000xm5"),
            manufacturer = "Sony", model = "WF-1000XM5",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "galaxy_buds3_pro",
            matchKeys = listOf("galaxy buds3 pro", "buds3 pro", "galaxy buds 3 pro"),
            manufacturer = "Samsung", model = "Galaxy Buds3 Pro",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "cmf_buds_pro_2",
            matchKeys = listOf("cmf buds pro 2", "buds pro 2"),
            manufacturer = "CMF by Nothing", model = "Buds Pro 2",
            category = Category.TWS, exact = true,
            features = features(anc = true, transparency = true, eq = true, spatial = true),
        ),
        Entry(
            id = "jbl_tune_130nc",
            matchKeys = listOf("jbl tune 130nc", "tune130nc", "tune 130nc"),
            manufacturer = "JBL", model = "Tune 130NC TWS",
            category = Category.TWS, exact = true,
            imageAsset = "product_jbl_tune_130nc",
            imageAttribution = "JBL Tune 130NC TWS",
            features = features(anc = true, transparency = true, eq = true),
        ),
        Entry(
            id = "cmf_buds",
            matchKeys = listOf("cmf buds", "cmf by nothing buds"),
            manufacturer = "CMF by Nothing", model = "Buds",
            category = Category.TWS, exact = true,
            imageAsset = "product_cmf_buds",
            imageAttribution = "CMF by Nothing Buds",
            features = features(anc = true, transparency = true, eq = true),
        ),
        Entry(
            id = "redmi_buds_5a",
            matchKeys = listOf("redmi buds 5a", "buds 5a"),
            manufacturer = "Redmi", model = "Buds 5A",
            category = Category.TWS, exact = true,
            imageAsset = "product_redmi_buds_5a",
            imageAttribution = "Redmi Buds 5A",
            features = features(),
        ),
        Entry(
            id = "sony_wi_c100",
            matchKeys = listOf("wi-c100", "wi c100"),
            manufacturer = "Sony", model = "WI-C100",
            category = Category.NECKBAND, exact = true,
            imageAsset = "product_sony_wi_c100",
            imageSource = "Wikimedia Commons: File:Sony in-ear headphone WI-C100.jpg",
            imageLicense = "CC BY-SA 4.0",
            imageAttribution = "Photo: KKPCW (Kyu3), CC BY-SA 4.0, via Wikimedia Commons",
            features = features(eq = true),
        ),
        Entry(
            id = "boat_rockerz_450",
            matchKeys = listOf("boat rockerz 450", "rockerz 450"),
            manufacturer = "boAt", model = "Rockerz 450",
            category = Category.OVER_EAR, exact = true,
            imageAsset = "product_boat_rockerz_450",
            imageAttribution = "boAt Rockerz 450",
            features = features(),
        ),
        Entry(
            id = "skullcandy_hesh_anc",
            matchKeys = listOf("skullcandy hesh anc", "hesh anc"),
            manufacturer = "Skullcandy", model = "Hesh ANC",
            category = Category.OVER_EAR, exact = true,
            features = features(anc = true, transparency = true),
        ),
        Entry(
            id = "airpods_max",
            matchKeys = listOf("airpods max"),
            manufacturer = "Apple", model = "AirPods Max",
            category = Category.OVER_EAR, exact = true,
            features = features(anc = true, transparency = true),
        ),
        // -- Family lines (name cannot pin the generation/variant) -------
        Entry(
            id = "airpods_pro",
            matchKeys = listOf("airpods pro"),
            manufacturer = "Apple", model = "AirPods Pro",
            category = Category.TWS,
            features = features(anc = true, transparency = true),
        ),
        Entry(
            id = "airpods",
            matchKeys = listOf("airpods"),
            manufacturer = "Apple", model = "AirPods",
            category = Category.TWS,
            features = features(),
        ),
        Entry(
            id = "pixel_buds_pro",
            matchKeys = listOf("pixel buds pro"),
            manufacturer = "Google", model = "Pixel Buds Pro",
            category = Category.TWS,
            features = features(anc = true, transparency = true),
        ),
        Entry(
            id = "pixel_buds",
            matchKeys = listOf("pixel buds"),
            manufacturer = "Google", model = "Pixel Buds",
            category = Category.TWS,
            features = null,
        ),
        Entry(
            id = "cmf",
            matchKeys = listOf("cmf buds", "cmf"),
            manufacturer = "CMF by Nothing", model = "Buds (model variant not identified)",
            category = Category.TWS,
            features = null,
        ),
        Entry(
            id = "galaxy_buds",
            matchKeys = listOf("galaxy buds"),
            manufacturer = "Samsung", model = "Galaxy Buds",
            category = Category.TWS,
            features = features(anc = true, transparency = true),
        ),
        Entry(
            id = "sony_wf",
            matchKeys = listOf("wf-1000", "wf-c", "linkbuds"),
            manufacturer = "Sony", model = "WF / LinkBuds series",
            category = Category.TWS,
            features = features(anc = true, transparency = true, eq = true),
        ),
        Entry(
            id = "sony_wh",
            matchKeys = listOf("wh-1000", "wh-ch"),
            manufacturer = "Sony", model = "WH series",
            category = Category.OVER_EAR,
            features = features(anc = true, transparency = true, eq = true),
        ),
        Entry(
            id = "bose_quietcomfort",
            matchKeys = listOf("quietcomfort", "bose qc"),
            manufacturer = "Bose", model = "QuietComfort",
            category = Category.OVER_EAR,
            features = features(anc = true, transparency = true),
        ),
        Entry(
            id = "nothing_ear",
            matchKeys = listOf("nothing ear"),
            manufacturer = "Nothing", model = "Ear",
            category = Category.TWS,
            features = features(anc = true, transparency = true, eq = true),
        ),
        Entry(
            id = "oneplus_buds",
            matchKeys = listOf("oneplus buds"),
            manufacturer = "OnePlus", model = "Buds",
            category = Category.TWS,
            features = features(anc = true, transparency = true),
        ),
        Entry(
            id = "soundcore",
            matchKeys = listOf("soundcore"),
            manufacturer = "Anker", model = "Soundcore",
            category = Category.TWS,
            features = features(anc = true, transparency = true),
        ),
        Entry(
            id = "oppo_enco",
            matchKeys = listOf("enco"),
            manufacturer = "Oppo", model = "Enco",
            category = Category.TWS,
            features = null,
        ),
        Entry(
            id = "boat_airdopes",
            matchKeys = listOf("airdopes"),
            manufacturer = "boAt", model = "Airdopes",
            category = Category.TWS,
            features = null,
        ),
        Entry(
            id = "sennheiser_momentum_tw",
            matchKeys = listOf("momentum true wireless"),
            manufacturer = "Sennheiser", model = "Momentum True Wireless",
            category = Category.TWS,
            features = features(anc = true, transparency = true, eq = true),
        ),
        Entry(
            id = "sennheiser_momentum",
            matchKeys = listOf("momentum"),
            manufacturer = "Sennheiser", model = "Momentum",
            category = Category.OVER_EAR,
            features = features(anc = true, transparency = true, eq = true),
        ),
        Entry(
            id = "realme_buds",
            matchKeys = listOf("realme buds"),
            manufacturer = "realme", model = "Buds",
            category = Category.TWS,
            features = features(anc = true, eq = true),
        ),
        Entry(
            id = "redmi_buds",
            matchKeys = listOf("redmi buds"),
            manufacturer = "Redmi", model = "Buds",
            category = Category.TWS,
            features = features(anc = true),
        ),
    )

    /** Stored override value meaning "the user says: none of the catalog". */
    const val GENERIC_OVERRIDE_ID = "__generic__"

    fun byId(id: String?): Entry? = entries.firstOrNull { it.id == id }

    /** Every entry that carries a bundled licensed photograph (About credits). */
    fun licensedImages(): List<Entry> = entries.filter { it.imageAsset != null }

    fun resolve(deviceName: String?, overrideCatalogId: String? = null): ProductInfo {
        if (overrideCatalogId == GENERIC_OVERRIDE_ID) {
            return fallback(deviceName, Confidence.OVERRIDE)
        }
        byId(overrideCatalogId)?.let { entry ->
            return entry.toInfo(Confidence.OVERRIDE)
        }
        val name = deviceName?.lowercase()?.trim().orEmpty()
        if (name.isEmpty()) return fallback(deviceName, Confidence.CATEGORY_FALLBACK)

        val tokens = tokenize(name)
        val compact = tokens.joinToString("")

        // Exact pass, variant-guarded; then family pass. Most specific
        // (longest matched key) wins; list order breaks ties.
        for (exactPass in listOf(true, false)) {
            var best: Entry? = null
            var bestScore = -1
            for (entry in entries) {
                if (entry.exact != exactPass) continue
                val score = matchScore(entry, tokens, compact) ?: continue
                if (exactPass && hasUnmatchedVariant(entry, tokens)) continue
                if (score > bestScore) {
                    best = entry
                    bestScore = score
                }
            }
            if (best != null) {
                return best.toInfo(if (exactPass) Confidence.EXACT else Confidence.FAMILY)
            }
        }
        return fallback(deviceName, Confidence.CATEGORY_FALLBACK)
    }

    /** Catalog entries for the correction picker, same category first. */
    fun candidatesFor(category: Category): List<Entry> =
        entries.sortedByDescending { it.category == category }

    fun allEntries(): List<Entry> = entries

    // -- matching internals ---------------------------------------------------

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private val genericTokens = setOf(
        "tws", "true", "wireless", "earbuds", "earbud", "headphones",
        "headphone", "bluetooth", "anc", "the", "by",
    )

    private val variantTokens = setOf(
        "pro", "max", "ultra", "plus", "lite", "neo", "air", "mini",
        "2", "3", "4", "5", "6",
    )

    /**
     * Specificity score of the best matching key, or null for no match.
     * A key matches when its tokens form a contiguous run in the name
     * tokens, or — for model-code keys such as "wi-c100"/"tune130nc" —
     * when its compact form appears in the compacted name.
     */
    private fun matchScore(entry: Entry, tokens: List<String>, compact: String): Int? {
        var best: Int? = null
        for (key in entry.matchKeys) {
            val keyTokens = tokenize(key)
            if (keyTokens.isEmpty()) continue
            val phraseHit = containsRun(tokens, keyTokens)
            val codeHit = keyTokens.joinToString("") in compact
            if (phraseHit || codeHit) {
                val score = keyTokens.size * 10 + keyTokens.sumOf { it.length }
                if (best == null || score > best) best = score
            }
        }
        return best
    }

    private fun containsRun(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.size > haystack.size) return false
        for (i in 0..haystack.size - needle.size) {
            if (haystack.subList(i, i + needle.size) == needle) return true
        }
        return false
    }

    /**
     * True when the name carries a variant token that no key of the
     * exact entry accounts for — the name is a different variant
     * ("CMF Buds Pro" vs the plain "CMF Buds" entry).
     */
    private fun hasUnmatchedVariant(entry: Entry, tokens: List<String>): Boolean {
        val accounted = entry.matchKeys.flatMap { tokenize(it) }.toSet() + genericTokens
        return tokens.any { it in variantTokens && it !in accounted }
    }

    private fun Entry.toInfo(confidence: Confidence) = ProductInfo(
        manufacturer = manufacturer,
        model = model,
        category = category,
        imageAsset = imageAsset,
        imageAttribution = imageAttribution,
        features = features,
        confidence = confidence,
        catalogId = id,
    )

    private fun fallback(deviceName: String?, confidence: Confidence): ProductInfo {
        val category = inferCategory(deviceName?.lowercase()?.trim().orEmpty())
        return ProductInfo(
            manufacturer = null,
            model = null,
            category = category,
            imageAsset = null,
            imageAttribution = null,
            features = null,
            confidence = confidence,
            catalogId = null,
        )
    }

    private fun inferCategory(normalized: String): Category {
        if (normalized.isEmpty()) return Category.UNKNOWN
        val tokens = tokenize(normalized)
        val neckbandHints = listOf("neckband", "bullets wireless", "wi-c", "wi c")
        val overEarHints = listOf(
            "headphone", "headset", "rockerz", "hesh", "quietcomfort",
            "momentum", "airpods max", "over-ear", "over ear",
        )
        val twsHints = listOf(
            "buds", "pods", "tws", "earbud", "airdopes", "soundcore",
            "enco", "freebuds",
        )
        fun hit(hints: List<String>) = hints.any { hint ->
            containsRun(tokens, tokenize(hint)) || tokenize(hint).joinToString("") in tokens.joinToString("")
        }
        return when {
            hit(neckbandHints) -> Category.NECKBAND
            hit(overEarHints) -> Category.OVER_EAR
            hit(twsHints) -> Category.TWS
            else -> Category.UNKNOWN
        }
    }
}

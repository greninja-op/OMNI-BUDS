package com.omnibuds.android.hardware.capability

/**
 * The control ids the app currently has a real dispatch path for.
 *
 * Research records can list many official-app features; only ids present here
 * may become actionable. This is the guardrail against showing a toggle that
 * has no handler behind it.
 */
object VerifiedControlCatalog {

    val supportedIds: Set<String> = setOf(
        "anc_mode",
        "transparency_mode",
        "eq_preset",
        "spatial_audio",
        "game_mode",
        "volume_enhancer",
        "wear_detection",
        "multipoint",
        "ultra_bass",
        "touch_controls",
    )

    fun actionable(ids: Set<String>): Set<String> = ids.intersect(supportedIds)
}

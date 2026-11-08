package com.omnibuds.android.ui.compose.model

/**
 * Universal Audio Device Hardware Form Factors.
 *
 * Defines the physical industrial design category of the connected audio device,
 * driving both the vector iconography ([com.omnibuds.android.ui.compose.components.AdaptiveAudioGlyph])
 * and the dashboard capability cards.
 */
enum class AudioFormFactor(
    val displayName: String,
    val hasCase: Boolean,
    val isBinauralSplit: Boolean,
    val supportsAnc: Boolean,
) {
    /**
     * Stem earbud with silicone ear tip (e.g. realme Buds T300, AirPods Pro, OnePlus Buds Pro, Nothing Ear).
     * Rendered with a 42° tilted ergonomic housing, silicone nozzle, dark vent slit, and vertical stem.
     * Accompanied by a wide rounded-rectangle pebble case.
     */
    STEM_IN_EAR(
        displayName = "Stem (In-Ear)",
        hasCase = true,
        isBinauralSplit = true,
        supportsAnc = true,
    ),

    /**
     * Stem earbud without rubber tip / open acoustic nozzle (e.g. AirPods 3/4, Nothing Ear stick).
     * Rendered with a smooth teardrop acoustic head with dark speaker grille and tapered stem.
     */
    STEM_HALF_IN_EAR(
        displayName = "Stem (Open / Half In-Ear)",
        hasCase = true,
        isBinauralSplit = true,
        supportsAnc = true,
    ),

    /**
     * Stemless bean / pill in-ear bud (e.g. Samsung Galaxy Buds, Sony WF-1000XM5, Google Pixel Buds).
     * Rendered with a sculpted kidney/pill shell, touchpad contour, and inner silicone tip.
     * Accompanied by a square rounded clamshell case.
     */
    BUD_BEAN_IN_EAR(
        displayName = "Bud (Bean / Pill)",
        hasCase = true,
        isBinauralSplit = true,
        supportsAnc = true,
    ),

    /**
     * Open-ring donut acoustic earbud (e.g. Sony LinkBuds WF-L900, Galaxy Buds Live).
     * Rendered with a figure-8 / open-ring loop and upper bean body.
     */
    BUD_OPEN_RING(
        displayName = "Open-Ring Donut",
        hasCase = true,
        isBinauralSplit = true,
        supportsAnc = false,
    ),

    /**
     * Over-ear hook hanger sports earphone (e.g. Powerbeats Pro, Nothing Ear Open, JBL Soundgear Sense).
     * Rendered with a sweeping C-curve flexible ear-hook and front speaker module.
     */
    OPEN_EAR_HOOK(
        displayName = "Open-Ear Hook",
        hasCase = true,
        isBinauralSplit = true,
        supportsAnc = false,
    ),

    /**
     * Cuff / ear-clip open earphone (e.g. Bose Ultra Open, Huawei FreeClip).
     * Rendered with an acoustic sphere ball connected via a curved bridge to a rear battery barrel.
     */
    OPEN_EAR_CLIP(
        displayName = "Open-Ear Clip",
        hasCase = true,
        isBinauralSplit = true,
        supportsAnc = false,
    ),

    /**
     * Symmetrical collar neckband with magnetic wired buds (e.g. OnePlus Bullets Wireless Z2, realme Buds Wireless 3).
     * Rendered with a U-shaped flexible collar band, weighted control modules, and thin wires.
     * Single-unit battery telemetry; no charging case.
     */
    NECKBAND(
        displayName = "Wireless Neckband",
        hasCase = false,
        isBinauralSplit = false,
        supportsAnc = true,
    ),

    /**
     * Temple wrap-around bone conduction headset (e.g. Shokz OpenRun Pro, Suunto Wing).
     * Rendered with a horizontal wrap-around headband and dual temporal bone transducer pads.
     * Single-unit battery telemetry; no charging case.
     */
    BONE_CONDUCTION(
        displayName = "Bone Conduction",
        hasCase = false,
        isBinauralSplit = false,
        supportsAnc = false,
    ),

    /**
     * Over-ear / on-ear closed-back headphones (e.g. Sony WH-1000XM5, AirPods Max, Bose QuietComfort Ultra).
     * Rendered with a padded headband arch, telescoping yokes, and plush sealed earcups.
     * Single-unit battery telemetry; no charging case.
     */
    HEADSET_OVER_ON_EAR(
        displayName = "Over-Ear Headset (Closed-Back)",
        hasCase = false,
        isBinauralSplit = false,
        supportsAnc = true,
    ),

    /**
     * Over-ear open-back audiophile headphones (e.g. HiFiMAN Deva Pro, Grado GW100x).
     * Rendered with padded headband and earcups featuring precision diagonal acoustic grille louvers.
     * Single-unit battery telemetry; no charging case; ANC disabled by acoustic physics.
     */
    HEADSET_OPEN_BACK(
        displayName = "Over-Ear Headset (Open-Back)",
        hasCase = false,
        isBinauralSplit = false,
        supportsAnc = false,
    );

    val isOpenAcoustic: Boolean
        get() = this == BUD_OPEN_RING ||
            this == OPEN_EAR_HOOK ||
            this == OPEN_EAR_CLIP ||
            this == BONE_CONDUCTION ||
            this == HEADSET_OPEN_BACK
}


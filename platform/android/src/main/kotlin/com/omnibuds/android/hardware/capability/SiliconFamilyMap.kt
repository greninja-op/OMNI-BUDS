package com.omnibuds.android.hardware.capability

import com.omnibuds.android.ui.compose.model.AudioFormFactor

/** The 12 canonical silicon SDK / protocol families. */
enum class SiliconFamily(val displayName: String) {
    OPO_HEYMELODY("OPOv1 / BES HeyMelody"),
    NOTHING_X("Nothing X 0x5560"),
    AIROHA_RACE("Airoha / MediaTek RACE"),
    SONY_TANDEM("Sony Tandem SPP/BLE"),
    SAMSUNG_SEAMLESS("Samsung Seamless SPP"),
    APPLE_L2CAP("Apple AACP L2CAP"),
    ANKER_SOUNDCORE("Soundcore 0x08EE"),
    QUALCOMM_GAIA("Qualcomm GAIA v3"),
    HUAWEI_HONOR("Huawei / Honor L2HC"),
    OPEN_EAR("Open-Ear / Bone Conduction"),
    JIELI_BLUETRUM("Jieli / Bluetrum RCSP"),
    GOOGLE_FAST_PAIR("Google Fast Pair Maestro"),
}

/** Physical input mechanism rendered by the controls customizer. */
enum class PhysicalControlSchema(val displayName: String) {
    CAPACITIVE_TOUCH_PAD("Capacitive touch pad"),
    FORCE_STEM_PINCH_AND_SLIDE("Force pinch + stem slide"),
    OPEN_EAR_CLIP_AND_HOOK_TAP("Clip / ear-hook tap"),
    PHYSICAL_TACTILE_BUTTON_AND_JOYSTICK("Physical button / joystick"),
    SMART_DIAL_AND_DIGITAL_CROWN("Smart dial / digital crown"),
}

object SiliconFamilyMap {

    private val brandFamilies: Map<String, SiliconFamily> = buildMap {
        // 1. OPOv1 / BES
        put("realme", SiliconFamily.OPO_HEYMELODY)
        put("oneplus", SiliconFamily.OPO_HEYMELODY)
        put("oppo", SiliconFamily.OPO_HEYMELODY)

        // 2. Nothing / CMF
        put("nothing", SiliconFamily.NOTHING_X)
        put("cmf", SiliconFamily.NOTHING_X)

        // 3. Airoha / RACE
        listOf(
            "xiaomi", "redmi", "poco", "earfun", "soundpeats", "edifier",
            "1more", "qcy", "baseus", "ugreen", "tribit",
        ).forEach { put(it, SiliconFamily.AIROHA_RACE) }

        // 4. Sony
        put("sony", SiliconFamily.SONY_TANDEM)

        // 5. Samsung
        put("samsung", SiliconFamily.SAMSUNG_SEAMLESS)
        put("akg", SiliconFamily.SAMSUNG_SEAMLESS)

        // 6. Apple
        put("apple", SiliconFamily.APPLE_L2CAP)
        put("beats", SiliconFamily.APPLE_L2CAP)

        // 7. Soundcore
        put("anker", SiliconFamily.ANKER_SOUNDCORE)
        put("soundcore", SiliconFamily.ANKER_SOUNDCORE)

        // 8. Qualcomm GAIA
        listOf(
            "sennheiser", "bose", "jabra", "bowers", "bang & olufsen", "beoplay",
            "master & dynamic", "beyerdynamic", "audio-technica", "technics",
            "panasonic", "philips", "marshall", "jbl", "lg", "skullcandy", "amazon",
            "echo", "fidelio", "tour", "live", "tune",
        ).forEach { put(it, SiliconFamily.QUALCOMM_GAIA) }

        // 9. Huawei / Honor
        listOf("huawei", "honor", "vivo", "iqoo").forEach {
            put(it, SiliconFamily.HUAWEI_HONOR)
        }

        // 10. Open-ear / bone conduction
        listOf("shokz", "suunto", "oladance", "nank", "sanag").forEach {
            put(it, SiliconFamily.OPEN_EAR)
        }

        // 11. Jieli / Bluetrum
        listOf(
            "boat", "noise", "boult", "mivi", "truke", "zebronics",
            "ptron", "fire-boltt", "wings",
        ).forEach { put(it, SiliconFamily.JIELI_BLUETRUM) }

        // 12. Google
        put("google", SiliconFamily.GOOGLE_FAST_PAIR)
        put("pixel", SiliconFamily.GOOGLE_FAST_PAIR)
    }

    fun resolve(manufacturer: String, model: String): SiliconFamily {
        val text = "$manufacturer $model".lowercase()
        brandFamilies.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { text.contains(it.key) }
            ?.let { return it.value }
        return SiliconFamily.QUALCOMM_GAIA
    }

    fun controlSchema(
        manufacturer: String,
        model: String,
        formFactor: AudioFormFactor,
    ): PhysicalControlSchema {
        val text = "$manufacturer $model".lowercase()

        if (text.contains("crusher") || text.contains("smart dial") ||
            text.contains("neckband pro") || text.contains("airpods max") ||
            text.contains("h95") || text.contains("h100") || text.contains("beoplay h9")
        ) {
            return PhysicalControlSchema.SMART_DIAL_AND_DIGITAL_CROWN
        }

        if (text.contains("clip") || text.contains("open") ||
            text.contains("aero fit") || text.contains("aerofit") ||
            text.contains("freeclip") || text.contains("ultra open") ||
            text.contains("openfit") || text.contains("opendots") ||
            text.contains("oladance") || text.contains("nank") ||
            text.contains("sanag")
        ) {
            return PhysicalControlSchema.OPEN_EAR_CLIP_AND_HOOK_TAP
        }

        if (text.contains("airpods pro") || text.contains("airpods 4") ||
            text.contains("nothing ear") || text.contains("oneplus buds pro") ||
            text.contains("enco x") || text.contains("air 6 pro") ||
            text.contains("air 7 pro") || text.contains("air8 pro") ||
            text.contains("buds3 pro") || text.contains("freebuds pro")
        ) {
            return PhysicalControlSchema.FORCE_STEM_PINCH_AND_SLIDE
        }

        if (text.contains("jabra") || text.contains("elite") ||
            text.contains("wf-c500") || text.contains("wf-c510") ||
            text.contains("wh-1000") || text.contains("wi-1000") ||
            text.contains("marshall") || text.contains("major") ||
            text.contains("monitor") || text.contains("openrun") ||
            text.contains("opencomm") || text.contains("suunto") ||
            text.contains("neckband") || text.contains("rockerz") ||
            text.contains("collar") || text.contains("bullets wireless") ||
            text.contains("freelace") ||
            formFactor == AudioFormFactor.HEADSET_OVER_ON_EAR ||
            formFactor == AudioFormFactor.NECKBAND
        ) {
            return PhysicalControlSchema.PHYSICAL_TACTILE_BUTTON_AND_JOYSTICK
        }

        return PhysicalControlSchema.CAPACITIVE_TOUCH_PAD
    }
}

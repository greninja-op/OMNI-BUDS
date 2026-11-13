package com.omnibuds.android.hardware.protocol

import android.content.Context
import android.content.SharedPreferences

enum class AncProfileType(val title: String, val description: String) {
    PROFILE_A(
        "Profile A (Standard HeyMelody)",
        "Off: 0x00 | ANC: 0x01 | Transparency: 0x02",
    ),
    PROFILE_B(
        "Profile B (Alternative Airoha/Sony)",
        "ANC: 0x01 | Off: 0x02 | Transparency: 0x03",
    ),
    PROFILE_C(
        "Profile C (Legacy Bitmask)",
        "Off: 0x01 | ANC: 0x02 | Transparency: 0x04",
    ),
    PROFILE_D(
        "Profile D (realme BES)",
        "Off: 0x01 | Transparency: 0x02 | ANC: 0x04",
    ),
    PROFILE_E(
        "Profile E (realme Buds T300)",
        "Off: 0x02 | Transparency: 0x01 | ANC: 0x04",
    ),
    PROFILE_F(
        "Profile F (realme Buds T300 direct)",
        "Off: 0x00 | Transparency: 0x01 | ANC: 0x04",
    ),
    PROFILE_G(
        "Profile G (realme Buds T300, measured effect)",
        "Off: 0x08 | Transparency: 0x02 | ANC: 0x01",
    ),
    CUSTOM(
        "Custom Calibrated",
        "User-calibrated hardware byte mapping",
    ),
}

data class AncByteMapping(
    val offByte: Byte,
    val ancByte: Byte,
    val transByte: Byte,
    val profileType: AncProfileType = AncProfileType.PROFILE_A,
) {
    fun byteForMode(mode: String): Byte {
        return when (mode.uppercase()) {
            "ANC", "ANC_ON", "NOISE_CANCELLATION" -> ancByte
            "TRANSPARENCY", "AMBIENT" -> transByte
            "OFF", "NORMAL" -> offByte
            else -> offByte
        }
    }

    fun modeForByte(byteVal: Byte): String? {
        return when (byteVal) {
            ancByte -> "ANC"
            transByte -> "TRANSPARENCY"
            offByte -> "OFF"
            else -> null
        }
    }

    companion object {
        val PROFILE_A = AncByteMapping(
            offByte = 0x00,
            ancByte = 0x01,
            transByte = 0x02,
            profileType = AncProfileType.PROFILE_A,
        )
        val PROFILE_B = AncByteMapping(
            offByte = 0x02,
            ancByte = 0x01,
            transByte = 0x03,
            profileType = AncProfileType.PROFILE_B,
        )
        val PROFILE_C = AncByteMapping(
            offByte = 0x01,
            ancByte = 0x02,
            transByte = 0x04,
            profileType = AncProfileType.PROFILE_C,
        )
        val PROFILE_D = AncByteMapping(
            offByte = 0x01,
            ancByte = 0x04,
            transByte = 0x02,
            profileType = AncProfileType.PROFILE_D,
        )
        val PROFILE_E = AncByteMapping(
            offByte = 0x02,
            ancByte = 0x04,
            transByte = 0x01,
            profileType = AncProfileType.PROFILE_E,
        )
        val PROFILE_F = AncByteMapping(
            offByte = 0x00,
            ancByte = 0x04,
            transByte = 0x01,
            profileType = AncProfileType.PROFILE_F,
        )
        // Measured on a realme Buds T300 by the effect the user hears: 0x01 gives ANC,
        // 0x02 gives Transparency, 0x08 gives Ambient/Normal. Writing 0x04 or 0x00 changes
        // nothing, and the buds report the same values back.
        // The firmware's activation sound does NOT match the effect on this unit: it plays
        // the ANC sound for 0x08 and the ambient sound for 0x01, also on a stem press with
        // no app involved. The app follows the effect. Do not "fix" this by swapping the bytes.
        val PROFILE_G = AncByteMapping(
            offByte = 0x08,
            ancByte = 0x01,
            transByte = 0x02,
            profileType = AncProfileType.PROFILE_G,
        )

        fun forType(type: AncProfileType): AncByteMapping {
            return when (type) {
                AncProfileType.PROFILE_A -> PROFILE_A
                AncProfileType.PROFILE_B -> PROFILE_B
                AncProfileType.PROFILE_C -> PROFILE_C
                AncProfileType.PROFILE_D -> PROFILE_D
                AncProfileType.PROFILE_E -> PROFILE_E
                AncProfileType.PROFILE_F -> PROFILE_F
                AncProfileType.PROFILE_G -> PROFILE_G
                AncProfileType.CUSTOM -> PROFILE_A
            }
        }
    }
}

/**
 * Manages per-device persistent ANC opcode profile and custom byte mappings.
 */
object OpcodeRemapMatrix {

    private const val PREFS_NAME = "omni_anc_opcode_matrix"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getMapping(address: String): AncByteMapping =
        getMapping(address, AncProfileType.PROFILE_A)

    /**
     * Reads the stored mapping, falling back to [defaultType] when the user has
     * never chosen one for this device. Different protocols use different byte
     * orders, so the caller supplies the correct default.
     */
    fun getMapping(address: String, defaultType: AncProfileType): AncByteMapping {
        val p = prefs ?: return AncByteMapping.forType(defaultType)
        val typeStr = p.getString("v2_type_$address", defaultType.name)
        val type = try {
            AncProfileType.valueOf(typeStr ?: defaultType.name)
        } catch (_: Exception) {
            defaultType
        }

        if (type == AncProfileType.CUSTOM) {
            val off = p.getInt("v2_custom_off_$address", 0x00).toByte()
            val anc = p.getInt("v2_custom_anc_$address", 0x01).toByte()
            val trans = p.getInt("v2_custom_trans_$address", 0x02).toByte()
            return AncByteMapping(offByte = off, ancByte = anc, transByte = trans, profileType = AncProfileType.CUSTOM)
        }

        return AncByteMapping.forType(type)
    }

    /** True when this device has an explicitly stored profile or custom bytes. */
    fun hasStoredMapping(address: String): Boolean =
        prefs?.contains("v2_type_$address") == true

    fun setProfile(address: String, type: AncProfileType): AncByteMapping {
        prefs?.edit()?.putString("v2_type_$address", type.name)?.apply()
        return getMapping(address)
    }

    fun cycleProfile(address: String): AncByteMapping {
        val current = getMapping(address)
        val nextType = when (current.profileType) {
            AncProfileType.PROFILE_A -> AncProfileType.PROFILE_B
            AncProfileType.PROFILE_B -> AncProfileType.PROFILE_C
            AncProfileType.PROFILE_C -> AncProfileType.PROFILE_D
            AncProfileType.PROFILE_D -> AncProfileType.PROFILE_E
            AncProfileType.PROFILE_E -> AncProfileType.PROFILE_F
            AncProfileType.PROFILE_F -> AncProfileType.PROFILE_G
            AncProfileType.PROFILE_G -> AncProfileType.PROFILE_A
            AncProfileType.CUSTOM -> AncProfileType.PROFILE_A
        }
        return setProfile(address, nextType)
    }

    fun setCustomMapping(address: String, off: Byte, anc: Byte, trans: Byte): AncByteMapping {
        prefs?.edit()
            ?.putString("v2_type_$address", AncProfileType.CUSTOM.name)
            ?.putInt("v2_custom_off_$address", off.toInt() and 0xFF)
            ?.putInt("v2_custom_anc_$address", anc.toInt() and 0xFF)
            ?.putInt("v2_custom_trans_$address", trans.toInt() and 0xFF)
            ?.apply()
        return AncByteMapping(offByte = off, ancByte = anc, transByte = trans, profileType = AncProfileType.CUSTOM)
    }
}


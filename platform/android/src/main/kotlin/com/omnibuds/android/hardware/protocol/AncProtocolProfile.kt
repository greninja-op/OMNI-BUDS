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

        fun forType(type: AncProfileType): AncByteMapping {
            return when (type) {
                AncProfileType.PROFILE_A -> PROFILE_A
                AncProfileType.PROFILE_B -> PROFILE_B
                AncProfileType.PROFILE_C -> PROFILE_C
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

    fun getMapping(address: String): AncByteMapping {
        val p = prefs ?: return AncByteMapping.PROFILE_A
        val typeStr = p.getString("type_$address", AncProfileType.PROFILE_A.name)
        val type = try {
            AncProfileType.valueOf(typeStr ?: AncProfileType.PROFILE_A.name)
        } catch (_: Exception) {
            AncProfileType.PROFILE_A
        }

        if (type == AncProfileType.CUSTOM) {
            val off = p.getInt("custom_off_$address", 0x00).toByte()
            val anc = p.getInt("custom_anc_$address", 0x01).toByte()
            val trans = p.getInt("custom_trans_$address", 0x02).toByte()
            return AncByteMapping(offByte = off, ancByte = anc, transByte = trans, profileType = AncProfileType.CUSTOM)
        }

        return AncByteMapping.forType(type)
    }

    fun setProfile(address: String, type: AncProfileType): AncByteMapping {
        prefs?.edit()?.putString("type_$address", type.name)?.apply()
        return getMapping(address)
    }

    fun cycleProfile(address: String): AncByteMapping {
        val current = getMapping(address)
        val nextType = when (current.profileType) {
            AncProfileType.PROFILE_A -> AncProfileType.PROFILE_B
            AncProfileType.PROFILE_B -> AncProfileType.PROFILE_C
            AncProfileType.PROFILE_C -> AncProfileType.PROFILE_A
            AncProfileType.CUSTOM -> AncProfileType.PROFILE_A
        }
        return setProfile(address, nextType)
    }

    fun setCustomMapping(address: String, off: Byte, anc: Byte, trans: Byte): AncByteMapping {
        prefs?.edit()
            ?.putString("type_$address", AncProfileType.CUSTOM.name)
            ?.putInt("custom_off_$address", off.toInt() and 0xFF)
            ?.putInt("custom_anc_$address", anc.toInt() and 0xFF)
            ?.putInt("custom_trans_$address", trans.toInt() and 0xFF)
            ?.apply()
        return AncByteMapping(offByte = off, ancByte = anc, transByte = trans, profileType = AncProfileType.CUSTOM)
    }
}


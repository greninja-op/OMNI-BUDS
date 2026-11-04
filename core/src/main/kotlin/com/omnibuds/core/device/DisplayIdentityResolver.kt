package com.omnibuds.core.device

/**
 * Deterministic display-identity resolution (Iteration 3).
 *
 * The device-details header once rendered the literal string "Unknown"
 * for real devices: the app bridge froze whatever name the first
 * observation carried (often null), and the presentation fallback chain
 * promoted a placeholder manufacturer value into the display name. This
 * resolver is the single place where a display identity is composed,
 * so that failure mode cannot recur.
 *
 * Field separation (kept strictly apart):
 *  - **displayName** — what the header shows.
 *  - **manufacturer / model** — filled ONLY by a verified catalog/vendor
 *    identification; never guessed from the Bluetooth name.
 *  - **user alias** — the user's own label; outranks everything for the
 *    display name and is never mistaken for the hardware model.
 *
 * Display-name priority:
 *  1. user alias (user-selected identity),
 *  2. Bluetooth alias (the name Android itself displays),
 *  3. Bluetooth device name,
 *  4. verified "Manufacturer Model" (when a verified identification
 *     exists but no Bluetooth name does),
 *  5. the neutral fallback [FALLBACK_DISPLAY_NAME].
 *
 * A candidate is usable only when it is non-blank, is not the literal
 * placeholder "Unknown" (in any case), and is not a MAC address —
 * none of those is a name.
 */
object DisplayIdentityResolver {

    /** Neutral label used when no reliable identity exists at all. */
    const val FALLBACK_DISPLAY_NAME = "Bluetooth audio device"

    /** The placeholder literal that must never be treated as a name. */
    private const val PLACEHOLDER = "Unknown"

    /** Which evidence produced the display name. */
    enum class Source {
        USER_ALIAS,
        BLUETOOTH_ALIAS,
        BLUETOOTH_NAME,
        VERIFIED_MODEL,
        FALLBACK,
    }

    data class Resolved(
        val displayName: String,
        val manufacturer: String?,
        val model: String?,
        val source: Source,
    )

    private val macShape = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

    /** The trimmed candidate when it is a real name, else null. */
    fun usableName(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.equals(PLACEHOLDER, ignoreCase = true)) return null
        if (macShape.matches(trimmed)) return null
        return trimmed
    }

    fun resolve(
        userAlias: String? = null,
        verifiedManufacturer: String? = null,
        verifiedModel: String? = null,
        bluetoothAlias: String? = null,
        bluetoothName: String? = null,
    ): Resolved {
        val manufacturer = usableName(verifiedManufacturer)
        val model = usableName(verifiedModel)
        usableName(userAlias)?.let {
            return Resolved(it, manufacturer, model, Source.USER_ALIAS)
        }
        usableName(bluetoothAlias)?.let {
            return Resolved(it, manufacturer, model, Source.BLUETOOTH_ALIAS)
        }
        usableName(bluetoothName)?.let {
            return Resolved(it, manufacturer, model, Source.BLUETOOTH_NAME)
        }
        if (manufacturer != null && model != null) {
            return Resolved("$manufacturer $model", manufacturer, model, Source.VERIFIED_MODEL)
        }
        if (model != null) {
            return Resolved(model, manufacturer, model, Source.VERIFIED_MODEL)
        }
        return Resolved(FALLBACK_DISPLAY_NAME, manufacturer, model, Source.FALLBACK)
    }
}

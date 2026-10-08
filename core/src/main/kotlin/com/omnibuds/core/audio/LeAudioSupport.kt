package com.omnibuds.core.audio

/**
 * Whether LE Audio observation is possible, decided without touching Android.
 *
 * LE Audio's platform APIs (`BluetoothLeAudio`, `TYPE_BLE_HEADSET`) exist from
 * API 33 (OB-P10-REQ-019). This decision is a pure function of the API level
 * so the core never imports `android.os.Build` — the platform module supplies
 * the integer it already reads for every other feature probe, and this
 * function turns it into a verdict. "API too old" and "unknown" are different
 * answers with different consequences:
 *
 * - [API_TOO_OLD]: the OS cannot offer LE Audio. The reconciler gates the
 *   LE Audio profile to UNAVAILABLE/UNKNOWN on this verdict, and no LE Audio
 *   platform class may be loaded.
 * - [SUPPORTED]: the OS exposes the APIs. This says nothing about any
 *   headset — API availability is not device capability.
 * - [UNKNOWN]: the API level itself could not be determined. LE Audio is then
 *   treated as unavailable for observation (never assumed), with the reason
 *   recorded instead of guessed.
 *
 * The platform adapter must additionally isolate every `BluetoothLeAudio`
 * reference in a class that is only loaded when this function returns
 * [SUPPORTED]; a verdict is not a class-loading guard by itself.
 */
enum class LeAudioSupport {
    SUPPORTED,
    API_TOO_OLD,
    UNKNOWN,
}

/** Minimum API level with the LE Audio platform surface. */
const val LE_AUDIO_MIN_API_LEVEL: Int = 33

/**
 * Decides LE Audio observability from an API level, with no framework access.
 *
 * @param apiLevel the platform API level, or null when it could not be determined.
 */
fun leAudioSupport(apiLevel: Int?): LeAudioSupport = when {
    apiLevel == null -> LeAudioSupport.UNKNOWN
    apiLevel >= LE_AUDIO_MIN_API_LEVEL -> LeAudioSupport.SUPPORTED
    else -> LeAudioSupport.API_TOO_OLD
}

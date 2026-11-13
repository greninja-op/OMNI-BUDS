package com.omnibuds.android.hardware.capability

import android.bluetooth.BluetoothDevice
import com.omnibuds.android.ui.compose.model.AudioFormFactor

/**
 * Official companion app schemas across major audio brands.
 */
enum class BrandSchema(val companionAppName: String) {
    NOTHING_X("Nothing X"),
    REALME_LINK("realme Link"),
    HEY_MELODY("HeyMelody"),
    GALAXY_WEARABLE("Galaxy Wearable"),
    SONY_SOUND_CONNECT("Sony Sound Connect"),
    IOS_SETTINGS("iOS AirPods Settings"),
    GENERIC("Standard Bluetooth"),
}

/**
 * How the official app presents battery information for a model.
 *
 * These values are transcribed from the manufacturer's own app/UI, not from
 * what the Bluetooth stack happens to expose. They are deliberately separate
 * from [StrictBrandProfile.supportsCaseBatteryWhenUndocked], which only says
 * whether a case can be cached while the buds are out.
 */
enum class BatteryBudsDisplay {
    SPLIT,
    COMBINED,
    UNKNOWN,
}

enum class BatteryCaseDisplay {
    SEPARATE,
    HIDDEN,
    UNKNOWN,
}

enum class BatteryCaseVisibility {
    ALWAYS,
    ONLY_WHEN_DOCKED,
    ONLY_WHEN_CASE_OPEN,
    ONLY_WHEN_CHARGING,
    UNKNOWN,
}

enum class BatteryCaseWhileBudsOut {
    LIVE,
    LAST_KNOWN,
    HIDDEN,
    UNKNOWN,
}

data class BatteryDisplayContract(
    val budsDisplay: BatteryBudsDisplay = BatteryBudsDisplay.UNKNOWN,
    val caseDisplay: BatteryCaseDisplay = BatteryCaseDisplay.UNKNOWN,
    val caseVisibility: BatteryCaseVisibility = BatteryCaseVisibility.UNKNOWN,
    val caseWhileBudsOut: BatteryCaseWhileBudsOut = BatteryCaseWhileBudsOut.UNKNOWN,
)

/**
 * Definition of an official gesture supported by an earbud model.
 */
data class GestureDefinition(
    val gestureId: String,
    val displayName: String,
    val allowedActions: List<Pair<String, String>>, // actionId to displayLabel
)

/**
 * Authoritative, brand-exact profile schema governing UI layout,
 * controls, presets, gestures, and battery visibility rules.
 */
data class StrictBrandProfile(
    val modelKey: String,
    val brandSchema: BrandSchema,
    val manufacturer: String,
    val modelName: String,
    val formFactor: AudioFormFactor,
    val supportsCaseBatteryWhenUndocked: Boolean = false,
    /** Battery presentation contract from the official app, when verified. */
    val batteryContract: BatteryDisplayContract = BatteryDisplayContract(),
    val companionAppName: String = brandSchema.companionAppName,
    // Noise Control
    val hasAnc: Boolean = true,
    val ancModes: List<String> = listOf("Active Noise Cancellation", "Transparency Mode", "Off"),
    val ancSubLevels: List<String> = emptyList(),
    // Equalizer
    val eqPresets: List<String> = emptyList(),
    val defaultEqPreset: String = "Balanced",
    val hasCustom3BandEq: Boolean = false,
    val hasParametric8BandEq: Boolean = false,
    val hasClearBass: Boolean = false, // Sony Clear Bass (-10 to +10)
    val hasBassWave: Boolean = false, // OnePlus BassWave (-5 to +5)
    // Dynamic / Ultra Bass
    val hasUltraBass: Boolean = false, // Nothing Ultra Bass 2.0 (1..5)
    val ultraBassLevels: Int = 5,
    val hasDynamicBass: Boolean = false, // Realme Dynamic Bass
    // Gestures
    val gestures: List<GestureDefinition> = emptyList(),
    // Device Settings & System Toggles
    val hasInEarDetection: Boolean = false,
    val hasLowLagMode: Boolean = false,
    val lowLagModeLabel: String = "Low Lag Mode",
    val hasDualConnection: Boolean = false, // Multipoint
    val hasFindMyBuds: Boolean = false,
    val hasSmartDial: Boolean = false, // CMF Buds Pro 2 / CMF Neckband Pro case knob
    val hasLdac: Boolean = false,
    val hasSpatialAudio: Boolean = false,
    val spatialAudioLabel: String = "Spatial Audio",
    val hasDolbyAtmos: Boolean = false,
    val hasVolumeEnhancer: Boolean = false,
    val hasMindRelax: Boolean = false,
    val hasGoldenSound: Boolean = false,
    val hasPersonalizedAnc: Boolean = false,
    val hasFitTest: Boolean = false,
    val hasStemSlide: Boolean = false,
    val hasEdgeTap: Boolean = false,
    val hasHeadGestures: Boolean = false,
    val hasSpeakToChat: Boolean = false,
    val hasDsee: Boolean = false,
    val hasZenModeAir: Boolean = false,
    val hasVoiceDetect: Boolean = false,
    val hasConversationalAwareness: Boolean = false,
    val hasPersonalizedVolume: Boolean = false,
    val hasHeadphoneAccommodations: Boolean = false,
    val hasPinchSpeed: Boolean = false,
) {
    val siliconFamily: SiliconFamily
        get() = SiliconFamilyMap.resolve(manufacturer, modelName)
    val physicalControlSchema: PhysicalControlSchema
        get() = SiliconFamilyMap.controlSchema(manufacturer, modelName, formFactor)
    val batteryTopology: BatteryTopologyMode
        get() = BatteryTopologyResolver.resolve(manufacturer, modelName, formFactor)

    val hasCaseSuperMic: Boolean
        get() = modelName.contains("Nothing Ear (3)", ignoreCase = true)
    val hasCaseTransmitter: Boolean
        get() = listOf(
            "JBL Tour Pro 3", "JBL Live Buds 3", "JBL Live Beam 3", "JBL Live Flex 3",
            "LG TONE Free T90S", "LG TONE Free T80", "LG TONE Free UT90Q",
            "LG TONE Free FN9", "AKG N5 Hybrid", "AKG N9 Hybrid",
        ).any { modelName.contains(it, ignoreCase = true) }
    val hasOnboardRecorder: Boolean
        get() = modelName.contains("Xiaomi Buds 5 Pro", ignoreCase = true)
    val hasBiometrics: Boolean
        get() = listOf("MOMENTUM Sport", "AirPods Pro 3", "Powerbeats Pro 2")
            .any { modelName.contains(it, ignoreCase = true) }
    val hasSwimMp3: Boolean
        get() = listOf("OpenSwim", "Suunto Aqua")
            .any { modelName.contains(it, ignoreCase = true) }
    val schema: BrandSchema get() = brandSchema
    val ultraBassMaxLevel: Int get() = ultraBassLevels
    val hasNoiseControl: Boolean get() = hasAnc
    val hasAncSubLevels: Boolean get() = ancSubLevels.isNotEmpty()
    val hasEarTipFitTest: Boolean get() = hasFitTest
    val gameModeLabel: String get() = lowLagModeLabel
}

/**
 * Comprehensive cross-brand hardware profile database enforcing 1:1 parity
 * with official manufacturer companion applications.
 */
object StrictBrandProfileDatabase {

    // --- CMF Buds 1 (Gen 1 / Model B168) ---
    val CMF_BUDS_1_GESTURES = listOf(
        GestureDefinition(
            gestureId = "double_tap",
            displayName = "Double Tap",
            allowedActions = listOf(
                "PLAY_PAUSE" to "Play/Pause",
                "SKIP_FORWARD" to "Skip Forward",
                "SKIP_BACK" to "Skip Back",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "NONE" to "No Action",
            ),
        ),
        GestureDefinition(
            gestureId = "triple_tap",
            displayName = "Triple Tap",
            allowedActions = listOf(
                "SKIP_FORWARD" to "Skip Forward",
                "SKIP_BACK" to "Skip Back",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "NONE" to "No Action",
            ),
        ),
        GestureDefinition(
            gestureId = "tap_hold",
            displayName = "Tap & Hold",
            allowedActions = listOf(
                "SWITCH_ANC_TRANSPARENCY" to "Switch ANC / Transparency",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "DECLINE_CALL" to "Decline Incoming Call",
                "NONE" to "No Action",
            ),
        ),
        GestureDefinition(
            gestureId = "double_tap_hold",
            displayName = "Double Tap & Hold",
            allowedActions = listOf(
                "VOLUME_UP" to "Volume Up",
                "VOLUME_DOWN" to "Volume Down",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "LOW_LAG_MODE" to "Low Lag Mode",
                "NONE" to "No Action",
            ),
        ),
    )

    val NOTHING_EAR_PINCH_GESTURES = listOf(
        GestureDefinition(
            gestureId = "single_pinch",
            displayName = "Pinch",
            allowedActions = listOf(
                "PLAY_PAUSE" to "Play/Pause",
                "ANSWER_HANGUP" to "Answer/Hang Up Call",
                "NONE" to "No Action",
            ),
        ),
        GestureDefinition(
            gestureId = "double_pinch",
            displayName = "Double Pinch",
            allowedActions = listOf(
                "SKIP_FORWARD" to "Skip Forward",
                "REJECT_CALL" to "Decline Call",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "NONE" to "No Action",
            ),
        ),
        GestureDefinition(
            gestureId = "triple_pinch",
            displayName = "Triple Pinch",
            allowedActions = listOf(
                "SKIP_BACK" to "Skip Back",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "NONE" to "No Action",
            ),
        ),
        GestureDefinition(
            gestureId = "pinch_hold",
            displayName = "Pinch & Hold",
            allowedActions = listOf(
                "SWITCH_NOISE_CONTROL" to "Switch Noise Control",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "NONE" to "No Action",
            ),
        ),
        GestureDefinition(
            gestureId = "double_pinch_hold",
            displayName = "Double Pinch & Hold",
            allowedActions = listOf(
                "VOLUME_UP" to "Volume Up",
                "VOLUME_DOWN" to "Volume Down",
                "LOW_LAG_MODE" to "Low Lag Mode",
                "NONE" to "No Action",
            ),
        ),
    )

    val REALME_T300_GESTURES = listOf(
        GestureDefinition(
            gestureId = "double_tap",
            displayName = "Double Tap",
            allowedActions = listOf(
                "PLAY_PAUSE" to "Play/Pause",
                "NEXT_TRACK" to "Next Track",
                "PREVIOUS_TRACK" to "Previous Track",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "GAME_MODE" to "50ms Game Mode",
                "NONE" to "None",
            ),
        ),
        GestureDefinition(
            gestureId = "triple_tap",
            displayName = "Triple Tap",
            allowedActions = listOf(
                "NEXT_TRACK" to "Next Track",
                "PREVIOUS_TRACK" to "Previous Track",
                "GAME_MODE" to "50ms Game Mode",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "NONE" to "None",
            ),
        ),
        GestureDefinition(
            gestureId = "long_press",
            displayName = "Touch & Hold",
            allowedActions = listOf(
                "SWITCH_NOISE_CONTROL" to "Switch Noise Control (ANC / Ambient)",
                "VOICE_ASSISTANT" to "Voice Assistant",
                "VOLUME_UP" to "Volume Up",
                "VOLUME_DOWN" to "Volume Down",
                "NONE" to "None",
            ),
        ),
    )

    private val PROFILES = listOf(
        // ====================================================================
        // 1. NOTHING / CMF (Nothing X schema)
        // ====================================================================
        StrictBrandProfile(
            modelKey = "cmf_buds",
            brandSchema = BrandSchema.NOTHING_X,
            manufacturer = "CMF by Nothing",
            modelName = "CMF Buds",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = false, // HIDE case battery ring/card unless valid byte 1..100
            // Nothing X shows L / C / R as three separate readings; the case
            // tile requires the case to be open.
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_CASE_OPEN,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.HIDDEN,
            ),
            hasAnc = true,
            ancModes = listOf("Active Noise Cancellation", "Transparency Mode", "Off"),
            ancSubLevels = listOf("High (42dB)", "Mid", "Low", "Adaptive"),
            eqPresets = listOf("Dirac Opteo", "Balanced", "More Bass", "More Treble", "Voice", "Custom"),
            defaultEqPreset = "Dirac Opteo",
            hasCustom3BandEq = true,
            hasUltraBass = true,
            ultraBassLevels = 5,
            gestures = CMF_BUDS_1_GESTURES,
            hasInEarDetection = true,
            hasLowLagMode = true,
            lowLagModeLabel = "Low Lag Mode",
            hasDualConnection = true,
            hasFindMyBuds = true,
            // Strictly false for CMF Buds 1
            hasSmartDial = false,
            hasLdac = false,
            hasSpatialAudio = false,
            hasDolbyAtmos = false,
            hasVolumeEnhancer = false,
            hasMindRelax = false,
            hasGoldenSound = false,
            hasPersonalizedAnc = false,
            hasFitTest = false,
        ),
        StrictBrandProfile(
            modelKey = "cmf_buds_pro_2",
            brandSchema = BrandSchema.NOTHING_X,
            manufacturer = "CMF by Nothing",
            modelName = "CMF Buds Pro 2",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("Active Noise Cancellation", "Transparency Mode", "Off"),
            ancSubLevels = listOf("High (50dB)", "Mid", "Low", "Adaptive"),
            eqPresets = listOf("Dirac Opteo", "Balanced", "More Bass", "More Treble", "Voice", "Custom"),
            defaultEqPreset = "Dirac Opteo",
            hasCustom3BandEq = true,
            hasUltraBass = true,
            ultraBassLevels = 5,
            gestures = CMF_BUDS_1_GESTURES,
            hasInEarDetection = true,
            hasLowLagMode = true,
            lowLagModeLabel = "Low Lag Mode",
            hasDualConnection = true,
            hasFindMyBuds = true,
            hasSmartDial = true, // Unlocked on CMF Buds Pro 2!
            hasLdac = true, // Unlocked on CMF Buds Pro 2!
            hasSpatialAudio = true,
            spatialAudioLabel = "Spatial Audio Effect",
            hasDolbyAtmos = false,
            hasVolumeEnhancer = false,
            hasMindRelax = false,
            hasGoldenSound = false,
            hasPersonalizedAnc = true,
            hasFitTest = false,
        ),
        StrictBrandProfile(
            modelKey = "cmf_neckband_pro",
            brandSchema = BrandSchema.NOTHING_X,
            manufacturer = "CMF by Nothing",
            modelName = "CMF Neckband Pro",
            formFactor = AudioFormFactor.NECKBAND,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("Active Noise Cancellation", "Transparency Mode", "Off"),
            ancSubLevels = listOf("High (50dB)", "Mid", "Low", "Adaptive"),
            eqPresets = listOf("Dirac Opteo", "Balanced", "More Bass", "More Treble", "Voice", "Custom"),
            defaultEqPreset = "Dirac Opteo",
            hasCustom3BandEq = true,
            hasUltraBass = true,
            ultraBassLevels = 5,
            hasInEarDetection = false,
            hasLowLagMode = true,
            lowLagModeLabel = "Low Lag Mode",
            hasDualConnection = true,
            hasFindMyBuds = true,
            hasSmartDial = true, // Physical 3-in-1 Smart Dial
            hasLdac = false,
            hasSpatialAudio = true,
            spatialAudioLabel = "Spatial Audio Effect",
        ),
        StrictBrandProfile(
            modelKey = "nothing_ear",
            brandSchema = BrandSchema.NOTHING_X,
            manufacturer = "Nothing",
            modelName = "Nothing Ear",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("Active Noise Cancellation", "Transparency Mode", "Off"),
            ancSubLevels = listOf("High (45dB)", "Mid", "Low", "Adaptive"),
            eqPresets = listOf("Balanced", "More Bass", "More Treble", "Voice", "Custom"),
            defaultEqPreset = "Balanced",
            hasCustom3BandEq = true,
            hasParametric8BandEq = true, // Advanced 8-Band Parametric EQ
            hasUltraBass = false,
            gestures = NOTHING_EAR_PINCH_GESTURES,
            hasInEarDetection = true,
            hasLowLagMode = true,
            lowLagModeLabel = "Low Lag Mode",
            hasDualConnection = true,
            hasFindMyBuds = true,
            hasSmartDial = false,
            hasLdac = true,
            hasSpatialAudio = false,
            hasGoldenSound = true, // Personal Sound Profile
            hasFitTest = true, // Ear Tip Fit Test
            hasPinchSpeed = true,
        ),
        StrictBrandProfile(
            modelKey = "nothing_ear_a",
            brandSchema = BrandSchema.NOTHING_X,
            manufacturer = "Nothing",
            modelName = "Nothing Ear (a)",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("Active Noise Cancellation", "Transparency Mode", "Off"),
            ancSubLevels = listOf("High (45dB)", "Mid", "Low", "Adaptive"),
            eqPresets = listOf("Balanced", "More Bass", "More Treble", "Voice", "Custom"),
            defaultEqPreset = "Balanced",
            hasCustom3BandEq = true,
            hasParametric8BandEq = false,
            hasUltraBass = false,
            gestures = NOTHING_EAR_PINCH_GESTURES,
            hasInEarDetection = true,
            hasLowLagMode = true,
            lowLagModeLabel = "Low Lag Mode",
            hasDualConnection = true,
            hasFindMyBuds = true,
            hasSmartDial = false,
            hasLdac = true,
            hasFitTest = true,
            hasPinchSpeed = true,
        ),
        StrictBrandProfile(
            modelKey = "nothing_ear_2",
            brandSchema = BrandSchema.NOTHING_X,
            manufacturer = "Nothing",
            modelName = "Nothing Ear (2)",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("Active Noise Cancellation", "Transparency Mode", "Off"),
            ancSubLevels = listOf("High (40dB)", "Mid", "Low", "Adaptive"),
            eqPresets = listOf("Balanced", "More Bass", "More Treble", "Voice", "Custom"),
            defaultEqPreset = "Balanced",
            hasCustom3BandEq = true,
            hasParametric8BandEq = true,
            hasUltraBass = false,
            gestures = NOTHING_EAR_PINCH_GESTURES,
            hasInEarDetection = true,
            hasLowLagMode = true,
            lowLagModeLabel = "Low Lag Mode",
            hasDualConnection = true,
            hasFindMyBuds = true,
            hasSmartDial = false,
            hasLdac = false,
            hasGoldenSound = true,
            hasFitTest = true,
            hasPinchSpeed = true,
        ),

        // ====================================================================
        // 2. REALME (realme Link schema)
        // ====================================================================
        StrictBrandProfile(
            modelKey = "t300",
            brandSchema = BrandSchema.REALME_LINK,
            manufacturer = "realme",
            modelName = "realme Buds T300",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = true, // Supports 3-ring pogo-pin dock reading
            // realme Link hides the case tile, but the user-facing contract is
            // that the case percentage appears whenever at least one bud is
            // docked. The engine reports caseIsLive for that condition.
            batteryContract = BatteryDisplayContract(
                budsDisplay = BatteryBudsDisplay.SPLIT,
                caseDisplay = BatteryCaseDisplay.SEPARATE,
                caseVisibility = BatteryCaseVisibility.ONLY_WHEN_DOCKED,
                caseWhileBudsOut = BatteryCaseWhileBudsOut.HIDDEN,
            ),
            hasAnc = true,
            ancModes = listOf("ANC", "Transparency", "Normal"),
            ancSubLevels = listOf("30dB"),
            eqPresets = listOf("Pure Bass", "Clear Vocals", "Nature Balance", "Bass Boost+"),
            defaultEqPreset = "Pure Bass",
            hasDynamicBass = true,
            hasVolumeEnhancer = true,
            hasSpatialAudio = true,
            spatialAudioLabel = "360° Spatial Audio",
            hasDolbyAtmos = true,
            hasLowLagMode = true,
            lowLagModeLabel = "50ms Game Mode",
            hasMindRelax = true,
            hasFindMyBuds = true,
            gestures = REALME_T300_GESTURES,
            // Pro features absent on T300
            hasDualConnection = false,
            hasLdac = false,
            hasGoldenSound = false,
            hasPersonalizedAnc = false,
            hasInEarDetection = false,
            hasSmartDial = false,
            hasUltraBass = false,
        ),
        StrictBrandProfile(
            modelKey = "realme_buds_air_pro",
            brandSchema = BrandSchema.REALME_LINK,
            manufacturer = "realme",
            modelName = "realme Buds Air 6 Pro",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = true,
            hasAnc = true,
            ancModes = listOf("ANC", "Transparency", "Normal"),
            ancSubLevels = listOf("Max 50dB", "Moderate", "Mild", "Smart"),
            eqPresets = listOf("Pure Bass", "Clear Vocals", "Nature Balance", "Bass Boost+"),
            defaultEqPreset = "Pure Bass",
            hasDynamicBass = true,
            hasVolumeEnhancer = true,
            hasSpatialAudio = true,
            spatialAudioLabel = "360° Spatial Audio",
            hasDolbyAtmos = true,
            hasLowLagMode = true,
            lowLagModeLabel = "50ms Game Mode",
            hasMindRelax = true,
            hasFindMyBuds = true,
            hasDualConnection = true,
            hasLdac = true,
            hasGoldenSound = true, // Unlocked on Air Pro
            hasPersonalizedAnc = true,
            hasInEarDetection = true,
            hasStemSlide = true,
            hasFitTest = true,
        ),

        // ====================================================================
        // 3. ONEPLUS & OPPO (HeyMelody schema)
        // ====================================================================
        StrictBrandProfile(
            modelKey = "oneplus_buds_pro",
            brandSchema = BrandSchema.HEY_MELODY,
            manufacturer = "OnePlus",
            modelName = "OnePlus Buds 3 / Pro 2 / Pro 3",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("ANC", "Transparency", "Off"),
            ancSubLevels = listOf("Max (49dB)", "Moderate", "Mild", "Smart"),
            eqPresets = listOf("Balanced", "Serenade", "Bass", "Bold"),
            defaultEqPreset = "Balanced",
            hasBassWave = true, // -5 to +5 BassWave
            hasGoldenSound = true, // Golden Sound ear canal compensation
            hasZenModeAir = true, // White noise soundscape
            hasDualConnection = true,
            hasLdac = true,
            hasStemSlide = true, // Stem touch volume slide
            hasLowLagMode = true,
            lowLagModeLabel = "Game Mode",
            hasInEarDetection = true,
            hasFindMyBuds = true,
        ),

        // ====================================================================
        // 4. SAMSUNG (Galaxy Wearable schema)
        // ====================================================================
        StrictBrandProfile(
            modelKey = "galaxy_buds",
            brandSchema = BrandSchema.GALAXY_WEARABLE,
            manufacturer = "Samsung",
            modelName = "Galaxy Buds2 Pro / FE / Buds3 Pro",
            formFactor = AudioFormFactor.BUD_BEAN_IN_EAR,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("Active Noise Canceling", "Ambient Sound", "Off"),
            ancSubLevels = emptyList(),
            eqPresets = listOf("Normal", "Bass Boost", "Soft", "Dynamic", "Clear", "Treble Boost"),
            defaultEqPreset = "Normal",
            hasSpatialAudio = true,
            spatialAudioLabel = "360 Audio (Head Tracking)",
            hasVoiceDetect = true, // Voice Detect auto-transparency
            hasEdgeTap = true, // Double Tap Earbud Edge
            hasDualConnection = true, // Seamless Earbud Connection
            hasLowLagMode = true,
            lowLagModeLabel = "Gaming Mode",
            hasInEarDetection = true,
            hasFindMyBuds = true,
        ),

        // ====================================================================
        // 5. SONY (Sony Sound Connect schema)
        // ====================================================================
        StrictBrandProfile(
            modelKey = "sony_1000x",
            brandSchema = BrandSchema.SONY_SOUND_CONNECT,
            manufacturer = "Sony",
            modelName = "Sony WF-1000XM5 / WH-1000XM5",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("Noise Canceling", "Ambient Sound", "Off"),
            eqPresets = listOf("Off", "Bright", "Excited", "Mellow", "Relaxed", "Vocal", "Treble Boost", "Bass Boost", "Speech", "Manual", "Custom"),
            defaultEqPreset = "Off",
            hasClearBass = true, // Clear Bass (-10 to +10)
            hasSpeakToChat = true,
            hasDsee = true,
            hasDualConnection = true,
            hasLdac = true,
            hasInEarDetection = true,
            hasFindMyBuds = true,
        ),

        // ====================================================================
        // 6. APPLE & BEATS (iOS AirPods Settings schema)
        // ====================================================================
        StrictBrandProfile(
            modelKey = "airpods_pro",
            brandSchema = BrandSchema.IOS_SETTINGS,
            manufacturer = "Apple",
            modelName = "AirPods Pro",
            formFactor = AudioFormFactor.STEM_IN_EAR,
            supportsCaseBatteryWhenUndocked = false,
            hasAnc = true,
            ancModes = listOf("Noise Cancellation", "Adaptive", "Transparency", "Off"),
            hasConversationalAwareness = true,
            hasPersonalizedVolume = true,
            hasSpatialAudio = true,
            spatialAudioLabel = "Spatial Audio (Fixed / Head Tracked)",
            hasHeadphoneAccommodations = true,
            hasPinchSpeed = true,
            hasStemSlide = true,
            hasFitTest = true,
            hasInEarDetection = true,
            hasFindMyBuds = true,
        ),
    )

    /**
     * Neutral profile for devices with no verified companion-app schema.
     *
     * It deliberately claims no vendor feature: no ANC, no EQ presets, no
     * gestures, no battery-split behavior. UIs may still show standard
     * media/battery/codec information because that comes from Android, not
     * from this profile.
     */
    val GENERIC_AUDIO: StrictBrandProfile = StrictBrandProfile(
        modelKey = "generic_audio",
        brandSchema = BrandSchema.GENERIC,
        manufacturer = "",
        modelName = "Bluetooth Audio Device",
        formFactor = AudioFormFactor.STEM_IN_EAR,
        supportsCaseBatteryWhenUndocked = false,
        hasAnc = false,
        ancModes = emptyList(),
        ancSubLevels = emptyList(),
        eqPresets = emptyList(),
        hasCustom3BandEq = false,
        hasParametric8BandEq = false,
        hasClearBass = false,
        hasBassWave = false,
        hasUltraBass = false,
        hasDynamicBass = false,
        gestures = emptyList(),
        hasInEarDetection = false,
        hasLowLagMode = false,
        hasDualConnection = false,
        hasFindMyBuds = false,
        hasSmartDial = false,
        hasLdac = false,
        hasSpatialAudio = false,
        hasDolbyAtmos = false,
        hasVolumeEnhancer = false,
        hasMindRelax = false,
        hasGoldenSound = false,
        hasPersonalizedAnc = false,
        hasFitTest = false,
        hasStemSlide = false,
        hasEdgeTap = false,
        hasHeadGestures = false,
        hasSpeakToChat = false,
        hasDsee = false,
        hasZenModeAir = false,
        hasVoiceDetect = false,
        hasConversationalAwareness = false,
        hasPersonalizedVolume = false,
        hasHeadphoneAccommodations = false,
        hasPinchSpeed = false,
    )

    fun getAllProfiles(): List<StrictBrandProfile> = PROFILES

    fun findProfileByKey(key: String): StrictBrandProfile? =
        PROFILES.firstOrNull { it.modelKey.equals(key, ignoreCase = true) }
}

/**
 * Strict Brand & Model Profile Resolver.
 * Inspects device name, Bluetooth MAC OUI, discovered GATT Service UUIDs,
 * and RFCOMM UUIDs to load the exact brand profile.
 */
object StrictBrandProfileResolver {

    fun resolveProfile(name: String, address: String = ""): StrictBrandProfile =
        resolve(null, nameOverride = name, addressOverride = address)

    fun resolve(
        device: BluetoothDevice?,
        nameOverride: String? = null,
        addressOverride: String? = null,
        discoveredGattUuids: List<String> = emptyList(),
        discoveredRfcommUuids: List<String> = emptyList(),
    ): StrictBrandProfile {
        val name = (nameOverride ?: try { device?.name } catch (_: Exception) { null } ?: "").trim().lowercase()
        val address = (addressOverride ?: try { device?.address } catch (_: Exception) { null } ?: "").trim().uppercase()
        val sdpUuids = try { device?.uuids?.map { it.uuid.toString().lowercase() } ?: emptyList() } catch (_: Exception) { emptyList() }
        val gattUuids = (discoveredGattUuids.map { it.lowercase() } + sdpUuids).distinct()
        val rfcommUuids = (discoveredRfcommUuids.map { it.lowercase() } + sdpUuids).distinct()

        fun profile(key: String): StrictBrandProfile =
            StrictBrandProfileDatabase.findProfileByKey(key)
                ?: StrictBrandProfileDatabase.GENERIC_AUDIO

        // 1. Nothing / CMF Earbuds (Nothing X)
        val isNothingOrCmf = name.contains("cmf") || name.contains("nothing") ||
            name.contains("b168") || name.contains("ear (1)") || name.contains("ear (2)") ||
            name.contains("ear (a)") || name.contains("ear open") ||
            gattUuids.any { it.contains("fee0") || it.contains("fee1") || it.contains("5560") }

        if (isNothingOrCmf) {
            when {
                name.contains("buds pro 2") -> return profile("cmf_buds_pro_2")
                name.contains("neckband") -> return profile("cmf_neckband_pro")
                name.contains("ear (a)") -> return profile("nothing_ear_a")
                name.contains("ear (2)") -> return profile("nothing_ear_2")
                name.contains("nothing ear") -> return profile("nothing_ear")
                // Default CMF Buds (CMF Buds 1 / Model B168)
                else -> return profile("cmf_buds")
            }
        }

        // 2. Realme Earbuds (realme Link)
        val isRealme = name.contains("realme") || address.startsWith("74:D7:13") ||
            rfcommUuids.any { it.contains("079a-d102") } ||
            gattUuids.any { it.contains("0100-3c17") || it.contains("0200-3c17") }

        if (isRealme) {
            if (name.contains("air 5 pro") || name.contains("air 6 pro") || name.contains("air 7 pro") || name.contains("air5 pro") || name.contains("air6 pro")) {
                return profile("realme_buds_air_pro")
            }
            return profile("t300")
        }

        // 3. OnePlus & Oppo (HeyMelody)
        // Only explicit brand/model evidence. A bare "Buds Pro" from another
        // manufacturer must not inherit the OnePlus profile.
        val isOnePlusOrOppo = name.contains("oneplus") || name.contains("oppo") || name.contains("bullets")

        if (isOnePlusOrOppo) {
            return profile("oneplus_buds_pro")
        }

        // 4. Samsung Galaxy Buds (Galaxy Wearable)
        val isSamsung = name.contains("galaxy buds") || name.contains("buds2") || name.contains("buds3") || name.contains("buds live")

        if (isSamsung) {
            return profile("galaxy_buds")
        }

        // 5. Sony (Sony Sound Connect)
        val isSony = name.contains("sony") || name.contains("wf-1000") || name.contains("wh-1000") || name.contains("linkbuds")

        if (isSony) {
            return profile("sony_1000x")
        }

        // 6. Apple & Beats (iOS Settings)
        val isApple = name.contains("airpods") || name.contains("beats") || name.contains("powerbeats") ||
            address.startsWith("F0:D2:F1") || address.startsWith("70:66:1B")

        if (isApple) {
            return profile("airpods_pro")
        }

        // Fallback: explicit T300 keyword, otherwise a neutral profile that
        // claims no vendor feature. Unknown devices must not inherit another
        // model's capabilities.
        if (name.contains("t300")) {
            return profile("t300")
        }

        return StrictBrandProfileDatabase.GENERIC_AUDIO
    }
}

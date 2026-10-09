package com.omnibuds.core.hil

/**
 * Execution environment for a test.
 *
 * Phase 38: a simulated test is never reported as a physical-device
 * test; an emulator result never implies real hardware verification.
 */
enum class HilEnvironment {
    /** Deterministic dry run; no Bluetooth hardware involved. */
    SIMULATED,

    /** Android emulator; no real Bluetooth hardware or firmware. */
    EMULATOR,

    /** Physical Android host, but no verified earbud device. */
    PHYSICAL_ANDROID_HOST,

    /** Real device verified against a hardware profile. */
    HARDWARE_DEVICE_VERIFIED,

    /** Persistence proven per the Phase 18 evidence rules. */
    PERSISTENCE_VERIFIED,

    /** Actual acoustic measurements taken. */
    ACOUSTICALLY_MEASURED,
}

/**
 * Whether this environment may involve physical hardware.
 */
fun HilEnvironment.involvesPhysicalHardware(): Boolean = when (this) {
    HilEnvironment.SIMULATED,
    HilEnvironment.EMULATOR -> false
    HilEnvironment.PHYSICAL_ANDROID_HOST,
    HilEnvironment.HARDWARE_DEVICE_VERIFIED,
    HilEnvironment.PERSISTENCE_VERIFIED,
    HilEnvironment.ACOUSTICALLY_MEASURED -> true
}

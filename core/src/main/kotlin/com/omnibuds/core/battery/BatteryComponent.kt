package com.omnibuds.core.battery

/**
 * Which physical component a battery reading belongs to.
 *
 * Phase 16 (OB-P16-REQ-001): extensible by adding values — no architecture
 * redesign needed. UNKNOWN is the default; never assume a device has all
 * components.
 */
enum class BatteryComponent {
    /** Left earbud. */
    LEFT_EARBUD,

    /** Right earbud. */
    RIGHT_EARBUD,

    /** Charging case. */
    CHARGING_CASE,

    /** Single-unit headphones. */
    HEADPHONES,

    /** Combined/device-level reading (never split into parts). */
    DEVICE,

    /** Component not identified. */
    UNKNOWN,
}

package com.omnibuds.core.platform

/**
 * Operations OmniBuds may want to perform against the phone's Bluetooth stack, each carrying
 * whether Phase 2 is authorised to perform it.
 *
 * The set is wider than Phase 2's scope on purpose. Phase 2 prompt section 5.3 requires the
 * resolver to know what *each* operation needs so that a permission is never requested for a
 * reason the current phase cannot justify; declaring a requirement for a future operation is
 * safe, performing that operation is not. [authorizedInPhase] makes the boundary explicit and
 * testable: [PermissionRequirementResolver] refuses to produce an executable plan for any
 * operation whose [authorizedInPhase] is ahead of the running phase.
 */
enum class BluetoothOperation(
    /** Human-stable identifier used in requirement records, logs and protocol docs. */
    val technicalName: String,

    /** The phase that first authorises performing this operation, not merely modelling it. */
    val authorizedInPhase: Int,

    /** Why a permission would be needed, stated once, so no call site improvises one. */
    val description: String,
) {
    ADAPTER_AVAILABILITY_INSPECTION(
        "adapter.availability-inspection",
        authorizedInPhase = 2,
        description = "Determine whether the phone has a usable Bluetooth adapter at all",
    ),
    ADAPTER_STATE_INSPECTION(
        "adapter.state-inspection",
        authorizedInPhase = 2,
        description = "Read the adapter's current on/off/transitioning state once",
    ),
    ADAPTER_STATE_OBSERVATION(
        "adapter.state-observation",
        authorizedInPhase = 2,
        description = "Receive adapter state changes while a session is being maintained",
    ),
    PLATFORM_CAPABILITY_INSPECTION(
        "platform.capability-inspection",
        authorizedInPhase = 2,
        description = "Describe what this phone and OS expose, without touching a device",
    ),
    PERMISSION_STATUS_INSPECTION(
        "platform.permission-status-inspection",
        authorizedInPhase = 2,
        description = "Report the standing of permissions the app has already been offered",
    ),

    CONNECTED_DEVICE_INSPECTION(
        "device.connected-inspection",
        authorizedInPhase = 3,
        description = "Look at devices the system already considers connected",
    ),
    BONDED_DEVICE_LIST_INSPECTION(
        "device.bonded-list-inspection",
        authorizedInPhase = 3,
        description = "Enumerate paired devices from the system bond list",
    ),
    DEVICE_DISCOVERY_SCAN(
        "device.discovery-scan",
        authorizedInPhase = 3,
        description = "Start classic discovery or BLE scanning to find nearby devices",
    ),
    PROFILE_CONNECTION_STATE_INSPECTION(
        "profile.connection-state-inspection",
        authorizedInPhase = 3,
        description = "Ask the system about A2DP/HFP/LE Audio profile connection state",
    ),
    TRANSPORT_GATT_OPEN(
        "transport.gatt-open",
        authorizedInPhase = 6,
        description = "Open a GATT client and exchange attribute traffic",
    ),
    TRANSPORT_RFCOMM_OPEN(
        "transport.rfcomm-open",
        authorizedInPhase = 6,
        description = "Open an RFCOMM/SPP socket to a device",
    ),
    LE_AUDIO_SESSION_INSPECTION(
        "leaudio.session-inspection",
        authorizedInPhase = 10,
        description = "Inspect LE Audio group and context state through platform APIs",
    ),
    ;

    /** Whether this operation may be *performed* in the given phase. */
    fun isAuthorizedIn(phase: Int): Boolean = authorizedInPhase <= phase
}

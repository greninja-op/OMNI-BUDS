package com.omnibuds.core.processing

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.device.DeviceIdentity

/**
 * Who owns a processing setting.
 *
 * Phase 15 (OB-P15-REQ-013, OB-P15-REQ-020): answers which component owns,
 * observes, changes, and confirms a setting — and whether the state lives
 * in the device, firmware, platform, or app, session-scoped or persistent.
 * Persistence has four distinct owners: firmware, platform, app-preference,
 * session-only.
 */
data class ProcessingOwnership(
    val feature: FeatureId,
    val device: DeviceIdentity,
    /** The domain that owns the setting's truth. */
    val owner: AudioProcessingDomain,
    /** Domains that can observe the setting. */
    val observableBy: Set<AudioProcessingDomain>,
    /** Domains that can change the setting. */
    val changeableBy: Set<AudioProcessingDomain>,
    /** The domain that confirms a change. */
    val confirmedBy: AudioProcessingDomain,
    /** True when the state persists beyond the session. */
    val persistent: Boolean,
    /** Where the persisted value lives, when [persistent]. */
    val persistenceOwner: PersistenceOwner,
) {
    /** The four persistence owners (OB-P15-REQ-020). */
    enum class PersistenceOwner {
        /** Stored in device firmware. */
        DEVICE_FIRMWARE,

        /** Stored by the Android platform. */
        ANDROID_PLATFORM,

        /** Stored as an OmniBuds user preference (not applied to hardware). */
        APP_PREFERENCE,

        /** Session-scoped only; not persisted. */
        SESSION_ONLY,
    }
}

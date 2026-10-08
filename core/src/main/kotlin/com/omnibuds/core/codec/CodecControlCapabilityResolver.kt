package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.device.DeviceIdentity

/**
 * Determines what the platform/device can actually do with a codec.
 *
 * Phase 12 (OB-P12-REQ-014): before any configuration operation, the engine
 * consults the resolver. It considers Android API level, transport, profile,
 * the connected device, available platform APIs, vendor protocol availability,
 * previously verified capabilities, permission state, and lifecycle state.
 *
 * The resolver is the single gate between "the domain wants to control a
 * codec" and "a mechanism exists". Every operation the engine runs has passed
 * through [resolve] first.
 */
interface CodecControlCapabilityResolver {

    /**
     * Resolve the full control capability for [codec] on [device].
     * Never throws for unknown devices — returns an all-false capability
     * with evidence recording the limitation.
     */
    suspend fun resolve(device: DeviceIdentity, codec: Codec): CodecControlCapability

    /** Convenience: can this codec's state be observed here? */
    suspend fun canObserve(device: DeviceIdentity, codec: Codec): Boolean =
        resolve(device, codec).observable

    /** Convenience: can this codec be selected (switched to) here? */
    suspend fun canSelect(device: DeviceIdentity, codec: Codec): Boolean =
        resolve(device, codec).selectable

    /** Convenience: can this codec's parameters be configured here? */
    suspend fun canConfigure(device: DeviceIdentity, codec: Codec): Boolean =
        resolve(device, codec).configurable

    /** Convenience: can a change to this codec be verified here? */
    suspend fun canVerify(device: DeviceIdentity, codec: Codec): Boolean =
        resolve(device, codec).verifiable
}

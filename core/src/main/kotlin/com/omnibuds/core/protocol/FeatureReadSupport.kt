package com.omnibuds.core.protocol

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.config.ConfigurationValue

/**
 * A protocol that can read a feature's value asks for it by [FeatureId] and returns a
 * [ConfigurationValue].
 *
 * **What implementing this says, and what it does not.** It says this *protocol family*
 * has a read mechanism for feature-addressed values. It says nothing whatsoever about the
 * device in front of the session: whether this earbud exposes ANC, battery or a vendor
 * mode is decided by [EarbudProtocol.discoverCapabilities] and recorded as a
 * [com.omnibuds.core.capability.FeatureCapability] (PROTO-ABST-002, PROTO-CAP-001). A type
 * check on an interface is not capability evidence, and treating it as such would be the
 * "fake capability detection" master section 51 names.
 *
 * An unsupported or unmodelled feature is a `Failure` carrying
 * [com.omnibuds.core.common.OmniBudsErrorCategory.UNSUPPORTED_FEATURE] **only** when
 * absence was positively established; the ordinary answer for something nobody has looked
 * at is that the capability engine never asks, and the feature stays
 * [com.omnibuds.core.state.CapabilityState.UNKNOWN] (PROTO-ERR-004). A failed read yields
 * no value at all, never a zeroed one (specs.md section 3 rule 5).
 *
 * **No feature semantics live here.** ANC levels, transparency modes, equalizer band
 * structures and gesture assignments are deliberately absent, because inventing their
 * shapes in Phase 1 would fabricate hardware behaviour that has not been discovered for any
 * device (Phase 1 prompt sections 2, 26 and 51; the feature list of master section 12 is a
 * roadmap, not a specification). They arrive as opaque [ConfigurationValue]s — a
 * [ConfigurationValue.ModeValue], an [ConfigurationValue.IntValue], a name — whose meaning
 * is defined by the phases that own them, addressed by a feature id and mapped to commands
 * by a [CapabilityMapping]. That is what lets a vendor's adaptive-sound mode be read
 * without this contract pretending to understand it.
 *
 * Phase 1 defines the contract only; there is no implementation in `:core`
 * (ADR-P0-003, ADR-P0-008).
 */
interface FeatureReadSupport {

    /**
     * Read the current value of [feature].
     *
     * Suspending because the answer comes from a device (specs.md section 1.1, section
     * 5.2). Cancellation must propagate and report `Cancelled`, not success
     * (specs.md section 5.3).
     */
    suspend fun readFeature(feature: FeatureId): OperationOutcome<ConfigurationValue>
}

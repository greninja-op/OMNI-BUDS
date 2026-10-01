package com.omnibuds.core.protocol

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.config.ConfigurationValue

/**
 * A protocol that can set a feature's value.
 *
 * **What implementing this says, and what it does not.** It says this *protocol family*
 * has a write mechanism. It does not say the connected device accepts a write, and it does
 * not make a control offerable: that is decided by capability discovery, whose result must
 * be at a controllable [com.omnibuds.core.state.CapabilityState] rung before any surface
 * shows one (PROTO-ABST-002, PROTO-CAP-005). `readFeature` and this method exist as
 * separate interfaces precisely so a read-only protocol can implement the first alone
 * (master section 9: no vendor is forced to implement every operation).
 *
 * **Success here establishes the least, not the most.** A returned `Success(Unit)` means
 * the channel reported that the command was accepted — rung 2 of the persistence ladder.
 * It does not establish that the value was applied, and it never establishes that anything
 * survives a disconnect: `SUPPORTED_PERSISTENT` requires read-back and
 * `PERSISTENCE_VERIFIED` requires a real reconnect (PROTO-PERSIST-001, ADR-P0-005, master
 * section 24). Whoever calls this must then read the affected feature back and record the
 * comparison.
 *
 * **A timeout is not a licence to send again.** When this returns a
 * [com.omnibuds.core.common.OmniBudsErrorCategory.TIMEOUT] failure, the device's state is
 * unknown and the resolution is to read it, never to re-issue the write
 * (PROTO-ERR-002, specs.md section 4: `SET ANC` -&gt; Timeout -&gt; do not resend -&gt; read
 * the current value). That asymmetry is data — [EffectClass] on the command being written —
 * and an implementation may not override it locally.
 *
 * **No feature semantics live here.** There is no `setAnc`, no `setTransparency`, no
 * `writeEqualizer`, no gesture table, because specifying them in Phase 1 would mean
 * inventing hardware behaviour no device has been shown to have (Phase 1 prompt sections 2,
 * 26, 51 and 53). Values travel as [ConfigurationValue]s whose meaning the phases owning
 * those features define, and which command carries them is a [CapabilityMapping] fact.
 *
 * Phase 1 defines the contract only; there is no implementation in `:core`, and none may be
 * written that sets a field and calls it control (ADR-P0-003, ADR-P0-008, prompt section
 * 53).
 */
interface FeatureWriteSupport {

    /**
     * Ask the device to set [feature] to [value].
     *
     * Whether the write is permitted at all — protocol confidence, session state, feature
     * dependency conflicts — is decided above this interface
     * (`protocol-governance.md` section 8 step 7, master section 29); this method is the
     * mechanism, never the authorisation.
     */
    suspend fun writeFeature(feature: FeatureId, value: ConfigurationValue): OperationOutcome<Unit>
}

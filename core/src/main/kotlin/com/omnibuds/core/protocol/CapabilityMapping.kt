package com.omnibuds.core.protocol

import com.omnibuds.core.common.FeatureId

/**
 * Which operation reads and which operation sets one feature, for one protocol.
 *
 * This is the `CapabilityMapping` element of PROTO-NOMAGIC-001, and PROTO-NOMAGIC-003
 * makes it mandatory: every protocol literal needs a named definition element, a
 * documented line carrying its evidence, *and* a capability mapping. A mapping is
 * therefore the only place a feature and a command are allowed to meet — nothing above
 * this layer may pair them by hand at a call site.
 *
 * **A feature with no mapping is neither supported nor unsupported; it is unmodelled, and
 * the capability engine must leave it [com.omnibuds.core.state.CapabilityState.UNKNOWN].**
 * That is the rule PROTO-VENDOR-002 and PROTO-VENDOR-003 state, and master section 53
 * explains why it matters: absence of a mapping is absence of *knowledge*, while
 * `UNSUPPORTED` is a positive establishment that the device does not implement something.
 * Reading the first as the second would hide a feature the user's device may well have,
 * and is the single most damaging shortcut available in this system. A missing lookup may
 * not produce `UNSUPPORTED_FEATURE` either (PROTO-ERR-004).
 *
 * The [effectClass] rule is enforced here rather than trusted, because it is the retry
 * gate: a mapping that names a write command without stating its effect class would let a
 * caller default the class and re-send a side-effecting command after a timeout
 * (specs.md section 4). Conversely a mapping that claims [EffectClass.READ] while naming a
 * write command would mark a device-changing operation as safe to repeat, so that pairing
 * is refused too.
 *
 * Command ids are references into [ProtocolDefinition.commands], never free-floating
 * strings: PROTO-NOMAGIC-002 forbids protocol facts existing outside the definition
 * elements, and a reference that resolves to nothing is a dangling fact, not a hint.
 */
data class CapabilityMapping(
    /** The feature being modelled; core or vendor-namespaced, and identity never a label. */
    val feature: FeatureId,

    /** The command that reads this feature, or null when the protocol has no read path. */
    val readCommandId: String?,

    /** The command that sets this feature, or null when it is readable only or unmodelled. */
    val writeCommandId: String?,

    /**
     * The effect the mapping's write has on the device, or [EffectClass.READ] when the
     * mapping only reads.
     *
     * Non-null exactly when [writeCommandId] is non-null, unless the mapping is purely a
     * read path and states [EffectClass.READ].
     */
    val effectClass: EffectClass?,
) {

    init {
        require(readCommandId != null || writeCommandId != null) {
            "${feature.qualifiedName} maps no command at all. An unmodelled feature is recorded " +
                "by the absence of a CapabilityMapping, not by an empty one, so a mapping with " +
                "neither command id would claim knowledge it cannot point at"
        }
        require(readCommandId == null || readCommandId.isNotBlank()) {
            "readCommandId for ${feature.qualifiedName} is blank; use null when there is no read path"
        }
        require(writeCommandId == null || writeCommandId.isNotBlank()) {
            "writeCommandId for ${feature.qualifiedName} is blank; use null when there is no write path"
        }

        if (writeCommandId != null) {
            val effect = requireNotNull(effectClass) {
                "${feature.qualifiedName} names write command '$writeCommandId' but declares no " +
                    "effectClass, which is the retry gate for a command that changes the device"
            }
            require(effect != EffectClass.READ) {
                "${feature.qualifiedName} declares a write command but effectClass READ; READ would " +
                    "mark a device-changing operation as safe to re-send automatically"
            }
        } else {
            require(effectClass == null || effectClass == EffectClass.READ) {
                "${feature.qualifiedName} declares $effectClass with no write command; an effect " +
                    "class describing a change needs the command that makes it"
            }
        }
    }

    /** Whether this mapping has a read path defined by the protocol. */
    val hasReadCommand: Boolean
        get() = readCommandId != null

    /**
     * Whether this mapping has a write path defined by the protocol.
     *
     * A property of the *protocol*, not of any device: whether this device may actually be
     * written is decided by capability discovery and the persistence ladder
     * (PROTO-ABST-002, PROTO-CAP-001).
     */
    val hasWriteCommand: Boolean
        get() = writeCommandId != null

    /** Whether the feature is a vendor extension rather than a core feature. */
    val isVendorExtension: Boolean
        get() = feature.isVendorExtension

    /** Whether the write behind this mapping may ever be re-issued automatically. */
    val permitsAutomaticRetry: Boolean
        get() = effectClass?.permitsAutomaticRetry ?: false
}

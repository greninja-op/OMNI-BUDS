package com.omnibuds.core.protocoltest

/**
 * A protocol test campaign: a named, stable selection of test cases.
 *
 * Phase 37: campaigns are declarative. Only campaigns that map to
 * real tests exist.
 */
data class ProtocolCampaign(
    val campaignId: String,
    val description: String,
    /** Test-case ID prefixes included, e.g. `proto.framing.`. */
    val includes: Set<String> = emptySet(),
    /** Test-case IDs explicitly excluded. */
    val excludes: Set<String> = emptySet(),
) {
    init {
        require(campaignId.isNotBlank()) { "campaignId must not be blank" }
    }

    /** True when [testCaseId] is a member of this campaign. */
    fun selects(testCaseId: String): Boolean {
        if (testCaseId in excludes) return false
        if (includes.isEmpty()) return true
        return includes.any { testCaseId.startsWith(it) }
    }
}

/**
 * Standard offline campaigns.
 */
object ProtocolCampaigns {
    val PARSER_REGRESSION = ProtocolCampaign(
        campaignId = "parser-regression",
        description = "Fast parser conformance regression.",
        includes = setOf("proto.parser."),
    )
    val FRAMING_CONFORMANCE = ProtocolCampaign(
        campaignId = "framing-conformance",
        description = "Framing and message-boundary conformance.",
        includes = setOf("proto.framing."),
    )
    val MALFORMED_INPUT_SAFETY = ProtocolCampaign(
        campaignId = "malformed-input-safety",
        description = "Malformed and boundary input safety.",
        includes = setOf("proto.malformed."),
    )
    val FULL_OFFLINE = ProtocolCampaign(
        campaignId = "full-offline",
        description = "Full offline protocol regression.",
    )

    val ALL = listOf(
        PARSER_REGRESSION,
        FRAMING_CONFORMANCE,
        MALFORMED_INPUT_SAFETY,
        FULL_OFFLINE,
    )
}

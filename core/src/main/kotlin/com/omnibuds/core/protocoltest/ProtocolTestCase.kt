package com.omnibuds.core.protocoltest

/**
 * Where a protocol-test fixture came from. Mirrors the lab's
 * provenance model without importing it sideways.
 */
enum class ProtocolFixtureOrigin {
    SYNTHETIC,
    DOCUMENTED,
    SANITIZED_CAPTURE,
    IMPORTED,
}

/**
 * Versioned protocol test-case schema.
 *
 * Phase 37: declarative data only. A test case can never specify
 * executable code, shell commands, reflection targets, or device
 * writes. Fixture references are resolved against an approved
 * directory; paths that escape are rejected.
 */
data class ProtocolTestCase(
    /** Stable test-case ID, e.g. `proto.framing.truncated`. */
    val testCaseId: String,
    /** Schema version. Only [SUPPORTED_SCHEMA_VERSION] is accepted. */
    val schemaVersion: Int,
    /** Protocol identifier, e.g. `vendor.acme.control`. */
    val protocolId: String,
    /** Protocol version/revision under test. */
    val protocolVersion: String,
    /** Fixture ID consumed by this case. */
    val fixtureId: String,
    /** Fixture origin: synthetic, documented, sanitized-capture, imported. */
    val fixtureOrigin: ProtocolFixtureOrigin,
    /** Input bytes as hex. */
    val inputHex: String,
    /** Expected outcome: `parsed`, `incomplete`, `rejected`, etc. */
    val expectedOutcome: String,
    /** Expected failure category when the outcome is a failure. */
    val expectedFailureCategory: String?,
    /** Per-test execution budget in milliseconds. */
    val timeoutMillis: Long,
    /** Campaigns this case belongs to. */
    val campaigns: Set<String>,
    /** Requirement IDs validated. */
    val requirementRefs: List<String>,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val MAX_HEX_CHARS = 131_072 // 64 KiB
        const val MAX_TIMEOUT_MILLIS = 30_000L
        const val MIN_TIMEOUT_MILLIS = 1L
    }
}

/** Test-case validation result. */
sealed interface TestCaseValidation {
    data object Valid : TestCaseValidation
    data class Invalid(val reasons: List<String>) : TestCaseValidation
}

/**
 * Validates protocol test cases. External definitions are untrusted.
 */
object ProtocolTestCaseValidator {

    fun validate(testCase: ProtocolTestCase): TestCaseValidation {
        val reasons = mutableListOf<String>()

        if (testCase.testCaseId.isBlank()) {
            reasons.add("testCaseId must not be blank")
        }
        if (testCase.schemaVersion != ProtocolTestCase.SUPPORTED_SCHEMA_VERSION) {
            reasons.add(
                "unsupported schema version: ${testCase.schemaVersion}",
            )
        }
        if (testCase.protocolId.isBlank()) {
            reasons.add("protocolId must not be blank")
        }
        if (testCase.fixtureId.isBlank()) {
            reasons.add("fixtureId must not be blank")
        }
        // Fixture references must be plain IDs, never paths.
        if (testCase.fixtureId.contains('/') || testCase.fixtureId.contains('\\') ||
            testCase.fixtureId.contains("..")
        ) {
            reasons.add("fixtureId must be a plain identifier, not a path")
        }
        if (testCase.inputHex.length > ProtocolTestCase.MAX_HEX_CHARS) {
            reasons.add("inputHex exceeds ${ProtocolTestCase.MAX_HEX_CHARS} chars")
        }
        if (testCase.inputHex.any { it !in "0123456789abcdefABCDEF" }) {
            reasons.add("inputHex must be valid hex")
        }
        if (testCase.timeoutMillis !in
            ProtocolTestCase.MIN_TIMEOUT_MILLIS..ProtocolTestCase.MAX_TIMEOUT_MILLIS
        ) {
            reasons.add(
                "timeoutMillis must be in " +
                    "${ProtocolTestCase.MIN_TIMEOUT_MILLIS}.." +
                    ProtocolTestCase.MAX_TIMEOUT_MILLIS,
            )
        }
        if (testCase.expectedOutcome.isBlank()) {
            reasons.add("expectedOutcome must not be blank")
        }

        return if (reasons.isEmpty()) TestCaseValidation.Valid
        else TestCaseValidation.Invalid(reasons.sorted())
    }
}

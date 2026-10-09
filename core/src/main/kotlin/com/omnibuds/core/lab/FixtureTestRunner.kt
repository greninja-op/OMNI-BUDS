package com.omnibuds.core.lab

/**
 * Deterministic fixture generation + headless parser test runner.
 *
 * Phase 20 (OB-P20-REQ-017/018): fixtures preserve provenance; the runner
 * is deterministic and CI-suitable. Fixtures prove parser behavior, not
 * protocol correctness.
 */
object FixtureGenerator {

    /**
     * Generate a fixture from a trace event.
     * Deterministic: same input → same output.
     */
    fun fromEvent(
        fixtureId: String,
        trace: ProtocolTrace,
        event: TraceEvent,
        expectedOutcome: String,
    ): ParserFixture = ParserFixture(
        fixtureId = fixtureId,
        origin = when (trace.sourceType) {
            TraceSourceType.SYNTHETIC_FIXTURE -> FixtureOrigin.SYNTHETIC
            TraceSourceType.GENERATED_TEST_CASE -> FixtureOrigin.SYNTHETIC
            TraceSourceType.DOCUMENTED_EXAMPLE -> FixtureOrigin.DOCUMENTED
            TraceSourceType.SANITIZED_CAPTURE -> FixtureOrigin.SANITIZED_CAPTURE
            TraceSourceType.IMPORTED_TRACE -> FixtureOrigin.IMPORTED
        },
        traceRef = trace.traceId,
        // Never mislabel synthetic as captured.
        isSynthetic = trace.sourceType == TraceSourceType.SYNTHETIC_FIXTURE ||
            trace.sourceType == TraceSourceType.GENERATED_TEST_CASE,
        redacted = event.redacted,
        inputHex = event.payload?.toHex(1024) ?: "",
        expectedOutcome = expectedOutcome,
        protocolId = trace.protocolId,
        schemaVersion = trace.formatVersion,
    )
}

/** Where a fixture came from. */
enum class FixtureOrigin {
    SYNTHETIC,
    DOCUMENTED,
    SANITIZED_CAPTURE,
    IMPORTED,
}

/** One parser test fixture. */
data class ParserFixture(
    val fixtureId: String,
    val origin: FixtureOrigin,
    val traceRef: String?,
    val isSynthetic: Boolean,
    val redacted: Boolean,
    val inputHex: String,
    val expectedOutcome: String,
    val protocolId: String?,
    val schemaVersion: Int,
)

/**
 * Headless parser test runner.
 *
 * Phase 20: deterministic; each case records expected vs actual.
 */
object ParserTestRunner {

    /** Run a parser against fixtures. */
    fun run(
        parser: LabParser,
        fixtures: List<ParserFixture>,
    ): TestRunReport {
        val results = fixtures.map { fixture ->
            val (outcome, actual) = try {
                val input = hexToBytes(fixture.inputHex)
                    ?: return@map FixtureResult(
                        fixtureId = fixture.fixtureId,
                        parserId = parser.parserId,
                        expected = fixture.expectedOutcome,
                        actual = "malformed",
                        passed = fixture.expectedOutcome == "malformed",
                    )
                val parsed = parser.parse(input)
                parsed to parsed.describe()
            } catch (e: Exception) {
                ParseOutcome.Malformed("parser threw: ${e.message}") to "malformed"
            }
            FixtureResult(
                fixtureId = fixture.fixtureId,
                parserId = parser.parserId,
                expected = fixture.expectedOutcome,
                actual = actual,
                passed = actual == fixture.expectedOutcome,
            )
        }
        return TestRunReport(
            parserId = parser.parserId,
            total = results.size,
            passed = results.count { it.passed },
            failed = results.count { !it.passed },
            results = results,
        )
    }

    /**
     * Decode hex, or null when the input is not valid hex.
     * The `toHex` truncation suffix (`…(+N bytes)`) is not valid hex —
     * fixtures from truncated payloads are treated as malformed input.
     */
    private fun hexToBytes(hex: String): ByteArray? {
        // Strip the truncation suffix if present.
        val truncated = hex.substringBefore("…")
        val clean = truncated.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        if (clean.length % 2 != 0 || clean.isEmpty()) return null
        // If filtering removed characters, the input wasn't pure hex.
        if (clean.length != truncated.filter { !it.isWhitespace() }.length) return null
        return try {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun ParseOutcome.describe(): String = when (this) {
        is ParseOutcome.Parsed -> "parsed"
        is ParseOutcome.Incomplete -> "incomplete"
        is ParseOutcome.UnsupportedFormat -> "unsupported-format"
        is ParseOutcome.Malformed -> "malformed"
        is ParseOutcome.UnsupportedVersion -> "unsupported-version"
        is ParseOutcome.LimitExceeded -> "limit-exceeded"
    }
}

/** One fixture's result. */
data class FixtureResult(
    val fixtureId: String,
    val parserId: String,
    val expected: String,
    val actual: String,
    val passed: Boolean,
)

/** A full test-run report. */
data class TestRunReport(
    val parserId: String,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val results: List<FixtureResult>,
)

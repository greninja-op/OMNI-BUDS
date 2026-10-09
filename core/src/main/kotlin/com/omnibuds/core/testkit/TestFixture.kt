package com.omnibuds.core.testkit

/**
 * Provenance of a fixture's values.
 *
 * Phase 30 (OB-P30-REQ-002): synthetic fixtures are never presented as
 * genuine vendor captures.
 */
enum class FixtureProvenance {
    /** Hand-written for the test; not a real capture. */
    SYNTHETIC,

    /** Derived from documented public specifications. */
    DOCUMENTED,

    /** Real capture with explicit consent; redacted as required. */
    REAL_CAPTURE,
}

/**
 * A versioned test fixture.
 */
data class TestFixture(
    /** Stable fixture identifier. */
    val id: String,
    /** Schema name, e.g. `capability-snapshot`. */
    val schema: String,
    /** Schema version. */
    val version: Int,
    val provenance: FixtureProvenance,
    /** Opaque payload; validated by [FixtureValidator]. */
    val payload: Map<String, Any?>,
) {
    init {
        require(id.isNotBlank()) { "fixture id must not be blank" }
        require(schema.isNotBlank()) { "schema must not be blank" }
        require(version >= 1) { "version must be >= 1" }
    }
}

/**
 * Fixture validation outcomes.
 */
sealed interface FixtureValidation {
    data object Valid : FixtureValidation
    data class Invalid(val reasons: List<String>) : FixtureValidation
}

/**
 * Validates fixtures before test execution.
 *
 * Phase 30 (OB-P30-REQ-002): invalid fixtures fail fast, before any
 * test logic runs.
 */
object FixtureValidator {

    /**
     * Validate a fixture. [knownSchemas] maps schema name → supported
     * versions.
     */
    fun validate(
        fixture: TestFixture,
        knownSchemas: Map<String, IntRange>,
    ): FixtureValidation {
        val reasons = mutableListOf<String>()
        val versions = knownSchemas[fixture.schema]
        if (versions == null) {
            reasons.add("unknown schema: ${fixture.schema}")
        } else if (fixture.version !in versions) {
            reasons.add(
                "unsupported version ${fixture.version} for schema ${fixture.schema}; " +
                    "supported: $versions",
            )
        }
        if (fixture.provenance == FixtureProvenance.REAL_CAPTURE &&
            fixture.payload["consent"] != true
        ) {
            reasons.add("real captures require explicit consent metadata")
        }
        return if (reasons.isEmpty()) FixtureValidation.Valid
        else FixtureValidation.Invalid(reasons)
    }
}

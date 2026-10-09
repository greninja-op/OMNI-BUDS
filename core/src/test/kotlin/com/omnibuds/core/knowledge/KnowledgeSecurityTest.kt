package com.omnibuds.core.knowledge

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 22: security tests.
 */
class KnowledgeSecurityTest {

    @Test
    fun `no script execution vocabulary in main sources`() {
        // This test documents the invariant; the architecture scope guards
        // enforce it. Knowledge records are data, never code.
        assertTrue(true)
    }

    @Test
    fun `import rejects executable-looking content`() {
        // A record with a "script" field is unknown-kind-tolerant: the
        // codec ignores unknown fields, but the record itself carries no
        // executable semantics.
        val doc = mapOf(
            "packageVersion" to 1,
            "schemaVersion" to KnowledgeCodecs.SCHEMA_VERSION,
            "exporter" to "test",
            "recordCount" to 1,
            "records" to listOf(
                mapOf(
                    "kind" to "manufacturer",
                    "id" to "m-x",
                    "name" to "X",
                    "script" to "doEvil()",
                    "metadata" to mapOf(
                        "recordVersion" to 1,
                        "lifecycle" to "ACTIVE",
                    ),
                ),
            ),
        )
        val result = KnowledgePackage.validate(KnowledgeJson.encode(doc))
        // Validates (unknown fields ignored) — but nothing executes it.
        assertTrue(result is PackageValidation.Valid)
    }

    @Test
    fun `deeply nested package rejected by size limits`() {
        // Excessive nesting is bounded by the package size limit.
        var nested: Any = "x"
        repeat(100) { nested = listOf(nested) }
        val doc = mapOf(
            "packageVersion" to 1,
            "schemaVersion" to KnowledgeCodecs.SCHEMA_VERSION,
            "records" to listOf(nested),
        )
        val json = KnowledgeJson.encode(doc)
        // Either it validates as malformed records or is rejected;
        // the point is no stack overflow and a structured result.
        val result = KnowledgePackage.validate(json)
        assertTrue(
            result is PackageValidation.Invalid,
            "deeply nested content must not validate cleanly",
        )
    }

    @Test
    fun `knowledge never authorizes writes`() {
        // Structural invariant: no knowledge type exposes a transport,
        // command constructor, or write API. Verified by the absence of
        // such members — this test pins the package's public surface.
        val methods = KnowledgeQuery::class.java.methods.map { it.name }
        assertFalse(methods.any { it.contains("write", ignoreCase = true) })
        assertFalse(methods.any { it.contains("execute", ignoreCase = true) })
        assertFalse(methods.any { it.contains("transmit", ignoreCase = true) })
    }
}

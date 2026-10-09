package com.omnibuds.core.validation

import com.omnibuds.core.device.DeviceIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Phase 14 (§21H, OB-P14-REQ-015): the deterministic aggregation policy.
 */
class AggregationPolicyTest {

    private val device = DeviceIdentity(displayName = "Test Buds")
    private var counter = 0

    private fun result(
        status: ValidationStatus,
        severity: ValidationSeverity = ValidationSeverity.INFO,
    ) = ValidationResult(
        validationId = "P14-TEST-${++counter}",
        device = device,
        sessionGeneration = 0L,
        category = ValidationCategory.TRANSPORT,
        status = status,
        severity = severity,
        reason = "test",
    )

    @Test
    fun `all valid aggregates to valid`() {
        val results = listOf(
            result(ValidationStatus.VALID),
            result(ValidationStatus.VALID),
        )
        assertEquals(ValidationStatus.VALID, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `hard violation prevents valid`() {
        val results = listOf(
            result(ValidationStatus.VALID),
            result(ValidationStatus.INVALID, ValidationSeverity.ERROR),
        )
        assertEquals(ValidationStatus.INVALID, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `critical violation prevents valid`() {
        val results = listOf(
            result(ValidationStatus.VALID),
            result(ValidationStatus.INVALID, ValidationSeverity.CRITICAL),
        )
        assertEquals(ValidationStatus.INVALID, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `warning-level invalid becomes inconclusive`() {
        val results = listOf(
            result(ValidationStatus.VALID),
            result(ValidationStatus.INVALID, ValidationSeverity.WARNING),
        )
        assertEquals(ValidationStatus.INCONCLUSIVE, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `conflict stays visible`() {
        val results = listOf(
            result(ValidationStatus.VALID),
            result(ValidationStatus.CONFLICT, ValidationSeverity.WARNING),
        )
        assertEquals(ValidationStatus.CONFLICT, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `conflict outranks stale`() {
        val results = listOf(
            result(ValidationStatus.STALE, ValidationSeverity.WARNING),
            result(ValidationStatus.CONFLICT, ValidationSeverity.WARNING),
        )
        assertEquals(ValidationStatus.CONFLICT, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `stale is reported`() {
        val results = listOf(
            result(ValidationStatus.VALID),
            result(ValidationStatus.STALE, ValidationSeverity.WARNING),
        )
        assertEquals(ValidationStatus.STALE, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `valid plus inconclusive retains uncertainty`() {
        val results = listOf(
            result(ValidationStatus.VALID),
            result(ValidationStatus.INCONCLUSIVE),
        )
        assertEquals(ValidationStatus.INCONCLUSIVE, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `valid plus not-observable retains uncertainty`() {
        val results = listOf(
            result(ValidationStatus.VALID),
            result(ValidationStatus.NOT_OBSERVABLE),
        )
        assertEquals(ValidationStatus.INCONCLUSIVE, ValidationAggregator.aggregate(results))
    }

    @Test
    fun `hard violation outranks conflict`() {
        val results = listOf(
            result(ValidationStatus.CONFLICT, ValidationSeverity.WARNING),
            result(ValidationStatus.INVALID, ValidationSeverity.ERROR),
        )
        assertEquals(ValidationStatus.INVALID, ValidationAggregator.aggregate(results))
    }
}

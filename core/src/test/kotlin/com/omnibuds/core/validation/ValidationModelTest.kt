package com.omnibuds.core.validation

import com.omnibuds.core.device.DeviceIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Phase 14 (§21): result model invariants and status semantics.
 */
class ValidationModelTest {

    private val device = DeviceIdentity(displayName = "Test Buds")

    private fun result(
        status: ValidationStatus,
        severity: ValidationSeverity,
    ) = ValidationResult(
        validationId = "P14-TEST-001",
        device = device,
        sessionGeneration = 0L,
        category = ValidationCategory.TRANSPORT,
        status = status,
        severity = severity,
        reason = "test",
    )

    @Test
    fun `all statuses are distinct`() {
        assertEquals(7, ValidationStatus.entries.size)
    }

    @Test
    fun `all severities are distinct`() {
        assertEquals(4, ValidationSeverity.entries.size)
    }

    @Test
    fun `all categories are distinct`() {
        assertEquals(7, ValidationCategory.entries.size)
    }

    @Test
    fun `invalid cannot be info severity`() {
        assertThrows<IllegalArgumentException> {
            result(ValidationStatus.INVALID, ValidationSeverity.INFO)
        }
    }

    @Test
    fun `conflict cannot be info severity`() {
        assertThrows<IllegalArgumentException> {
            result(ValidationStatus.CONFLICT, ValidationSeverity.INFO)
        }
    }

    @Test
    fun `inconclusive is not invalid`() {
        val r = result(ValidationStatus.INCONCLUSIVE, ValidationSeverity.INFO)
        assertEquals(ValidationStatus.INCONCLUSIVE, r.status)
    }

    @Test
    fun `blank validation id is rejected`() {
        assertThrows<IllegalArgumentException> {
            ValidationResult(
                validationId = "",
                device = device,
                sessionGeneration = 0L,
                category = ValidationCategory.CODEC,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                reason = "test",
            )
        }
    }

    @Test
    fun `blank reason is rejected`() {
        assertThrows<IllegalArgumentException> {
            ValidationResult(
                validationId = "P14-TEST-001",
                device = device,
                sessionGeneration = 0L,
                category = ValidationCategory.CODEC,
                status = ValidationStatus.VALID,
                severity = ValidationSeverity.INFO,
                reason = "",
            )
        }
    }
}

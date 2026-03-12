package com.zyntasolutions.zyntapos.common.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S3-9: Comprehensive tests for [ValidationScope] — the shared validation DSL.
 * Target: >80% coverage of all validation methods.
 */
class ValidationScopeTest {

    // ── requireNotBlank ─────────────────────────────────────────────────

    @Test
    fun `requireNotBlank passes for non-blank string`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "hello")
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNotBlank fails for null`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", null)
        }.validate()
        assertEquals(1, errors.size)
        assertEquals("name must not be blank", errors[0])
    }

    @Test
    fun `requireNotBlank fails for empty string`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "")
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireNotBlank fails for whitespace-only string`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "   ")
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireLength ───────────────────────────────────────────────────

    @Test
    fun `requireLength passes for string within range`() {
        val errors = ValidationScope().apply {
            requireLength("name", "hello", 1, 10)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireLength passes at exact min boundary`() {
        val errors = ValidationScope().apply {
            requireLength("name", "a", 1, 10)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireLength passes at exact max boundary`() {
        val errors = ValidationScope().apply {
            requireLength("name", "a".repeat(10), 1, 10)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireLength fails when string too short`() {
        val errors = ValidationScope().apply {
            requireLength("name", "", 1, 10)
        }.validate()
        assertEquals(1, errors.size)
        assertTrue("between 1 and 10" in errors[0])
    }

    @Test
    fun `requireLength fails when string too long`() {
        val errors = ValidationScope().apply {
            requireLength("name", "a".repeat(11), 1, 10)
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireLength treats null as length 0`() {
        val errors = ValidationScope().apply {
            requireLength("name", null, 1, 10)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireMaxLength ────────────────────────────────────────────────

    @Test
    fun `requireMaxLength passes for null`() {
        val errors = ValidationScope().apply {
            requireMaxLength("name", null, 10)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireMaxLength passes for string at max`() {
        val errors = ValidationScope().apply {
            requireMaxLength("name", "a".repeat(10), 10)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireMaxLength fails for string over max`() {
        val errors = ValidationScope().apply {
            requireMaxLength("name", "a".repeat(11), 10)
        }.validate()
        assertEquals(1, errors.size)
        assertTrue("at most 10" in errors[0])
    }

    // ── requirePositive ─────────────────────────────────────────────────

    @Test
    fun `requirePositive passes for positive value`() {
        val errors = ValidationScope().apply {
            requirePositive("price", 1.5)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requirePositive fails for zero`() {
        val errors = ValidationScope().apply {
            requirePositive("price", 0.0)
        }.validate()
        assertEquals(1, errors.size)
        assertEquals("price must be positive", errors[0])
    }

    @Test
    fun `requirePositive fails for negative`() {
        val errors = ValidationScope().apply {
            requirePositive("price", -1.0)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireNonNegative (Int) ────────────────────────────────────────

    @Test
    fun `requireNonNegative Int passes for zero`() {
        val errors = ValidationScope().apply {
            requireNonNegative("count", 0)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNonNegative Int passes for positive`() {
        val errors = ValidationScope().apply {
            requireNonNegative("count", 5)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNonNegative Int fails for negative`() {
        val errors = ValidationScope().apply {
            requireNonNegative("count", -1)
        }.validate()
        assertEquals(1, errors.size)
        assertEquals("count must be non-negative", errors[0])
    }

    // ── requireNonNegative (Long) ───────────────────────────────────────

    @Test
    fun `requireNonNegative Long passes for zero`() {
        val errors = ValidationScope().apply {
            requireNonNegative("bytes", 0L)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNonNegative Long fails for negative`() {
        val errors = ValidationScope().apply {
            requireNonNegative("bytes", -1L)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireNonNegative (Double) ─────────────────────────────────────

    @Test
    fun `requireNonNegative Double passes for zero`() {
        val errors = ValidationScope().apply {
            requireNonNegative("amount", 0.0)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNonNegative Double fails for negative`() {
        val errors = ValidationScope().apply {
            requireNonNegative("amount", -0.01)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireUUID ─────────────────────────────────────────────────────

    @Test
    fun `requireUUID passes for valid UUID`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "550e8400-e29b-41d4-a716-446655440000")
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireUUID passes for uppercase UUID`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "550E8400-E29B-41D4-A716-446655440000")
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireUUID fails for null`() {
        val errors = ValidationScope().apply {
            requireUUID("id", null)
        }.validate()
        assertEquals(1, errors.size)
        assertEquals("id must be a valid UUID", errors[0])
    }

    @Test
    fun `requireUUID fails for blank`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "")
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireUUID fails for invalid format`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "not-a-uuid")
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireUUID fails for UUID without dashes`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "550e8400e29b41d4a716446655440000")
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireInRange (Double) ─────────────────────────────────────────

    @Test
    fun `requireInRange Double passes for value within range`() {
        val errors = ValidationScope().apply {
            requireInRange("tax", 10.0, 0.0, 100.0)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireInRange Double passes at boundaries`() {
        val errors = ValidationScope().apply {
            requireInRange("tax", 0.0, 0.0, 100.0)
            requireInRange("tax", 100.0, 0.0, 100.0)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireInRange Double fails below min`() {
        val errors = ValidationScope().apply {
            requireInRange("tax", -1.0, 0.0, 100.0)
        }.validate()
        assertEquals(1, errors.size)
        assertTrue("between 0.0 and 100.0" in errors[0])
    }

    @Test
    fun `requireInRange Double fails above max`() {
        val errors = ValidationScope().apply {
            requireInRange("tax", 101.0, 0.0, 100.0)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireInRange (Int) ────────────────────────────────────────────

    @Test
    fun `requireInRange Int passes for value within range`() {
        val errors = ValidationScope().apply {
            requireInRange("quantity", 5, 1, 100)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireInRange Int fails outside range`() {
        val errors = ValidationScope().apply {
            requireInRange("quantity", 0, 1, 100)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requirePattern ──────────────────────────────────────────────────

    @Test
    fun `requirePattern passes for matching pattern`() {
        val errors = ValidationScope().apply {
            requirePattern("email", "user@test.com", Regex("^.+@.+\\..+$"))
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requirePattern fails for non-matching pattern`() {
        val errors = ValidationScope().apply {
            requirePattern("email", "not-an-email", Regex("^.+@.+\\..+$"))
        }.validate()
        assertEquals(1, errors.size)
        assertEquals("email has invalid format", errors[0])
    }

    @Test
    fun `requirePattern uses custom message`() {
        val errors = ValidationScope().apply {
            requirePattern("code", "abc", Regex("^[0-9]+$"), "code must contain only digits")
        }.validate()
        assertEquals(1, errors.size)
        assertEquals("code must contain only digits", errors[0])
    }

    @Test
    fun `requirePattern fails for null value`() {
        val errors = ValidationScope().apply {
            requirePattern("code", null, Regex("^[0-9]+$"))
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireNotEmpty ─────────────────────────────────────────────────

    @Test
    fun `requireNotEmpty passes for non-empty collection`() {
        val errors = ValidationScope().apply {
            requireNotEmpty("items", listOf("a"))
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNotEmpty fails for empty list`() {
        val errors = ValidationScope().apply {
            requireNotEmpty("items", emptyList<String>())
        }.validate()
        assertEquals(1, errors.size)
        assertEquals("items must not be empty", errors[0])
    }

    @Test
    fun `requireNotEmpty fails for null`() {
        val errors = ValidationScope().apply {
            requireNotEmpty("items", null)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireMaxSize ──────────────────────────────────────────────────

    @Test
    fun `requireMaxSize passes for collection within limit`() {
        val errors = ValidationScope().apply {
            requireMaxSize("tags", listOf("a", "b"), 5)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireMaxSize passes for null`() {
        val errors = ValidationScope().apply {
            requireMaxSize("tags", null, 5)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireMaxSize passes at exact limit`() {
        val errors = ValidationScope().apply {
            requireMaxSize("tags", listOf("a", "b", "c"), 3)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireMaxSize fails over limit`() {
        val errors = ValidationScope().apply {
            requireMaxSize("tags", listOf("a", "b", "c", "d"), 3)
        }.validate()
        assertEquals(1, errors.size)
        assertTrue("at most 3" in errors[0])
    }

    // ── Multiple errors ─────────────────────────────────────────────────

    @Test
    fun `validate collects multiple errors`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "")
            requirePositive("price", -1.0)
            requireUUID("id", "bad")
        }.validate()
        assertEquals(3, errors.size)
    }

    @Test
    fun `validate returns empty list when no errors`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "hello")
            requirePositive("price", 10.0)
            requireUUID("id", "550e8400-e29b-41d4-a716-446655440000")
        }.validate()
        assertTrue(errors.isEmpty())
    }
}

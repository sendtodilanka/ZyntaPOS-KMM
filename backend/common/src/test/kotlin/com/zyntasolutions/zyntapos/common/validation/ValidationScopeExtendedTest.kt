package com.zyntasolutions.zyntapos.common.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C9: Extended tests for [ValidationScope] — covers edge cases not in the
 * original ValidationScopeTest, including boundary conditions, combined
 * validators, and validation error ordering.
 */
class ValidationScopeExtendedTest {

    // ── requireNotBlank edge cases ────────────────────────────────────────

    @Test
    fun `requireNotBlank passes for single character`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "a")
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNotBlank fails for tab-only string`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "\t")
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireNotBlank fails for newline-only string`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "\n")
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireNotBlank passes for string with leading whitespace`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "  hello")
        }.validate()
        assertTrue(errors.isEmpty())
    }

    // ── requireLength edge cases ──────────────────────────────────────────

    @Test
    fun `requireLength passes for zero min with empty string`() {
        val errors = ValidationScope().apply {
            requireLength("name", "", 0, 10)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireLength with same min and max acts as exact length check`() {
        val errors = ValidationScope().apply {
            requireLength("code", "ABCDE", 5, 5)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireLength with same min and max fails for different length`() {
        val errors = ValidationScope().apply {
            requireLength("code", "ABC", 5, 5)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireMaxLength edge cases ───────────────────────────────────────

    @Test
    fun `requireMaxLength passes for empty string`() {
        val errors = ValidationScope().apply {
            requireMaxLength("name", "", 10)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireMaxLength with max 0 fails for any non-empty string`() {
        val errors = ValidationScope().apply {
            requireMaxLength("name", "a", 0)
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requirePositive edge cases ────────────────────────────────────────

    @Test
    fun `requirePositive passes for very small positive value`() {
        val errors = ValidationScope().apply {
            requirePositive("price", 0.001)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requirePositive passes for large value`() {
        val errors = ValidationScope().apply {
            requirePositive("price", 999999.99)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    // ── requireNonNegative combined overloads ──────────────────────────────

    @Test
    fun `requireNonNegative all three overloads pass for zero`() {
        val errors = ValidationScope().apply {
            requireNonNegative("intVal", 0)
            requireNonNegative("longVal", 0L)
            requireNonNegative("doubleVal", 0.0)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNonNegative all three overloads fail for negative`() {
        val errors = ValidationScope().apply {
            requireNonNegative("intVal", -1)
            requireNonNegative("longVal", -1L)
            requireNonNegative("doubleVal", -1.0)
        }.validate()
        assertEquals(3, errors.size)
    }

    @Test
    fun `requireNonNegative Long passes for large positive`() {
        val errors = ValidationScope().apply {
            requireNonNegative("seq", Long.MAX_VALUE)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    // ── requireUUID edge cases ────────────────────────────────────────────

    @Test
    fun `requireUUID fails for UUID with extra characters`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "550e8400-e29b-41d4-a716-446655440000-extra")
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireUUID fails for UUID with missing section`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "550e8400-e29b-41d4-a716")
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireUUID fails for UUID with invalid hex character`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "550g8400-e29b-41d4-a716-446655440000")
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireUUID passes for mixed-case UUID`() {
        val errors = ValidationScope().apply {
            requireUUID("id", "550E8400-e29b-41D4-a716-446655440000")
        }.validate()
        assertTrue(errors.isEmpty())
    }

    // ── requireInRange edge cases ─────────────────────────────────────────

    @Test
    fun `requireInRange Int at exact min boundary passes`() {
        val errors = ValidationScope().apply {
            requireInRange("qty", 1, 1, 100)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireInRange Int at exact max boundary passes`() {
        val errors = ValidationScope().apply {
            requireInRange("qty", 100, 1, 100)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireInRange Int one below min fails`() {
        val errors = ValidationScope().apply {
            requireInRange("qty", 0, 1, 100)
        }.validate()
        assertEquals(1, errors.size)
        assertTrue("between 1 and 100" in errors[0])
    }

    @Test
    fun `requireInRange Int one above max fails`() {
        val errors = ValidationScope().apply {
            requireInRange("qty", 101, 1, 100)
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireInRange Double with negative range`() {
        val errors = ValidationScope().apply {
            requireInRange("temp", -5.0, -10.0, 10.0)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    // ── requirePattern edge cases ─────────────────────────────────────────

    @Test
    fun `requirePattern passes for exact match`() {
        val errors = ValidationScope().apply {
            requirePattern("pin", "1234", Regex("^\\d{4}$"))
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requirePattern fails for partial match`() {
        val errors = ValidationScope().apply {
            requirePattern("pin", "12345", Regex("^\\d{4}$"))
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requirePattern with empty string`() {
        val errors = ValidationScope().apply {
            requirePattern("code", "", Regex("^.+$"))
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireNotEmpty edge cases ────────────────────────────────────────

    @Test
    fun `requireNotEmpty passes for set with elements`() {
        val errors = ValidationScope().apply {
            requireNotEmpty("tags", setOf("a", "b"))
        }.validate()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `requireNotEmpty fails for empty set`() {
        val errors = ValidationScope().apply {
            requireNotEmpty("tags", emptySet<String>())
        }.validate()
        assertEquals(1, errors.size)
    }

    // ── requireMaxSize edge cases ─────────────────────────────────────────

    @Test
    fun `requireMaxSize with max 0 fails for any non-empty collection`() {
        val errors = ValidationScope().apply {
            requireMaxSize("items", listOf("a"), 0)
        }.validate()
        assertEquals(1, errors.size)
    }

    @Test
    fun `requireMaxSize passes for empty collection`() {
        val errors = ValidationScope().apply {
            requireMaxSize("items", emptyList<String>(), 5)
        }.validate()
        assertTrue(errors.isEmpty())
    }

    // ── Error ordering ────────────────────────────────────────────────────

    @Test
    fun `validate returns errors in order they were added`() {
        val errors = ValidationScope().apply {
            requireNotBlank("first", "")
            requireNotBlank("second", "")
            requireNotBlank("third", "")
        }.validate()
        assertEquals(3, errors.size)
        assertTrue(errors[0].contains("first"))
        assertTrue(errors[1].contains("second"))
        assertTrue(errors[2].contains("third"))
    }

    // ── Complex validation scenarios ──────────────────────────────────────

    @Test
    fun `full product validation scenario`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "Widget")
            requireLength("name", "Widget", 1, 100)
            requirePositive("price", 9.99)
            requireNonNegative("stock", 0)
            requireUUID("categoryId", "550e8400-e29b-41d4-a716-446655440000")
            requireMaxLength("description", "A short description", 500)
            requireNotEmpty("tags", listOf("electronics"))
            requireMaxSize("tags", listOf("electronics"), 10)
        }.validate()
        assertTrue(errors.isEmpty(), "Valid product should produce no errors")
    }

    @Test
    fun `full product validation scenario with multiple failures`() {
        val errors = ValidationScope().apply {
            requireNotBlank("name", "")
            requirePositive("price", -5.0)
            requireNonNegative("stock", -1)
            requireUUID("categoryId", "invalid")
            requireNotEmpty("tags", emptyList<String>())
        }.validate()
        assertEquals(5, errors.size)
    }

    // ── Empty validation scope ────────────────────────────────────────────

    @Test
    fun `empty validation scope returns empty errors`() {
        val errors = ValidationScope().apply {
            // No validations added
        }.validate()
        assertTrue(errors.isEmpty())
    }

    // ── Validate returns immutable copy ───────────────────────────────────

    @Test
    fun `validate returns a new list each time`() {
        val scope = ValidationScope().apply {
            requireNotBlank("name", "")
        }
        val errors1 = scope.validate()
        val errors2 = scope.validate()
        assertEquals(errors1, errors2)
        assertTrue(errors1 !== errors2, "validate() should return a new list each call")
    }
}

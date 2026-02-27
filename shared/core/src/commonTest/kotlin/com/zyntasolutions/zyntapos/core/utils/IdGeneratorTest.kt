package com.zyntasolutions.zyntapos.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [IdGenerator].
 *
 * Verifies that all three generation methods produce correctly structured identifiers
 * and that uniqueness is maintained across successive calls.
 */
class IdGeneratorTest {

    // ── newId ─────────────────────────────────────────────────────────────────

    @Test
    fun `newId produces non-empty string`() {
        val id = IdGenerator.newId()
        assertTrue(id.isNotEmpty(), "newId() must not return an empty string")
    }

    @Test
    fun `newId produces 36-character UUID-formatted string`() {
        val id = IdGenerator.newId()
        assertEquals(36, id.length, "UUID v4 string must be 36 characters long, got: $id")
    }

    @Test
    fun `newId produces string with exactly 4 hyphens`() {
        val id = IdGenerator.newId()
        val hyphenCount = id.count { it == '-' }
        assertEquals(4, hyphenCount, "UUID v4 must contain exactly 4 hyphens, got: $id")
    }

    @Test
    fun `newId produces string in standard UUID segment lengths`() {
        // Standard UUID: 8-4-4-4-12
        val id = IdGenerator.newId()
        val segments = id.split("-")
        assertEquals(5, segments.size, "UUID must have 5 segments separated by hyphens: $id")
        assertEquals(8, segments[0].length, "First UUID segment must be 8 chars: $id")
        assertEquals(4, segments[1].length, "Second UUID segment must be 4 chars: $id")
        assertEquals(4, segments[2].length, "Third UUID segment must be 4 chars: $id")
        assertEquals(4, segments[3].length, "Fourth UUID segment must be 4 chars: $id")
        assertEquals(12, segments[4].length, "Fifth UUID segment must be 12 chars: $id")
    }

    @Test
    fun `newId produces only lowercase hexadecimal characters and hyphens`() {
        val id = IdGenerator.newId()
        val validChars = "0123456789abcdef-"
        assertTrue(
            id.all { it in validChars },
            "UUID must contain only lowercase hex digits and hyphens, got: $id",
        )
    }

    @Test
    fun `newId produces unique values on successive calls`() {
        val id1 = IdGenerator.newId()
        val id2 = IdGenerator.newId()
        assertNotEquals(id1, id2, "Two successive newId() calls must produce different IDs")
    }

    @Test
    fun `newId produces unique values across many calls`() {
        val ids = (1..20).map { IdGenerator.newId() }.toSet()
        assertEquals(20, ids.size, "All 20 generated IDs must be unique")
    }

    // ── newCompactId ──────────────────────────────────────────────────────────

    @Test
    fun `newCompactId produces 32-character string`() {
        val id = IdGenerator.newCompactId()
        assertEquals(32, id.length, "newCompactId() must produce a 32-character string, got: $id")
    }

    @Test
    fun `newCompactId produces non-empty string`() {
        val id = IdGenerator.newCompactId()
        assertTrue(id.isNotEmpty(), "newCompactId() must not return an empty string")
    }

    @Test
    fun `newCompactId contains no hyphens`() {
        val id = IdGenerator.newCompactId()
        assertFalse(id.contains('-'), "newCompactId() must not contain hyphens, got: $id")
    }

    @Test
    fun `newCompactId produces only lowercase hexadecimal characters`() {
        val id = IdGenerator.newCompactId()
        val validChars = "0123456789abcdef"
        assertTrue(
            id.all { it in validChars },
            "newCompactId() must contain only lowercase hex digits, got: $id",
        )
    }

    @Test
    fun `newCompactId produces unique values on successive calls`() {
        val id1 = IdGenerator.newCompactId()
        val id2 = IdGenerator.newCompactId()
        assertNotEquals(id1, id2, "Two successive newCompactId() calls must produce different IDs")
    }

    // ── newPrefixedId ─────────────────────────────────────────────────────────

    @Test
    fun `newPrefixedId starts with provided prefix followed by hyphen`() {
        val id = IdGenerator.newPrefixedId("ORD")
        assertTrue(id.startsWith("ORD-"), "ID must start with 'ORD-', got: $id")
    }

    @Test
    fun `newPrefixedId with INV prefix starts with INV-`() {
        val id = IdGenerator.newPrefixedId("INV")
        assertTrue(id.startsWith("INV-"), "ID must start with 'INV-', got: $id")
    }

    @Test
    fun `newPrefixedId with PRD prefix starts with PRD-`() {
        val id = IdGenerator.newPrefixedId("PRD")
        assertTrue(id.startsWith("PRD-"), "ID must start with 'PRD-', got: $id")
    }

    @Test
    fun `newPrefixedId produces non-empty string`() {
        val id = IdGenerator.newPrefixedId("TEST")
        assertTrue(id.isNotEmpty(), "newPrefixedId() must not return an empty string")
    }

    @Test
    fun `newPrefixedId suffix contains UUID-derived segments with hyphens`() {
        // Expected format: PREFIX-{seg1}-{seg2}-{seg3} where segments come from first 3 UUID parts
        val id = IdGenerator.newPrefixedId("ORD")
        val withoutPrefix = id.removePrefix("ORD-")
        assertTrue(withoutPrefix.isNotEmpty(), "Suffix after prefix must be non-empty: $id")
        // Should have exactly 2 more hyphens (3 UUID segments: 8-4-4)
        val hyphenCount = withoutPrefix.count { it == '-' }
        assertEquals(2, hyphenCount, "Suffix must contain 2 hyphens (3 UUID segments), got: $id")
    }

    @Test
    fun `newPrefixedId produces unique values on successive calls`() {
        val id1 = IdGenerator.newPrefixedId("ORD")
        val id2 = IdGenerator.newPrefixedId("ORD")
        assertNotEquals(id1, id2, "Two successive newPrefixedId() calls with same prefix must produce different IDs")
    }

    @Test
    fun `newPrefixedId different prefixes produce different starts`() {
        val ordId = IdGenerator.newPrefixedId("ORD")
        val invId = IdGenerator.newPrefixedId("INV")
        assertTrue(ordId.startsWith("ORD-"), "ORD-prefixed ID must start with ORD-")
        assertTrue(invId.startsWith("INV-"), "INV-prefixed ID must start with INV-")
    }
}

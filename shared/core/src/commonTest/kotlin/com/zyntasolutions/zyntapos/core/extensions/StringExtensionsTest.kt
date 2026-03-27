package com.zyntasolutions.zyntapos.core.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — StringExtensions Unit Tests (commonTest)
 *
 * Validates string validation, transformation, and security helpers used throughout the POS.
 *
 * Coverage:
 *  A. isValidEmail validates correct and incorrect email formats
 *  B. isValidPhone validates correct and incorrect phone formats
 *  C. truncate shortens strings and appends ellipsis
 *  D. toTitleCase capitalises first letter of each word
 *  E. maskSensitive hides all but leading visible characters
 *  F. nullIfBlank returns null for blank strings, trimmed value otherwise
 *  G. ensurePrefix adds prefix only when absent
 *  H. isNumeric returns true for digit-only strings
 */
class StringExtensionsTest {

    // ── isValidEmail ──────────────────────────────────────────────────────────

    @Test
    fun `A - isValidEmail accepts valid email addresses`() {
        assertTrue("admin@zyntapos.com".isValidEmail())
        assertTrue("user.name+tag@example.co.uk".isValidEmail())
        assertTrue("a@b.io".isValidEmail())
        assertTrue("test123@domain.org".isValidEmail())
    }

    @Test
    fun `A2 - isValidEmail rejects invalid email addresses`() {
        assertFalse("".isValidEmail())
        assertFalse("   ".isValidEmail())
        assertFalse("notanemail".isValidEmail())
        assertFalse("@domain.com".isValidEmail())
        assertFalse("user@".isValidEmail())
        assertFalse("user@domain".isValidEmail())  // no TLD
    }

    // ── isValidPhone ──────────────────────────────────────────────────────────

    @Test
    fun `B - isValidPhone accepts valid phone numbers`() {
        assertTrue("+1-800-555-1234".isValidPhone())
        assertTrue("0771234567".isValidPhone())       // 10 digits
        assertTrue("+94771234567".isValidPhone())      // Sri Lanka international
        assertTrue("(555) 123-4567".isValidPhone())
        assertTrue("1234567".isValidPhone())           // minimum 7 digits
    }

    @Test
    fun `B2 - isValidPhone rejects invalid phone numbers`() {
        assertFalse("".isValidPhone())
        assertFalse("   ".isValidPhone())
        assertFalse("12345".isValidPhone())            // too short (5 digits)
        assertFalse("abc1234567".isValidPhone())       // non-digit chars
    }

    // ── truncate ──────────────────────────────────────────────────────────────

    @Test
    fun `C - truncate shortens long string and appends ellipsis`() {
        assertEquals("He...", "Hello, World!".truncate(5))
    }

    @Test
    fun `C2 - truncate does not modify string within maxLength`() {
        assertEquals("Hi", "Hi".truncate(10))
    }

    @Test
    fun `C3 - truncate returns full string when exactly at maxLength`() {
        assertEquals("Hello", "Hello".truncate(5))
    }

    @Test
    fun `C4 - truncate supports custom ellipsis`() {
        assertEquals("Hel~", "Hello World".truncate(4, "~"))
    }

    // ── toTitleCase ───────────────────────────────────────────────────────────

    @Test
    fun `D - toTitleCase capitalises first letter of each word`() {
        assertEquals("Hello World", "hello world".toTitleCase())
    }

    @Test
    fun `D2 - toTitleCase handles all-uppercase input`() {
        assertEquals("Hello World", "HELLO WORLD".toTitleCase())
    }

    @Test
    fun `D3 - toTitleCase handles single word`() {
        assertEquals("Kotlin", "kotlin".toTitleCase())
    }

    @Test
    fun `D4 - toTitleCase preserves already-correct title case`() {
        assertEquals("Alice Smith", "Alice Smith".toTitleCase())
    }

    // ── maskSensitive ─────────────────────────────────────────────────────────

    @Test
    fun `E - maskSensitive shows first 2 chars and masks the rest`() {
        assertEquals("12••••••••", "1234567890".maskSensitive())
    }

    @Test
    fun `E2 - maskSensitive with visibleChars=3 shows first 3`() {
        assertEquals("adm" + "•".repeat(15), "admin@zyntapos.com".maskSensitive(3))
    }

    @Test
    fun `E3 - maskSensitive masks entire string if shorter than visibleChars`() {
        assertEquals("•", "x".maskSensitive(visibleChars = 2))
    }

    @Test
    fun `E4 - maskSensitive uses custom mask character`() {
        assertEquals("12*******", "123456789".maskSensitive(maskChar = '*'))
    }

    // ── nullIfBlank ───────────────────────────────────────────────────────────

    @Test
    fun `F - nullIfBlank returns null for blank string`() {
        assertNull("".nullIfBlank())
        assertNull("   ".nullIfBlank())
        assertNull("\t\n".nullIfBlank())
    }

    @Test
    fun `F2 - nullIfBlank returns trimmed value for non-blank string`() {
        assertEquals("hello", "  hello  ".nullIfBlank())
        assertEquals("value", "value".nullIfBlank())
    }

    // ── ensurePrefix ──────────────────────────────────────────────────────────

    @Test
    fun `G - ensurePrefix adds prefix when absent`() {
        assertEquals("https://example.com", "example.com".ensurePrefix("https://"))
    }

    @Test
    fun `G2 - ensurePrefix does not duplicate existing prefix`() {
        assertEquals("https://example.com", "https://example.com".ensurePrefix("https://"))
    }

    // ── isNumeric ─────────────────────────────────────────────────────────────

    @Test
    fun `H - isNumeric returns true for digit-only strings`() {
        assertTrue("123".isNumeric())
        assertTrue("0".isNumeric())
        assertTrue("9876543210".isNumeric())
    }

    @Test
    fun `H2 - isNumeric returns false for strings with non-digit characters`() {
        assertFalse("".isNumeric())
        assertFalse("12a3".isNumeric())
        assertFalse("1.23".isNumeric())
        assertFalse(" 123".isNumeric())
        assertFalse("abc".isNumeric())
    }
}

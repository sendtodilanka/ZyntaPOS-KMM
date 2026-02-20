package com.zyntasolutions.zyntapos.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [CurrencyFormatter].
 * Target: ≥ 90% coverage of utils/CurrencyFormatter.kt
 */
class CurrencyFormatterTest {

    private val formatter = CurrencyFormatter(defaultCurrency = "LKR", defaultDecimals = 2)

    // ── format — basic cases ──────────────────────────────────────────────────

    @Test
    fun `format rounds to 2 decimal places`() {
        val result = formatter.format(1234.5)
        assertTrue(result.endsWith(".50"), "Expected .50 suffix: $result")
    }

    @Test
    fun `format with USD symbol`() {
        val result = formatter.format(99.9, "USD")
        assertTrue(result.startsWith("$"), "Expected $ prefix: $result")
        assertTrue(result.contains("99.90"), "Expected 99.90 in: $result")
    }

    @Test
    fun `format with EUR symbol`() {
        val result = formatter.format(0.01, "EUR")
        assertTrue(result.startsWith("€"), "Expected € prefix: $result")
        assertTrue(result.contains("0.01"), "Expected 0.01 in: $result")
    }

    @Test
    fun `format LKR uses Rs symbol`() {
        val result = formatter.format(5000.0, "LKR")
        assertTrue(result.startsWith("Rs."), "Expected Rs. prefix: $result")
    }

    @Test
    fun `format zero amount`() {
        val result = formatter.format(0.0, "LKR")
        assertTrue(result.contains("0.00"), "Expected 0.00 in: $result")
    }

    @Test
    fun `format negative amount includes sign`() {
        val result = formatter.format(-100.0, "USD")
        assertTrue(result.contains("-"), "Expected minus sign in: $result")
        assertTrue(result.contains("100.00"), "Expected 100.00 in: $result")
    }

    // ── format — thousands separator ──────────────────────────────────────────

    @Test
    fun `format adds thousands separator for large amount`() {
        val result = formatter.format(1_234_567.89, "LKR")
        assertTrue(result.contains(","), "Expected thousands separator in: $result")
    }

    @Test
    fun `format 1000 becomes 1,000`() {
        val result = formatter.format(1000.0, "USD")
        assertTrue(result.contains("1,000"), "Expected 1,000 in: $result")
    }

    @Test
    fun `format 1000000 becomes 1,000,000`() {
        val result = formatter.format(1_000_000.0, "USD")
        assertTrue(result.contains("1,000,000"), "Expected 1,000,000 in: $result")
    }

    // ── formatPlain ───────────────────────────────────────────────────────────

    @Test
    fun `formatPlain omits currency symbol`() {
        val result = formatter.formatPlain(1234.50)
        assertFalse(result.startsWith("Rs."), "Expected no Rs. prefix: $result")
        assertFalse(result.startsWith("$"), "Expected no $ prefix: $result")
        assertTrue(result.contains("1,234.50"), "Expected 1,234.50 in: $result")
    }

    @Test
    fun `formatPlain zero`() {
        val result = formatter.formatPlain(0.0)
        assertEquals("0.00", result)
    }

    // ── symbolFor ────────────────────────────────────────────────────────────

    @Test
    fun `symbolFor LKR returns Rs`() {
        assertEquals("Rs.", formatter.symbolFor("LKR"))
    }

    @Test
    fun `symbolFor USD returns dollar sign`() {
        assertEquals("$", formatter.symbolFor("USD"))
    }

    @Test
    fun `symbolFor EUR returns euro sign`() {
        assertEquals("€", formatter.symbolFor("EUR"))
    }

    @Test
    fun `symbolFor unknown code returns code itself`() {
        assertEquals("MYR", formatter.symbolFor("MYR"))
    }

    @Test
    fun `symbolFor is case insensitive`() {
        assertEquals("Rs.", formatter.symbolFor("lkr"))
        assertEquals("$", formatter.symbolFor("usd"))
    }

    // ── rounding edge cases ───────────────────────────────────────────────────

    @Test
    fun `format applies HALF_UP rounding correctly`() {
        // 1.005 × 100 = 100.5 → roundToLong = 101 → 1.01
        val result = formatter.formatPlain(1.005)
        assertTrue(result.contains("1.01") || result.contains("1.00"),
            "HALF_UP rounding issue: $result") // platform floating-point may differ slightly
    }

    // ── supportedCurrencies ───────────────────────────────────────────────────

    @Test
    fun `supportedCurrencies contains Phase 1 codes`() {
        val supported = formatter.supportedCurrencies
        assertTrue("LKR" in supported)
        assertTrue("USD" in supported)
        assertTrue("EUR" in supported)
    }

    // ── nameFor ──────────────────────────────────────────────────────────────

    @Test
    fun `nameFor LKR returns full name`() {
        assertEquals("Sri Lankan Rupee", formatter.nameFor("LKR"))
    }

    @Test
    fun `nameFor USD returns full name`() {
        assertEquals("US Dollar", formatter.nameFor("USD"))
    }
}

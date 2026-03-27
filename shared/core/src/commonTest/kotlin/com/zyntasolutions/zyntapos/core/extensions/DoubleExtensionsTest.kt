package com.zyntasolutions.zyntapos.core.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ZyntaPOS — DoubleExtensions Unit Tests (commonTest)
 *
 * Validates financial formatting and rounding functions used across the POS system.
 * All monetary calculations use HALF_UP semantics.
 *
 * Coverage:
 *  A. toCurrencyString formats with correct currency symbol
 *  B. toCurrencyString adds thousands separator for large values
 *  C. toCurrencyString zero-pads fractional part
 *  D. toCurrencyString handles unknown currency code as symbol
 *  E. roundToCurrency rounds to 2 decimal places HALF_UP
 *  F. roundToCurrency with 0 decimals rounds to nearest integer
 *  G. toPercentage with asRate false formats direct percentage
 *  H. toPercentage with asRate true multiplies by 100
 *  I. isPositive returns true only for values > 0
 *  J. isNonNegative returns true for zero and positive values
 */
class DoubleExtensionsTest {

    // ── toCurrencyString ──────────────────────────────────────────────────────

    @Test
    fun `A - toCurrencyString formats LKR correctly`() {
        val result = 1234.5.toCurrencyString("LKR")
        assertEquals("Rs. 1,234.50", result)
    }

    @Test
    fun `A2 - toCurrencyString formats USD correctly`() {
        val result = 99.9.toCurrencyString("USD")
        assertEquals("$ 99.90", result)
    }

    @Test
    fun `A3 - toCurrencyString formats EUR correctly`() {
        val result = 50.0.toCurrencyString("EUR")
        assertEquals("€ 50.00", result)
    }

    @Test
    fun `A4 - toCurrencyString formats GBP correctly`() {
        val result = 10.0.toCurrencyString("GBP")
        assertEquals("£ 10.00", result)
    }

    @Test
    fun `B - toCurrencyString adds thousands separator for large values`() {
        val result = 1234567.89.toCurrencyString("USD")
        assertEquals("$ 1,234,567.89", result)
    }

    @Test
    fun `B2 - toCurrencyString adds thousands separator for one million`() {
        val result = 1000000.0.toCurrencyString("LKR")
        assertEquals("Rs. 1,000,000.00", result)
    }

    @Test
    fun `C - toCurrencyString zero-pads single fractional digit`() {
        val result = 5.5.toCurrencyString("USD")
        assertEquals("$ 5.50", result)
    }

    @Test
    fun `C2 - toCurrencyString shows double zero for whole number`() {
        val result = 100.0.toCurrencyString("USD")
        assertEquals("$ 100.00", result)
    }

    @Test
    fun `D - toCurrencyString uses unknown currency code as symbol`() {
        val result = 10.0.toCurrencyString("XYZ")
        assertEquals("XYZ 10.00", result)
    }

    // ── roundToCurrency ───────────────────────────────────────────────────────

    @Test
    fun `E - roundToCurrency rounds 2994 millis to 2 decimals`() {
        assertEquals(2.99, 2.994.roundToCurrency())
    }

    @Test
    fun `E2 - roundToCurrency rounds 2995 millis HALF_UP`() {
        assertEquals(3.0, 2.995.roundToCurrency())
    }

    @Test
    fun `E3 - roundToCurrency preserves whole value`() {
        assertEquals(1.0, 1.0.roundToCurrency())
    }

    @Test
    fun `E4 - roundToCurrency preserves already-rounded value`() {
        assertEquals(123.45, 123.45.roundToCurrency())
    }

    @Test
    fun `F - roundToCurrency with 0 decimals rounds halves up`() {
        assertEquals(5.0, 4.5.roundToCurrency(0))
    }

    @Test
    fun `F2 - roundToCurrency with 0 decimals truncates below half`() {
        assertEquals(4.0, 4.4.roundToCurrency(0))
    }

    @Test
    fun `F3 - roundToCurrency with 0 decimals rounds 06 to 1`() {
        assertEquals(1.0, 0.6.roundToCurrency(0))
    }

    // ── toPercentage ──────────────────────────────────────────────────────────

    @Test
    fun `G - toPercentage asRate false formats 15 as 15 percent`() {
        assertEquals("15.00 %", 15.0.toPercentage(asRate = false))
    }

    @Test
    fun `G2 - toPercentage asRate false formats 8 and a half as 8 point 50 percent`() {
        assertEquals("8.50 %", 8.5.toPercentage(asRate = false))
    }

    @Test
    fun `G3 - toPercentage asRate false formats 100 as 100 percent`() {
        assertEquals("100.00 %", 100.0.toPercentage(asRate = false))
    }

    @Test
    fun `H - toPercentage asRate true converts fractional rate 015 to 15 percent`() {
        assertEquals("15.00 %", 0.15.toPercentage(asRate = true))
    }

    @Test
    fun `H2 - toPercentage asRate true converts fractional rate 0085 to 8 point 50 percent`() {
        assertEquals("8.50 %", 0.085.toPercentage(asRate = true))
    }

    // ── isPositive / isNonNegative ─────────────────────────────────────────────

    @Test
    fun `I - isPositive returns true for values greater than zero`() {
        assertTrue(1.0.isPositive())
        assertTrue(0.01.isPositive())
        assertTrue(1000.0.isPositive())
    }

    @Test
    fun `I2 - isPositive returns false for zero and negative values`() {
        assertFalse(0.0.isPositive())
        assertFalse((-1.0).isPositive())
        assertFalse((-0.001).isPositive())
    }

    @Test
    fun `J - isNonNegative returns true for zero and positive values`() {
        assertTrue(0.0.isNonNegative())
        assertTrue(0.01.isNonNegative())
        assertTrue(100.0.isNonNegative())
    }

    @Test
    fun `J2 - isNonNegative returns false for negative values`() {
        assertFalse((-0.01).isNonNegative())
        assertFalse((-100.0).isNonNegative())
    }
}

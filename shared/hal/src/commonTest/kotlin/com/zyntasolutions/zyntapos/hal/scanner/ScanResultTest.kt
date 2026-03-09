package com.zyntasolutions.zyntapos.hal.scanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Contract tests for [ScanResult] and [BarcodeFormat] (TEST-01).
 *
 * Verifies the sealed-class hierarchy used by all [BarcodeScanner] implementations
 * so that shared business logic (product lookup, coupon validation) can safely
 * pattern-match on scan events.
 */
class ScanResultTest {

    // ── ScanResult.Barcode ────────────────────────────────────────────────────

    @Test
    fun `Barcode holds value and format`() {
        val result = ScanResult.Barcode(value = "5901234123457", format = BarcodeFormat.EAN_13)

        assertEquals("5901234123457", result.value)
        assertEquals(BarcodeFormat.EAN_13, result.format)
    }

    @Test
    fun `Barcode is a ScanResult`() {
        val result: ScanResult = ScanResult.Barcode("123456", BarcodeFormat.CODE_128)
        assertIs<ScanResult.Barcode>(result)
    }

    @Test
    fun `two Barcodes with same content are equal`() {
        val a = ScanResult.Barcode("ABC", BarcodeFormat.QR_CODE)
        val b = ScanResult.Barcode("ABC", BarcodeFormat.QR_CODE)
        assertEquals(a, b)
    }

    @Test
    fun `two Barcodes with different values are not equal`() {
        val a = ScanResult.Barcode("ABC", BarcodeFormat.QR_CODE)
        val b = ScanResult.Barcode("XYZ", BarcodeFormat.QR_CODE)
        assertNotEquals(a, b)
    }

    @Test
    fun `two Barcodes with different formats are not equal`() {
        val a = ScanResult.Barcode("123", BarcodeFormat.EAN_13)
        val b = ScanResult.Barcode("123", BarcodeFormat.CODE_128)
        assertNotEquals(a, b)
    }

    // ── ScanResult.Error ──────────────────────────────────────────────────────

    @Test
    fun `Error holds message`() {
        val result = ScanResult.Error(message = "Camera focus failed")

        assertEquals("Camera focus failed", result.message)
    }

    @Test
    fun `Error is a ScanResult`() {
        val result: ScanResult = ScanResult.Error("hardware error")
        assertIs<ScanResult.Error>(result)
    }

    @Test
    fun `two Errors with same message are equal`() {
        val a = ScanResult.Error("timeout")
        val b = ScanResult.Error("timeout")
        assertEquals(a, b)
    }

    @Test
    fun `two Errors with different messages are not equal`() {
        val a = ScanResult.Error("timeout")
        val b = ScanResult.Error("permission denied")
        assertNotEquals(a, b)
    }

    // ── when exhaustiveness ───────────────────────────────────────────────────

    @Test
    fun `when expression covers all ScanResult variants`() {
        val results: List<ScanResult> = listOf(
            ScanResult.Barcode("test", BarcodeFormat.QR_CODE),
            ScanResult.Error("test error"),
        )

        var barcodeCount = 0
        var errorCount = 0
        results.forEach { result ->
            when (result) {
                is ScanResult.Barcode -> barcodeCount++
                is ScanResult.Error   -> errorCount++
            }
        }

        assertEquals(1, barcodeCount)
        assertEquals(1, errorCount)
    }

    // ── BarcodeFormat ─────────────────────────────────────────────────────────

    @Test
    fun `BarcodeFormat contains all required retail symbologies`() {
        val formats = BarcodeFormat.entries
        assertTrue(formats.contains(BarcodeFormat.EAN_13))
        assertTrue(formats.contains(BarcodeFormat.EAN_8))
        assertTrue(formats.contains(BarcodeFormat.UPC_A))
        assertTrue(formats.contains(BarcodeFormat.UPC_E))
        assertTrue(formats.contains(BarcodeFormat.CODE_128))
        assertTrue(formats.contains(BarcodeFormat.CODE_39))
        assertTrue(formats.contains(BarcodeFormat.QR_CODE))
        assertTrue(formats.contains(BarcodeFormat.UNKNOWN))
    }

    @Test
    fun `BarcodeFormat UNKNOWN is the fallback for unrecognised symbologies`() {
        // Regression guard: UNKNOWN must exist and be distinct from all named formats
        val named = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
        assertTrue(named.isNotEmpty())
        assertNotEquals(BarcodeFormat.UNKNOWN, BarcodeFormat.EAN_13)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        assertTrue(expected == actual, "Expected $expected but was $actual")
    }
}

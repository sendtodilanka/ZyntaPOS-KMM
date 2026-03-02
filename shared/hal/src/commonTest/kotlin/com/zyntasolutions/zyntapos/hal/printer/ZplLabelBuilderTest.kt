package com.zyntasolutions.zyntapos.hal.printer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [ZplLabelBuilder].
 *
 * Verifies ZPL command structure for typical label scenarios without
 * requiring a real printer — all tests operate on the ASCII byte output.
 */
class ZplLabelBuilderTest {

    private val defaultConfig = LabelPrinterConfig.DEFAULT
    private val defaultItem = LabelItem(
        barcode = "123456789012",  // 12-digit → Code 128
        productName = "Coconut Oil 1L",
        price = 450.00,
        widthMm = 50.0,
        heightMm = 25.0,
    )

    // ── 1. Label frame structure ──────────────────────────────────────────────

    @Test
    fun `label starts with XA and ends with XZ`() {
        val zpl = buildString(defaultItem)
        assertTrue(zpl.startsWith("^XA"), "Expected ^XA at start, got: ${zpl.take(20)}")
        assertTrue(zpl.trimEnd().endsWith("^XZ"), "Expected ^XZ at end")
    }

    @Test
    fun `label contains print width command`() {
        val zpl = buildString(defaultItem)
        assertContains(zpl, "^PW")
    }

    @Test
    fun `label contains label length command`() {
        val zpl = buildString(defaultItem)
        assertContains(zpl, "^LL")
    }

    @Test
    fun `label contains print quantity`() {
        val zpl = buildString(defaultItem, copies = 3)
        assertContains(zpl, "^PQ3")
    }

    // ── 2. Barcode type selection ─────────────────────────────────────────────

    @Test
    fun `EAN-13 barcode uses BEN command`() {
        val ean13Item = defaultItem.copy(barcode = "5901234123457") // 13 digits
        val zpl = buildString(ean13Item)
        assertContains(zpl, "^BEN")
        assertFalse(zpl.contains("^B3N"), "EAN-13 should not use Code 128 command")
    }

    @Test
    fun `non-EAN barcode uses Code 128 command`() {
        val code128Item = defaultItem.copy(barcode = "ABC-001-XYZ")
        val zpl = buildString(code128Item)
        assertContains(zpl, "^B3N")
        assertFalse(zpl.contains("^BEN"), "Code 128 should not use EAN-13 command")
    }

    @Test
    fun `12-digit barcode uses Code 128 not EAN-13`() {
        // EAN-13 requires exactly 13 digits
        val code128Item = defaultItem.copy(barcode = "123456789012") // 12 digits
        val zpl = buildString(code128Item)
        assertContains(zpl, "^B3N")
    }

    @Test
    fun `barcode data is included in label`() {
        val zpl = buildString(defaultItem)
        assertContains(zpl, defaultItem.barcode)
    }

    // ── 3. Product name ───────────────────────────────────────────────────────

    @Test
    fun `product name is included when not blank`() {
        val zpl = buildString(defaultItem)
        assertContains(zpl, "Coconut Oil 1L")
    }

    @Test
    fun `no FO command for product name when blank`() {
        val noNameItem = defaultItem.copy(productName = "")
        val zpl = buildString(noNameItem)
        // Product name is placed at ^FO16,16 (x=2mm, y=2mm at 8 dots/mm).
        // When productName is blank the entire block is skipped.
        assertFalse(zpl.contains("^FO16,16"), "Expected no field origin for blank product name")
    }

    // ── 4. Price formatting ───────────────────────────────────────────────────

    @Test
    fun `regular price is included in label`() {
        val zpl = buildString(defaultItem)
        assertContains(zpl, "450.00")
    }

    @Test
    fun `sale price takes precedence over regular price`() {
        val saleItem = defaultItem.copy(price = 500.00, salePrice = 399.00)
        val zpl = buildString(saleItem)
        assertContains(zpl, "399.00")
    }

    @Test
    fun `currency symbol is included`() {
        val zpl = buildString(defaultItem)
        assertContains(zpl, "Rs.")
    }

    // ── 5. Optional fields ────────────────────────────────────────────────────

    @Test
    fun `expiry date is included when present`() {
        val itemWithExpiry = defaultItem.copy(expiryDate = "2026-12-31")
        val zpl = buildString(itemWithExpiry)
        assertContains(zpl, "2026-12-31")
    }

    @Test
    fun `expiry date is absent when null`() {
        val zpl = buildString(defaultItem.copy(expiryDate = null))
        assertFalse(zpl.contains("Exp:"))
    }

    @Test
    fun `serial number is included when present`() {
        val itemWithSerial = defaultItem.copy(serialNumber = "SN-0042")
        val zpl = buildString(itemWithSerial)
        assertContains(zpl, "SN-0042")
    }

    // ── 6. Special character escaping ─────────────────────────────────────────

    @Test
    fun `caret in product name is replaced with space`() {
        val item = defaultItem.copy(productName = "Item^Name")
        val zpl = buildString(item)
        assertContains(zpl, "Item Name")
        assertFalse(zpl.contains("Item^Name"))
    }

    @Test
    fun `tilde in product name is replaced with space`() {
        val item = defaultItem.copy(productName = "Item~Name")
        val zpl = buildString(item)
        assertContains(zpl, "Item Name")
    }

    // ── 7. Batch build ────────────────────────────────────────────────────────

    @Test
    fun `batch produces concatenated ZPL for each item`() {
        val item2 = LabelItem(barcode = "999", productName = "Item2")
        val batch = ZplLabelBuilder.buildBatch(listOf(defaultItem, item2))
        val batchStr = batch.toString(Charsets.US_ASCII)
        // Two ^XA blocks
        val count = batchStr.windowed(3).count { it == "^XA" }
        assertTrue(count >= 2, "Expected 2 ^XA blocks in batch, found $count")
    }

    @Test
    fun `batch with single item equals single label`() {
        val single = ZplLabelBuilder.buildLabel(defaultItem)
        val batch = ZplLabelBuilder.buildBatch(listOf(defaultItem))
        assertTrue(single.contentEquals(batch), "Single item batch should equal buildLabel output")
    }

    // ── 8. Darkness and speed ─────────────────────────────────────────────────

    @Test
    fun `darkness level appears in SD command`() {
        val config = LabelPrinterConfig(darknessLevel = 12)
        val zpl = buildString(defaultItem, config = config)
        assertContains(zpl, "~SD12")
    }

    @Test
    fun `speed level appears in PR command`() {
        val config = LabelPrinterConfig(speedLevel = 5)
        val zpl = buildString(defaultItem, config = config)
        assertContains(zpl, "^PR6") // speedLevel+1
    }

    // ── 9. Output is valid ASCII ───────────────────────────────────────────────

    @Test
    fun `output bytes decode to valid ASCII string`() {
        val bytes = ZplLabelBuilder.buildLabel(defaultItem)
        val str = bytes.toString(Charsets.US_ASCII)
        assertTrue(str.isNotEmpty())
        assertTrue(bytes.all { it >= 0 }, "All bytes should be valid ASCII (non-negative)")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildString(
        item: LabelItem,
        config: LabelPrinterConfig = defaultConfig,
        copies: Int = 1,
    ): String = ZplLabelBuilder.buildLabel(item, config, copies).toString(Charsets.US_ASCII)
}

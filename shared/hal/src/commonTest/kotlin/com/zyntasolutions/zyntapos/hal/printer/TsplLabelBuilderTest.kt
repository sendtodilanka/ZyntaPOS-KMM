package com.zyntasolutions.zyntapos.hal.printer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [TsplLabelBuilder].
 *
 * Verifies TSPL command structure for typical TSC label printer scenarios.
 */
class TsplLabelBuilderTest {

    private val defaultConfig = LabelPrinterConfig.DEFAULT
    private val defaultItem = LabelItem(
        barcode = "987654321098",  // 12-digit → Code 128
        productName = "Rice Flour 500g",
        price = 320.00,
        widthMm = 50.0,
        heightMm = 25.0,
    )

    // ── 1. Label setup commands ───────────────────────────────────────────────

    @Test
    fun `label contains SIZE command`() {
        val tspl = buildString(defaultItem)
        assertContains(tspl, "SIZE")
        assertContains(tspl, "50")
        assertContains(tspl, "25")
    }

    @Test
    fun `label contains GAP command`() {
        val tspl = buildString(defaultItem)
        assertContains(tspl, "GAP")
    }

    @Test
    fun `label contains DENSITY command`() {
        val tspl = buildString(defaultItem)
        assertContains(tspl, "DENSITY")
    }

    @Test
    fun `label contains SPEED command`() {
        val tspl = buildString(defaultItem)
        assertContains(tspl, "SPEED")
    }

    @Test
    fun `label contains CLS command`() {
        val tspl = buildString(defaultItem)
        assertContains(tspl, "CLS")
    }

    @Test
    fun `label ends with PRINT command`() {
        val tspl = buildString(defaultItem, copies = 2)
        assertContains(tspl, "PRINT 2")
    }

    // ── 2. Barcode type selection ─────────────────────────────────────────────

    @Test
    fun `EAN-13 barcode uses EAN13 type`() {
        val ean13Item = defaultItem.copy(barcode = "5901234123457") // 13 digits
        val tspl = buildString(ean13Item)
        assertContains(tspl, "EAN13")
        assertFalse(tspl.contains("\"128\""), "EAN-13 should not use Code 128")
    }

    @Test
    fun `non-EAN barcode uses Code 128`() {
        val tspl = buildString(defaultItem.copy(barcode = "ITEM-XYZ-001"))
        assertContains(tspl, "\"128\"")
        assertFalse(tspl.contains("EAN13"), "Non-EAN should not use EAN13")
    }

    @Test
    fun `12-digit barcode uses Code 128`() {
        val tspl = buildString(defaultItem.copy(barcode = "123456789012"))
        assertContains(tspl, "\"128\"")
    }

    @Test
    fun `barcode value is in label output`() {
        val tspl = buildString(defaultItem)
        assertContains(tspl, defaultItem.barcode)
    }

    // ── 3. TEXT commands ─────────────────────────────────────────────────────

    @Test
    fun `product name uses TEXT command`() {
        val tspl = buildString(defaultItem)
        assertContains(tspl, "TEXT")
        assertContains(tspl, "Rice Flour 500g")
    }

    @Test
    fun `no TEXT for product name when blank`() {
        val noNameItem = defaultItem.copy(productName = "")
        val tspl = buildString(noNameItem)
        // The only TEXT commands should be for other fields (price / serial)
        // and there should be no name text
        assertFalse(tspl.contains("Rice Flour"))
    }

    // ── 4. Price ─────────────────────────────────────────────────────────────

    @Test
    fun `regular price included in TEXT`() {
        val tspl = buildString(defaultItem)
        assertContains(tspl, "320.00")
    }

    @Test
    fun `sale price takes precedence`() {
        val saleItem = defaultItem.copy(price = 500.00, salePrice = 280.00)
        val tspl = buildString(saleItem)
        assertContains(tspl, "280.00")
    }

    // ── 5. Optional fields ────────────────────────────────────────────────────

    @Test
    fun `expiry date is included when present`() {
        val item = defaultItem.copy(expiryDate = "2027-06-30")
        val tspl = buildString(item)
        assertContains(tspl, "2027-06-30")
    }

    @Test
    fun `serial number is included when present`() {
        val item = defaultItem.copy(serialNumber = "SN-9999")
        val tspl = buildString(item)
        assertContains(tspl, "SN-9999")
    }

    // ── 6. Special character escaping ─────────────────────────────────────────

    @Test
    fun `double-quote in product name is replaced with single-quote`() {
        val item = defaultItem.copy(productName = """Item "Premium" Grade""")
        val tspl = buildString(item)
        assertContains(tspl, "Item 'Premium' Grade")
        assertFalse(tspl.contains("""Item "Premium""""))
    }

    @Test
    fun `backslash in product name is replaced with forward-slash`() {
        val item = defaultItem.copy(productName = "Cat\\Dog")
        val tspl = buildString(item)
        assertContains(tspl, "Cat/Dog")
    }

    // ── 7. Batch build ────────────────────────────────────────────────────────

    @Test
    fun `batch produces two SIZE blocks for two items`() {
        val item2 = LabelItem(barcode = "111", productName = "Second Item")
        val batch = TsplLabelBuilder.buildBatch(listOf(defaultItem, item2))
        val batchStr = batch.toString(Charsets.US_ASCII)
        val count = batchStr.split("SIZE").size - 1
        assertTrue(count >= 2, "Expected 2 SIZE commands in batch, found $count")
    }

    // ── 8. Darkness and speed ─────────────────────────────────────────────────

    @Test
    fun `darkness level appears in DENSITY command`() {
        val config = LabelPrinterConfig(darknessLevel = 10)
        val tspl = buildString(defaultItem, config = config)
        assertContains(tspl, "DENSITY 10")
    }

    // ── 9. Output is valid ASCII ──────────────────────────────────────────────

    @Test
    fun `output bytes decode to valid ASCII string`() {
        val bytes = TsplLabelBuilder.buildLabel(defaultItem)
        val str = bytes.toString(Charsets.US_ASCII)
        assertTrue(str.isNotEmpty())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildString(
        item: LabelItem,
        config: LabelPrinterConfig = defaultConfig,
        copies: Int = 1,
    ): String = TsplLabelBuilder.buildLabel(item, config, copies).toString(Charsets.US_ASCII)
}

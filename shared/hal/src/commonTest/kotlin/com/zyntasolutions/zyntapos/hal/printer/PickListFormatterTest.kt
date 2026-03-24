package com.zyntasolutions.zyntapos.hal.printer

import com.zyntasolutions.zyntapos.domain.model.PickList
import com.zyntasolutions.zyntapos.domain.model.PickListItem
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Unit tests for [PickListFormatter] (P3-B1).
 */
class PickListFormatterTest {

    private val samplePickList = PickList(
        transferId = "t-abc123",
        sourceStoreName = "Main Warehouse",
        destinationStoreName = "Downtown Store",
        items = listOf(
            PickListItem(
                productId = "p1",
                productName = "Widget X",
                sku = "SKU-WX",
                quantity = 10.0,
                rackLocation = "A1",
                binLocation = "Row-2",
            ),
            PickListItem(
                productId = "p2",
                productName = "Gadget Y",
                sku = "SKU-GY",
                quantity = 5.5,
                rackLocation = null,
                binLocation = null,
            ),
        ),
        generatedAt = Instant.parse("2026-03-24T10:30:00Z"),
        notes = "Urgent shipment",
    )

    @Test
    fun `format 80mm contains header and items`() {
        val text = PickListFormatter.format(samplePickList, charsPerLine = 48)

        assertContains(text, "PICK LIST")
        assertContains(text, "Main Warehouse")
        assertContains(text, "Downtown Store")
        assertContains(text, "Widget X")
        assertContains(text, "Gadget Y")
        assertContains(text, "SKU-WX")
        assertContains(text, "A1")
        assertContains(text, "Urgent shipment")
        assertContains(text, "Total items: 2")
    }

    @Test
    fun `format 58mm still contains essential info`() {
        val text = PickListFormatter.format(samplePickList, charsPerLine = 32)

        assertContains(text, "PICK LIST")
        assertContains(text, "Widget X")
        assertContains(text, "Total items: 2")
    }

    @Test
    fun `integer quantities formatted without decimal`() {
        val text = PickListFormatter.format(samplePickList, charsPerLine = 48)

        // 10.0 should render as "10", not "10.0"
        assertContains(text, " 10")
        // 5.5 should render as "5.5"
        assertContains(text, "5.5")
    }

    @Test
    fun `toBytes returns non-empty byte array`() {
        val bytes = PickListFormatter.toBytes(samplePickList)

        assertTrue(bytes.isNotEmpty(), "Encoded pick list should not be empty")
    }

    @Test
    fun `null rack location renders as dash`() {
        val text = PickListFormatter.format(samplePickList, charsPerLine = 48)

        // Gadget Y has no rack — should show "-"
        val gadgetLine = text.lines().first { it.contains("Gadget Y") }
        assertContains(gadgetLine, "-")
    }
}

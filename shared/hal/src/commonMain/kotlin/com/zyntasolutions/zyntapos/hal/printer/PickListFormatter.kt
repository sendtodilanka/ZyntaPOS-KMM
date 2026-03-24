package com.zyntasolutions.zyntapos.hal.printer

import com.zyntasolutions.zyntapos.domain.model.PickList

/**
 * Formats a [PickList] as a plain-text document suitable for ESC/POS thermal
 * printing (P3-B1).
 *
 * The output is a fixed-width text block that fits 48-char (80 mm) or 32-char
 * (58 mm) paper. Items are listed in rack-location order so warehouse staff can
 * walk the aisles efficiently.
 *
 * ### Layout (80 mm / 48 chars)
 * ```
 * ============================================
 *              PICK LIST
 * ============================================
 * Transfer: abc123...
 * From:     Main Warehouse
 * To:       Downtown Store
 * Date:     2026-03-24 10:30
 * --------------------------------------------
 * #  Product          Qty   Rack    Bin
 * --------------------------------------------
 *  1 Widget X          10   A1      Row-2
 *  2 Gadget Y           5   B3      -
 * --------------------------------------------
 * Total items: 2
 * ============================================
 * ```
 */
object PickListFormatter {

    /**
     * Renders the pick list as a plain-text string.
     *
     * @param pickList The pick list to format.
     * @param charsPerLine Paper width in characters (32 for 58 mm, 48 for 80 mm).
     * @return Formatted plain-text ready for [PrinterManager.print] after encoding.
     */
    fun format(pickList: PickList, charsPerLine: Int = 48): String {
        val sep = "=".repeat(charsPerLine)
        val dash = "-".repeat(charsPerLine)
        val sb = StringBuilder()

        // Header
        sb.appendLine(sep)
        sb.appendLine(center("PICK LIST", charsPerLine))
        sb.appendLine(sep)
        sb.appendLine("Transfer: ${pickList.transferId.take(20)}")
        sb.appendLine("From:     ${pickList.sourceStoreName.take(charsPerLine - 10)}")
        sb.appendLine("To:       ${pickList.destinationStoreName.take(charsPerLine - 10)}")
        sb.appendLine("Date:     ${formatInstant(pickList.generatedAt)}")

        pickList.notes?.let { notes ->
            sb.appendLine("Notes:    ${notes.take(charsPerLine - 10)}")
        }

        sb.appendLine(dash)

        // Column header
        if (charsPerLine >= 48) {
            sb.appendLine(
                " #  " +
                "Product".padEnd(20) +
                "Qty".padStart(5) + "  " +
                "Rack".padEnd(8) +
                "Bin".padEnd(8)
            )
        } else {
            sb.appendLine(
                " # " +
                "Product".padEnd(14) +
                "Qty".padStart(4) + " " +
                "Rack".padEnd(6)
            )
        }
        sb.appendLine(dash)

        // Items
        pickList.items.forEachIndexed { index, item ->
            val num = "${index + 1}".padStart(2)
            val rack = (item.rackLocation ?: "-").take(if (charsPerLine >= 48) 7 else 5)
            val bin = (item.binLocation ?: "-").take(7)
            val qtyStr = formatQuantity(item.quantity)

            if (charsPerLine >= 48) {
                sb.appendLine(
                    " $num " +
                    item.productName.take(19).padEnd(20) +
                    qtyStr.padStart(5) + "  " +
                    rack.padEnd(8) +
                    bin.padEnd(8)
                )
                if (item.sku.isNotBlank()) {
                    sb.appendLine("     SKU: ${item.sku}")
                }
            } else {
                sb.appendLine(
                    " $num " +
                    item.productName.take(13).padEnd(14) +
                    qtyStr.padStart(4) + " " +
                    rack.padEnd(6)
                )
            }
        }

        sb.appendLine(dash)
        sb.appendLine("Total items: ${pickList.items.size}")
        sb.appendLine(sep)
        sb.appendLine() // trailing newline for paper feed

        return sb.toString()
    }

    /**
     * Converts a pick list to raw ESC/POS bytes using the given [config].
     *
     * This is a convenience method that formats the text and encodes it as
     * bytes suitable for [PrinterManager.print].
     */
    fun toBytes(pickList: PickList, config: PrinterConfig = PrinterConfig.DEFAULT): ByteArray {
        val text = format(pickList, config.paperWidth.charsPerLine)
        return text.encodeToByteArray()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun center(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        val pad = (width - text.length) / 2
        return " ".repeat(pad) + text
    }

    private fun formatQuantity(qty: Double): String =
        if (qty == qty.toLong().toDouble()) qty.toLong().toString()
        else "%.1f".format(qty)

    private fun formatInstant(instant: kotlinx.datetime.Instant): String {
        // Simple ISO-like format without requiring timezone dependency
        return instant.toString().take(16).replace('T', ' ')
    }
}

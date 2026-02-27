package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Pure-Kotlin TSPL (TSC Label Programming Language) label command builder.
 *
 * TSPL is used by TSC, Argox, and compatible label printers common in South/South-East Asia.
 * All methods are **pure** — no I/O, no side effects. Feed the result to
 * [LabelPrinterManager.printTspl] for transmission.
 *
 * ### TSPL commands used
 * | Command   | Purpose |
 * |-----------|---------|
 * | `SIZE`    | Label width and height in mm |
 * | `GAP`     | Gap between labels |
 * | `DENSITY` | Print darkness 0–15 |
 * | `SPEED`   | Print speed in inch/sec |
 * | `CLS`     | Clear image buffer |
 * | `TEXT`    | Print text string |
 * | `BARCODE` | Print barcode |
 * | `PRINT`   | Feed and print labels |
 */
object TsplLabelBuilder {

    /**
     * Builds a TSPL label for a single [LabelItem].
     *
     * @param item    Data to encode on the label.
     * @param config  [LabelPrinterConfig] supplying darkness and speed.
     * @param copies  Number of copies to print (default 1).
     * @return        TSPL byte array ready for transmission.
     */
    fun buildLabel(item: LabelItem, config: LabelPrinterConfig = LabelPrinterConfig.DEFAULT, copies: Int = 1): ByteArray {
        val sb = StringBuilder()

        // ── Label setup ──────────────────────────────────────────────────────
        sb.appendLine("""SIZE ${item.widthMm} mm,${item.heightMm} mm""")
        sb.appendLine("GAP 3 mm,0 mm")
        sb.appendLine("DENSITY ${config.darknessLevel}")
        sb.appendLine("SPEED ${(config.speedLevel + 1).coerceIn(1, 6)}")
        sb.appendLine("CLS")

        // TSPL coordinates in dots (203 DPI = 8 dots/mm)
        val dotsPerMm = 8

        // ── Product name ─────────────────────────────────────────────────────
        if (item.productName.isNotBlank()) {
            val x = 16  // 2mm * 8 dots/mm
            val y = 16
            val name = item.productName.take(30)
            sb.appendLine("""TEXT $x,$y,"3",0,1,1,"${tsplSafe(name)}"""")
        }

        // ── Price ─────────────────────────────────────────────────────────────
        if (item.price != null) {
            val x = 16
            val y = 64
            val priceText = if (item.salePrice != null) {
                "${item.currencySymbol}${formatPrice(item.salePrice)}"
            } else {
                "${item.currencySymbol}${formatPrice(item.price)}"
            }
            sb.appendLine("""TEXT $x,$y,"4",0,1,1,"${tsplSafe(priceText)}"""")
        }

        // ── Barcode ───────────────────────────────────────────────────────────
        if (item.barcode.isNotBlank()) {
            val bcX = 16
            val bcH = (item.heightMm * dotsPerMm * 0.4).toInt().coerceAtLeast(40)
            val bcY = (item.heightMm * dotsPerMm).toInt() - bcH - 24

            val ean = item.barcode.length == 13 && item.barcode.all { it.isDigit() }
            val barcodeType = if (ean) "EAN13" else "128"
            sb.appendLine("""BARCODE $bcX,$bcY,"$barcodeType",${bcH},1,0,2,2,"${tsplSafe(item.barcode)}"""")
        }

        // ── Expiry date ───────────────────────────────────────────────────────
        item.expiryDate?.let { expiry ->
            val x = 16
            val y = 40
            sb.appendLine("""TEXT $x,$y,"2",0,1,1,"Exp: ${tsplSafe(expiry)}"""")
        }

        // ── Serial number ─────────────────────────────────────────────────────
        item.serialNumber?.let { serial ->
            val x = 16
            val y = 28
            sb.appendLine("""TEXT $x,$y,"2",0,1,1,"${tsplSafe(serial)}"""")
        }

        // ── Print quantity ────────────────────────────────────────────────────
        sb.appendLine("PRINT $copies,1")

        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    /**
     * Builds a TSPL batch for multiple [items].
     * Each label is a separate `CLS … PRINT` block.
     */
    fun buildBatch(items: List<LabelItem>, config: LabelPrinterConfig = LabelPrinterConfig.DEFAULT): ByteArray {
        val parts = items.map { buildLabel(it, config) }
        val totalSize = parts.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun formatPrice(price: Double): String {
        val int = price.toLong()
        val frac = ((price - int) * 100).toLong().let { if (it < 0) -it else it }
        return "$int.${frac.toString().padStart(2, '0')}"
    }

    /** Escapes TSPL special characters in field data (double-quote is the delimiter). */
    private fun tsplSafe(text: String): String =
        text.replace("\"", "'").replace("\\", "/")
}

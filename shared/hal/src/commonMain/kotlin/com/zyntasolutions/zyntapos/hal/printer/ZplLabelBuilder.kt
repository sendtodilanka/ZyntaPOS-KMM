package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Pure-Kotlin ZPL (Zebra Programming Language) label command builder.
 *
 * All methods are **pure** — no I/O, no side effects. Feed the result to
 * [LabelPrinterManager.printZpl] for transmission to a Zebra-compatible printer.
 *
 * ### ZPL commands used
 * | Command | Purpose |
 * |---------|---------|
 * | `^XA`   | Start label format |
 * | `^XZ`   | End label format |
 * | `^FO`   | Field origin (x, y) |
 * | `^A0N`  | Scalable font |
 * | `^FD`   | Field data |
 * | `^FS`   | Field separator |
 * | `^BY`   | Bar code field default (module width) |
 * | `^B3N`  | Code 128 bar code |
 * | `^BEN`  | EAN-13 bar code |
 * | `^PQ`   | Print quantity |
 * | `^MN`   | Media tracking (gap or continuous) |
 * | `^PW`   | Print width in dots |
 * | `^LL`   | Label length in dots |
 * | `~SD`   | Print darkness |
 * | `^PR`   | Print speed |
 */
object ZplLabelBuilder {

    /** Dots-per-millimetre for standard 203 DPI Zebra printers. */
    const val DOTS_PER_MM = 8  // 203 DPI ≈ 8 dots/mm

    /**
     * Builds a ZPL label for a single [LabelItem].
     *
     * @param item    Data to encode on the label.
     * @param config  [LabelPrinterConfig] supplying darkness and speed.
     * @param copies  Number of copies to print (default 1).
     * @return        ZPL byte array ready for transmission.
     */
    fun buildLabel(item: LabelItem, config: LabelPrinterConfig = LabelPrinterConfig.DEFAULT, copies: Int = 1): ByteArray {
        val widthDots   = mmToDots(item.widthMm)
        val heightDots  = mmToDots(item.heightMm)
        val darkPercent = config.darknessLevel  // already 0–15; ^SD expects same range
        val speedVal    = (config.speedLevel + 1).coerceIn(1, 14)

        val sb = StringBuilder()

        // ── Label setup ──────────────────────────────────────────────────────
        sb.appendLine("^XA")
        sb.appendLine("~SD${darkPercent.toString().padStart(2, '0')}")  // print darkness
        sb.appendLine("^PR$speedVal")                                     // print speed
        sb.appendLine("^PW$widthDots")                                    // print width
        sb.appendLine("^LL$heightDots")                                   // label length
        sb.appendLine("^MNN")                                             // continuous media

        // ── Product name ─────────────────────────────────────────────────────
        if (item.productName.isNotBlank()) {
            val nameX = mmToDots(2.0)
            val nameY = mmToDots(2.0)
            val fontSize = fontSizeForWidth(item.widthMm, item.productName.length)
            sb.appendLine("^FO$nameX,$nameY")
            sb.appendLine("^A0N,$fontSize,$fontSize")
            sb.appendLine("^FD${zplSafe(item.productName.take(30))}^FS")
        }

        // ── Price ─────────────────────────────────────────────────────────────
        if (item.price != null) {
            val priceX = mmToDots(2.0)
            val priceY = mmToDots(8.0)
            val priceText = if (item.salePrice != null) {
                "${item.currencySymbol}${formatPrice(item.salePrice)}"
            } else {
                "${item.currencySymbol}${formatPrice(item.price)}"
            }
            sb.appendLine("^FO$priceX,$priceY")
            sb.appendLine("^A0N,28,28")
            sb.appendLine("^FD${zplSafe(priceText)}^FS")
        }

        // ── Barcode ───────────────────────────────────────────────────────────
        if (item.barcode.isNotBlank()) {
            val bcX = mmToDots(2.0)
            val bcY = heightDots - mmToDots(12.0)
            sb.appendLine("^FO$bcX,$bcY")

            val ean = item.barcode.length == 13 && item.barcode.all { it.isDigit() }
            if (ean) {
                sb.appendLine("^BEN,50,Y,N")  // EAN-13
            } else {
                sb.appendLine("^BY2")          // module width = 2 dots
                sb.appendLine("^B3N,N,50,Y,N") // Code 128 auto-subset
            }
            sb.appendLine("^FD${zplSafe(item.barcode)}^FS")
        }

        // ── Serial / sequential number ────────────────────────────────────────
        item.serialNumber?.let { serial ->
            val serialX = mmToDots(2.0)
            val serialY = mmToDots(3.0)
            sb.appendLine("^FO$serialX,$serialY")
            sb.appendLine("^A0N,16,16")
            sb.appendLine("^FD${zplSafe(serial)}^FS")
        }

        // ── Expiry date ───────────────────────────────────────────────────────
        item.expiryDate?.let { expiry ->
            val expX = mmToDots(2.0)
            val expY = mmToDots(5.0)
            sb.appendLine("^FO$expX,$expY")
            sb.appendLine("^A0N,16,16")
            sb.appendLine("^FDExp: ${zplSafe(expiry)}^FS")
        }

        // ── Print quantity ────────────────────────────────────────────────────
        sb.appendLine("^PQ$copies")

        sb.appendLine("^XZ")

        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    /**
     * Builds a single ZPL label batch for multiple [items].
     *
     * Each item is a separate label format (`^XA … ^XZ`) separated by a newline.
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

    private fun mmToDots(mm: Double): Int = (mm * DOTS_PER_MM).toInt()

    private fun fontSizeForWidth(widthMm: Double, charCount: Int): Int {
        val availableDots = mmToDots(widthMm) - mmToDots(4.0)  // 2mm margin each side
        val sizeByWidth = (availableDots / charCount.coerceAtLeast(1)).coerceIn(16, 40)
        return sizeByWidth
    }

    private fun formatPrice(price: Double): String {
        val int = price.toLong()
        val frac = ((price - int) * 100).toLong().let { if (it < 0) -it else it }
        return "$int.${frac.toString().padStart(2, '0')}"
    }

    /** Escapes ZPL special characters that would break field data. */
    private fun zplSafe(text: String): String =
        text.replace("^", " ").replace("~", " ").replace("\\", " ")
}

/**
 * Data carried by a single label print job.
 *
 * @property barcode        Barcode value (EAN-13 triggers EAN-13 encoder; other lengths → Code 128).
 * @property productName    Human-readable product name printed above the barcode.
 * @property price          Regular selling price (printed on label, or struck-through if [salePrice] set).
 * @property salePrice      Sale/promotional price; when set, this is the displayed price.
 * @property currencySymbol Symbol prepended to price (e.g. "₨", "$").
 * @property widthMm        Label width in millimetres.
 * @property heightMm       Label height in millimetres.
 * @property serialNumber   Optional sequential serial number (e.g. "SN-0001").
 * @property expiryDate     Optional expiry date string (e.g. "2026-12-31").
 */
data class LabelItem(
    val barcode: String,
    val productName: String = "",
    val price: Double? = null,
    val salePrice: Double? = null,
    val currencySymbol: String = "Rs.",
    val widthMm: Double = 50.0,
    val heightMm: Double = 25.0,
    val serialNumber: String? = null,
    val expiryDate: String? = null,
)

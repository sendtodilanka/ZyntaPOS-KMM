package com.zyntasolutions.zyntapos.hal.printer

import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.RegisterSession

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Pure-Kotlin ESC/POS receipt generator that implements [ReceiptBuilder] for
 * 58 mm (32 chars/line) and 80 mm (48 chars/line) thermal paper rolls.
 *
 * ### ESC/POS Commands Used
 * | Command           | Hex / Bytes          | Purpose                        |
 * |-------------------|----------------------|--------------------------------|
 * | `ESC @`           | 1B 40                | Initialize / reset printer     |
 * | `ESC t n`         | 1B 74 nn             | Select character code page     |
 * | `ESC E n`         | 1B 45 01/00          | Bold on / off                  |
 * | `ESC a n`         | 1B 61 00/01/02       | Align left / centre / right    |
 * | `GS ! n`          | 1D 21 nn             | Character size (width × height)|
 * | `ESC p m t1 t2`   | 1B 70 00 19 FA       | Cash-drawer kick pulse         |
 * | `GS ( k`          | 1D 28 6B ...         | Print QR code                  |
 * | `GS V n`          | 1D 56 42 00          | Paper cut (partial)            |
 * | `FS p kc1 kc2`    | 1C 70 20 01          | Print NV logo from slot 1      |
 * | `GS k m [data] NUL`| 1D 6B 02 ...        | Code 128 barcode               |
 *
 * All methods are **pure** — no I/O, no side effects.
 *
 * @param config Active [PrinterConfig] governing paper width, character set,
 *               header/footer content, and optional QR code / logo printing.
 */
class EscPosReceiptBuilder(private val config: PrinterConfig) : ReceiptBuilder {

    // ─── ESC/POS command constants ──────────────────────────────────────────

    private val ESC: Byte = 0x1B.toByte()
    private val GS: Byte  = 0x1D.toByte()
    private val LF: Byte  = 0x0A.toByte()

    /** Initialize printer to default state. */
    private val CMD_INIT           = byteArrayOf(ESC, 0x40)

    /** Select character code page (`ESC t n`). */
    private fun cmdCodePage(set: CharacterSet) = byteArrayOf(ESC, 0x74, set.code.toByte())

    // Alignment
    private val CMD_ALIGN_LEFT     = byteArrayOf(ESC, 0x61, 0x00)
    private val CMD_ALIGN_CENTER   = byteArrayOf(ESC, 0x61, 0x01)
    private val CMD_ALIGN_RIGHT    = byteArrayOf(ESC, 0x61, 0x02)

    // Bold
    private val CMD_BOLD_ON        = byteArrayOf(ESC, 0x45, 0x01)
    private val CMD_BOLD_OFF       = byteArrayOf(ESC, 0x45, 0x00)

    // Character sizes (GS ! n — bits 0-2 height multiplier, bits 4-6 width multiplier)
    private val CMD_SIZE_NORMAL    = byteArrayOf(GS,  0x21, 0x00)   // 1x1
    private val CMD_SIZE_DOUBLE_H  = byteArrayOf(GS,  0x21, 0x01)   // 1w × 2h
    @Suppress("unused")
    private val CMD_SIZE_DOUBLE_W  = byteArrayOf(GS,  0x21, 0x10)   // 2w × 1h
    @Suppress("unused")
    private val CMD_SIZE_DOUBLE    = byteArrayOf(GS,  0x21, 0x11)   // 2x2

    /** Partial paper cut (`GS V 66 0`). */
    private val CMD_PAPER_CUT      = byteArrayOf(GS,  0x56, 0x42, 0x00)

    /** Feed 3 blank lines before cut. */
    private val CMD_FEED_LINES     = byteArrayOf(ESC, 0x64, 0x03)

    // ─── Convenience columns ────────────────────────────────────────────────

    private val cols: Int get() = config.paperWidth.charsPerLine

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Implements [ReceiptBuilder.buildReceipt] — delegates to the extended overload
     * with default cashier name and receipt counter.
     */
    override fun buildReceipt(order: Order, config: PrinterConfig): ByteArray =
        buildReceipt(order, config, cashierName = "", receiptCount = 0)

    /**
     * Builds a customer receipt for the given [order].
     *
     * Layout (top → bottom):
     * 1. Init + code page
     * 2. NV logo (`FS p`) — if [PrinterConfig.showLogo] and [PrinterConfig.logoNvSlot] set
     * 3. Store header (centered, bold) — from [PrinterConfig.headerLines]
     * 4. Separator rule
     * 5. Order number + date/time + cashier name (if [PrinterConfig.showCashierName])
     * 6. Separator rule
     * 7. Item table: name (left) | qty | unit price | line total (right)
     * 8. Separator rule
     * 9. Subtotal / Tax detail / Discount / **Total** (right-aligned values)
     * 10. Payment method + amount tendered + change
     * 11. Separator rule
     * 12. Footer (rotating promotional text or static [PrinterConfig.footerLines])
     * 13. Receipt barcode (Code 128 of order number) — if [PrinterConfig.showReceiptBarcode]
     * 14. QR code block (`GS ( k`) — if [PrinterConfig.showQrCode]
     * 15. Feed + paper cut
     *
     * @param order        Fully-resolved [Order] with items, totals, and payment details.
     * @param config       Printer layout configuration.
     * @param cashierName  Display name of the logged-in cashier. Printed when
     *                     [PrinterConfig.showCashierName] is `true`.
     * @param receiptCount Running counter (1-based) used to select the rotating footer text.
     */
    fun buildReceipt(
        order: Order,
        config: PrinterConfig,
        cashierName: String,
        receiptCount: Int,
    ): ByteArray {
        val buf = ByteArrayBuffer()
        val cfg = config

        buf += CMD_INIT
        buf += cmdCodePage(cfg.characterSet)

        // ── 1. NV logo ────────────────────────────────────────────────────────
        if (cfg.showLogo && cfg.logoNvSlot != null) {
            buf += CMD_ALIGN_CENTER
            buf += LogoNvInjector.buildPrintNvLogoCommand()
            buf += LF
        }

        // ── 2. Header ────────────────────────────────────────────────────────
        if (cfg.headerLines.isNotEmpty()) {
            buf += CMD_ALIGN_CENTER
            buf += CMD_BOLD_ON
            cfg.headerLines.forEachIndexed { idx, line ->
                buf += if (idx == 0) CMD_SIZE_DOUBLE_H else CMD_SIZE_NORMAL
                buf += line.truncate(cols).toByteArray()
                buf += LF
            }
            buf += CMD_SIZE_NORMAL
            buf += CMD_BOLD_OFF
        }

        buf += separatorLine('=')

        // ── 3. Order reference + timestamp + cashier ─────────────────────────
        buf += CMD_ALIGN_LEFT
        buf += "Order : ${order.orderNumber}".toByteArray()
        buf += LF
        buf += "Date  : ${order.createdAt}".toByteArray()
        buf += LF
        if (cfg.showCashierName && cashierName.isNotBlank()) {
            buf += "Cashier: ${cashierName.truncate(cols - 9)}".toByteArray()
            buf += LF
        }

        buf += separatorLine('-')

        // ── 4. Item table ─────────────────────────────────────────────────────
        //   58mm (32): name (16) | qty (4) | price (6) | total (6)
        //   80mm (48): name (24) | qty (5) | price (9) | total (10)
        val qtyW   = if (cols <= 32) 4  else 5
        val priceW = if (cols <= 32) 6  else 9
        val totW   = if (cols <= 32) 6  else 10
        val nameW  = cols - qtyW - priceW - totW

        // Column header
        val hdrLine = "ITEM".padEnd(nameW) +
                      "QTY".padStart(qtyW) +
                      "PRICE".padStart(priceW) +
                      "TOTAL".padStart(totW)
        buf += CMD_BOLD_ON
        buf += hdrLine.toByteArray()
        buf += LF
        buf += CMD_BOLD_OFF

        order.items.forEach { item ->
            val name      = item.productName.truncate(nameW).padEnd(nameW)
            val qty       = formatQty(item.quantity).padStart(qtyW)
            val price     = formatMoney(item.unitPrice).padStart(priceW)
            val lineTotal = formatMoney(item.lineTotal).padStart(totW)
            buf += (name + qty + price + lineTotal).toByteArray()
            buf += LF

            // Discount sub-line (only when a discount was applied)
            if (item.discount > 0.0) {
                val discLabel = "  Discount: -${formatMoney(item.discount)}".truncate(cols)
                buf += discLabel.toByteArray()
                buf += LF
            }
        }

        buf += separatorLine('-')

        // ── 5. Totals block ───────────────────────────────────────────────────
        val labelW = cols - 12
        buf += totalsLine("Subtotal",   formatMoney(order.subtotal),      labelW)

        if (cfg.showTaxDetail && order.taxBreakdown.isNotEmpty()) {
            // Per-tax-group detail rows (e.g. "VAT 8%" : 120.00)
            order.taxBreakdown.forEach { (label, amount) ->
                buf += totalsLine("  $label", "+${formatMoney(amount)}", labelW)
            }
        } else if (order.taxAmount > 0.0) {
            buf += totalsLine("Tax", "+${formatMoney(order.taxAmount)}", labelW)
        }

        if (order.discountAmount > 0.0)
            buf += totalsLine("Discount", "-${formatMoney(order.discountAmount)}", labelW)

        // Grand total — double-height bold
        buf += CMD_BOLD_ON
        buf += CMD_SIZE_DOUBLE_H
        buf += totalsLine("TOTAL", formatMoney(order.total), labelW)
        buf += CMD_SIZE_NORMAL
        buf += CMD_BOLD_OFF

        buf += separatorLine('-')

        // ── 6. Payment ────────────────────────────────────────────────────────
        buf += totalsLine("Payment", labelForPayment(order.paymentMethod), labelW)
        if (order.paymentMethod == PaymentMethod.SPLIT) {
            order.paymentSplits.forEach { split ->
                val label = "  ${labelForPayment(split.method)}"
                buf += totalsLine(label, formatMoney(split.amount), labelW)
            }
        }
        if (order.paymentMethod == PaymentMethod.CASH) {
            buf += totalsLine("Tendered", formatMoney(order.amountTendered), labelW)
            buf += totalsLine("Change",   formatMoney(order.changeAmount),   labelW)
        }

        // ── 7. Footer (rotating or static) ───────────────────────────────────
        val footerToShow: List<String> = when {
            cfg.rotatingFooterTexts.isNotEmpty() -> {
                val idx = ((receiptCount - 1) / cfg.footerRotationInterval) %
                          cfg.rotatingFooterTexts.size
                listOf(cfg.rotatingFooterTexts[idx.coerceAtLeast(0)])
            }
            cfg.footerLines.isNotEmpty() -> cfg.footerLines
            else -> emptyList()
        }
        if (footerToShow.isNotEmpty()) {
            buf += separatorLine('-')
            buf += CMD_ALIGN_CENTER
            footerToShow.forEach { line ->
                buf += line.truncate(cols).toByteArray()
                buf += LF
            }
        }

        // ── 8. Receipt barcode (Code 128) ─────────────────────────────────────
        if (cfg.showReceiptBarcode && order.orderNumber.isNotBlank()) {
            buf += CMD_ALIGN_CENTER
            buf += LF
            buf += buildCode128Command(order.orderNumber)
            buf += LF
        }

        // ── 9. QR code ────────────────────────────────────────────────────────
        if (cfg.showQrCode && order.orderNumber.isNotBlank()) {
            buf += CMD_ALIGN_CENTER
            buf += LF
            buf += buildQrCodeCommand(order.orderNumber)
            buf += LF
        }

        // ── 10. Feed + cut ────────────────────────────────────────────────────
        buf += CMD_ALIGN_LEFT
        buf += CMD_FEED_LINES
        buf += CMD_PAPER_CUT

        return buf.toByteArray()
    }

    /**
     * Builds a Z-report (end-of-shift summary) for the given register [session].
     *
     * Layout (top → bottom):
     * 1. Header with "Z-REPORT" banner
     * 2. Session open/close timestamps + operator names
     * 3. Opening float
     * 4. Cash sales / expected balance / actual balance / variance
     * 5. Feed + paper cut
     */
    override fun buildZReport(session: RegisterSession): ByteArray {
        val buf = ByteArrayBuffer()
        val labelW = cols - 12

        buf += CMD_INIT
        buf += cmdCodePage(config.characterSet)

        // Header
        buf += CMD_ALIGN_CENTER
        buf += CMD_BOLD_ON
        buf += CMD_SIZE_DOUBLE_H
        buf += "Z-REPORT".toByteArray()
        buf += LF
        buf += CMD_SIZE_NORMAL
        buf += CMD_BOLD_OFF

        buf += separatorLine('=')

        buf += CMD_ALIGN_LEFT
        buf += totalsLine("Session ID", session.id.takeLast(8), labelW)
        buf += totalsLine("Opened by",  session.openedBy.takeLast(16), labelW)
        buf += totalsLine("Opened at",  session.openedAt.toString(), labelW)
        session.closedAt?.let  { buf += totalsLine("Closed at",  it.toString(), labelW) }
        session.closedBy?.let  { buf += totalsLine("Closed by",  it.takeLast(16), labelW) }

        buf += separatorLine('-')

        buf += totalsLine("Opening Float",    formatMoney(session.openingBalance),  labelW)
        buf += totalsLine("Expected Balance", formatMoney(session.expectedBalance), labelW)
        session.closingBalance?.let { buf += totalsLine("Closing Balance", formatMoney(it), labelW) }
        session.actualBalance?.let  {
            buf += totalsLine("Actual Balance",  formatMoney(it), labelW)
            val variance = it - session.expectedBalance
            val varLabel = if (variance >= 0) "+${formatMoney(variance)}" else "-${formatMoney(-variance)}"
            buf += totalsLine("Variance", varLabel, labelW)
        }

        buf += separatorLine('=')

        // Status
        buf += CMD_ALIGN_CENTER
        buf += "Status: ${session.status}".toByteArray()
        buf += LF

        buf += CMD_FEED_LINES
        buf += CMD_PAPER_CUT

        return buf.toByteArray()
    }

    /**
     * Builds a printer self-test / calibration page.
     *
     * Prints a ruler line, character samples, and then cuts the paper so the
     * operator can verify alignment and character encoding visually.
     */
    override fun buildTestPage(): ByteArray {
        val buf = ByteArrayBuffer()

        buf += CMD_INIT
        buf += cmdCodePage(config.characterSet)

        buf += CMD_ALIGN_CENTER
        buf += CMD_BOLD_ON
        buf += "PRINTER TEST PAGE".truncate(cols).toByteArray()
        buf += LF
        buf += CMD_BOLD_OFF

        buf += separatorLine('=')

        // Ruler: 0123456789 repeated to fill the line width
        buf += CMD_ALIGN_LEFT
        val ruler = (0 until cols).joinToString("") { (it % 10).toString() }
        buf += ruler.toByteArray()
        buf += LF

        buf += separatorLine('-')

        // Paper width indicator
        buf += "Paper width : $cols chars/line".toByteArray()
        buf += LF
        buf += "Config      : ${config.paperWidth} / ${config.characterSet}".toByteArray()
        buf += LF

        buf += separatorLine('-')

        // Sample receipt item row
        val sample = "Sample Item".padEnd(cols - 18) +
                     "2".padStart(4) +
                     "9.99".padStart(7) +
                     "19.98".padStart(7)
        buf += sample.toByteArray()
        buf += LF

        buf += separatorLine('=')

        buf += CMD_ALIGN_CENTER
        buf += "** TEST COMPLETE **".toByteArray()
        buf += LF

        buf += CMD_FEED_LINES
        buf += CMD_PAPER_CUT

        return buf.toByteArray()
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Returns a full-width separator line filled with [char] followed by [LF].
     */
    private fun separatorLine(char: Char): ByteArray =
        (char.toString().repeat(cols) + "\n").toByteArray()

    /**
     * Right-aligns [value] (12 chars wide) and left-pads [label] to [labelW],
     * producing a full-width totals row.
     */
    private fun totalsLine(label: String, value: String, labelW: Int): ByteArray {
        val row = label.truncate(labelW).padEnd(labelW) + value.padStart(12)
        return (row.truncate(cols) + "\n").toByteArray()
    }

    /**
     * Truncates this string to [max] characters, appending "…" if truncated.
     */
    private fun String.truncate(max: Int): String =
        if (length <= max) this else substring(0, maxOf(0, max - 1)) + "\u2026"

    /**
     * Formats a currency amount to 2 decimal places.
     * Kept locale-neutral for ESC/POS compatibility.
     */
    private fun formatMoney(amount: Double): String =
        amount.let {
            val int  = it.toLong()
            val frac = ((it - int) * 100).toLong().let { f -> if (f < 0) -f else f }
            "$int.${frac.toString().padStart(2, '0')}"
        }

    /**
     * Formats a quantity value — strips trailing ".0" for whole-unit quantities.
     */
    private fun formatQty(qty: Double): String =
        if (qty == qty.toLong().toDouble()) qty.toLong().toString() else qty.toString()

    /** Maps [PaymentMethod] to a human-readable receipt label. */
    private fun labelForPayment(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH          -> "Cash"
        PaymentMethod.CARD          -> "Card"
        PaymentMethod.MOBILE        -> "Mobile Pay"
        PaymentMethod.BANK_TRANSFER -> "Bank Transfer"
        PaymentMethod.SPLIT         -> "Split"
    }

    /**
     * Builds the multi-byte `GS ( k` QR code ESC/POS sequence for [data].
     *
     * Sequence:
     * 1. Select model 2 QR code
     * 2. Set size (module = 4)
     * 3. Set error correction level M
     * 4. Store data
     * 5. Print stored QR code
     */
    private fun buildQrCodeCommand(data: String): ByteArray {
        val dataBytes = data.toByteArray(Charsets.US_ASCII)
        val store     = ByteArrayBuffer()

        // Select model 2
        store += byteArrayOf(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00)
        // Set module size (4 = recommended for 80mm; 3 for 58mm)
        val moduleSize: Byte = if (cols <= 32) 3 else 4
        store += byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, moduleSize)
        // Error correction level M
        store += byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31)
        // Store data (pL + pH = dataLen + 3)
        val pLen = dataBytes.size + 3
        val pL   = (pLen and 0xFF).toByte()
        val pH   = ((pLen shr 8) and 0xFF).toByte()
        store += byteArrayOf(GS, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30)
        store += dataBytes
        // Print stored QR code
        store += byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)

        return store.toByteArray()
    }

    /**
     * Builds a Code 128 barcode ESC/POS command for [data].
     *
     * Uses the `GS k` two-part format: `GS k m d1...dk NUL` where m = 2 (Code 128).
     * Height is fixed at 50 dots; HRI characters printed below.
     */
    private fun buildCode128Command(data: String): ByteArray {
        val dataBytes = data.toByteArray(Charsets.US_ASCII)
        val buf = ByteArrayBuffer()

        // Set barcode height: GS h n (50 dots)
        buf += byteArrayOf(GS, 0x68, 50)
        // HRI position: below barcode — GS H n (2 = below)
        buf += byteArrayOf(GS, 0x48, 0x02)
        // GS k 2 [data] NUL — Code 128 auto-subset
        buf += byteArrayOf(GS, 0x6B, 0x02)
        buf += dataBytes
        buf += byteArrayOf(0x00)  // NUL terminator

        return buf.toByteArray()
    }
}

// ─── Minimal ByteArray accumulation helper ───────────────────────────────────

/**
 * Lightweight mutable byte buffer used inside [EscPosReceiptBuilder].
 *
 * Avoids repeated [ByteArray] concatenation allocations; all appended chunks
 * are materialised only when [toByteArray] is called.
 */
private class ByteArrayBuffer {
    private val chunks = mutableListOf<ByteArray>()

    /** Appends a [ByteArray] chunk. */
    operator fun plusAssign(bytes: ByteArray) { chunks += bytes }

    /** Appends a single [Byte]. */
    operator fun plusAssign(byte: Byte) { chunks += byteArrayOf(byte) }

    /** Materialises all chunks into a single [ByteArray]. */
    fun toByteArray(): ByteArray {
        val total = chunks.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}

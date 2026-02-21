package com.zyntasolutions.zyntapos.domain.formatter

import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod

/**
 * Pure-domain receipt text formatter.
 *
 * Produces a human-readable, monospace-friendly receipt [String] directly from
 * an [Order] domain model. This class has **no HAL dependencies** — it does not
 * generate ESC/POS byte sequences. Its output is used exclusively for on-screen
 * preview (e.g., [com.zyntasolutions.zyntapos.feature.pos.ReceiptScreen]).
 *
 * The physical print pipeline uses [com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder]
 * in the infrastructure layer to produce the actual byte commands sent to the printer.
 *
 * ### Example output (48-char line / 80 mm paper)
 * ```
 * ================================================
 *             ZYNTA POINT OF SALE
 *          123 Main Street, Colombo 03
 *             Tel: +94 11 234 5678
 * ================================================
 * Date: 2026-02-21           Time: 14:35:02
 * Order #: 1001
 * ------------------------------------------------
 * Coca-Cola 1L         x2         LKR  580.00
 * Bread (White)        x1         LKR  150.00
 * ------------------------------------------------
 * Subtotal:                       LKR  730.00
 * Tax (15%):                      LKR  109.50
 * Discount:                      -LKR   50.00
 * ================================================
 * TOTAL:                          LKR  789.50
 * ================================================
 * Cash Tendered:                  LKR  800.00
 * Change:                         LKR   10.50
 * ================================================
 *          Thank you for your purchase!
 *         Please come again. Have a nice day!
 * ================================================
 * ```
 *
 * @param currencyCode ISO 4217 currency code passed to [CurrencyFormatter] (default: "LKR").
 */
class ReceiptFormatter(
    private val currencyFormatter: CurrencyFormatter,
    private val currencyCode: String = "LKR",
) {

    /**
     * Formats [order] as a plain-text receipt string.
     *
     * @param order       The completed [Order] to render.
     * @param headerLines Store branding lines printed above the order details
     *   (e.g., store name, address, phone). Up to 5 lines are rendered.
     * @param footerLines Closing message lines (e.g., thank-you text, website).
     *   Up to 3 lines are rendered.
     * @param charsPerLine Number of characters per line. Use 32 for 58 mm paper,
     *   48 for 80 mm paper. Defaults to 48.
     * @return A monospace-friendly receipt string ready for display.
     */
    fun format(
        order: Order,
        headerLines: List<String> = emptyList(),
        footerLines: List<String> = emptyList(),
        charsPerLine: Int = DEFAULT_CHARS_PER_LINE,
    ): String = buildString {

        val divider   = "=".repeat(charsPerLine)
        val separator = "-".repeat(charsPerLine)

        // ── Header ────────────────────────────────────────────────────────────
        appendLine(divider)
        headerLines.take(5).forEach { line ->
            appendLine(line.take(charsPerLine).center(charsPerLine))
        }
        if (headerLines.isNotEmpty()) appendLine(divider)

        // ── Order meta ────────────────────────────────────────────────────────
        val dateTime = order.createdAt.toString()
            .replace("T", "  Time: ")
            .take(38) // trim sub-second precision
        appendLine("Date: $dateTime")
        appendLine("Order #: ${order.orderNumber}")
        if (order.customerId != null) {
            appendLine("Customer ID: ${order.customerId}")
        }
        appendLine(separator)

        // ── Line items ────────────────────────────────────────────────────────
        order.items.forEach { item -> appendLine(formatItem(item, charsPerLine)) }
        appendLine(separator)

        // ── Totals ────────────────────────────────────────────────────────────
        appendLine(formatTwoCol("Subtotal:", fmt(order.subtotal), charsPerLine))
        if (order.taxAmount > 0.0) {
            appendLine(formatTwoCol("Tax:", fmt(order.taxAmount), charsPerLine))
        }
        if (order.discountAmount > 0.0) {
            appendLine(formatTwoCol("Discount:", "-${fmt(order.discountAmount)}", charsPerLine))
        }
        appendLine(divider)
        appendLine(formatTwoCol("TOTAL:", fmt(order.total), charsPerLine))
        appendLine(divider)

        // ── Payment breakdown ─────────────────────────────────────────────────
        if (order.paymentSplits.isNotEmpty()) {
            order.paymentSplits.forEach { split ->
                appendLine(formatTwoCol(split.method.label + ":", fmt(split.amount), charsPerLine))
            }
        } else {
            appendLine(formatTwoCol(order.paymentMethod.label + ":", fmt(order.total), charsPerLine))
        }
        if (order.paymentMethod == PaymentMethod.CASH) {
            appendLine(formatTwoCol("Cash Tendered:", fmt(order.amountTendered), charsPerLine))
            appendLine(formatTwoCol("Change:", fmt(order.changeAmount), charsPerLine))
        }
        appendLine(divider)

        // ── Footer ────────────────────────────────────────────────────────────
        footerLines.take(3).forEach { line ->
            appendLine(line.take(charsPerLine).center(charsPerLine))
        }
        append(divider) // no trailing newline on last line
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun formatItem(item: OrderItem, charsPerLine: Int): String {
        val name     = item.productName.take(20).padEnd(20)
        val qty      = "x${item.quantity.toInt()}"
        val total    = fmt(item.lineTotal)
        val middle   = "$name  $qty"
        return formatTwoCol(middle, total, charsPerLine)
    }

    /**
     * Formats two strings as left-aligned label + right-aligned value within [width] chars.
     */
    private fun formatTwoCol(left: String, right: String, width: Int): String {
        val gap = width - left.length - right.length
        return if (gap > 0) left + " ".repeat(gap) + right
        else (left.take(width - right.length - 1) + " " + right).take(width)
    }

    private fun fmt(amount: Double): String =
        currencyFormatter.format(amount, currencyCode)

    private fun String.center(width: Int): String {
        if (length >= width) return take(width)
        val pad = (width - length) / 2
        return " ".repeat(pad) + this
    }

    /** Label shown on the receipt line (e.g. "Cash", "Card"). */
    private val PaymentMethod.label: String
        get() = when (this) {
            PaymentMethod.CASH          -> "Cash"
            PaymentMethod.CARD          -> "Card"
            PaymentMethod.MOBILE        -> "Mobile Pay"
            PaymentMethod.BANK_TRANSFER -> "Bank Transfer"
            PaymentMethod.SPLIT         -> "Split"
        }

    companion object {
        const val DEFAULT_CHARS_PER_LINE = 48
        const val CHARS_58MM = 32
        const val CHARS_80MM = 48
    }
}

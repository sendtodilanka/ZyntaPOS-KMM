package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager

/**
 * Prints a condensed sales summary report to the connected thermal printer.
 *
 * ### Thermal Format (Z-report style)
 * ```
 * ══════════════════════════════════
 *       SALES REPORT SUMMARY
 * ══════════════════════════════════
 * Period: 2026-02-21 to 2026-02-21
 * ----------------------------------
 * Total Sales      LKR 12,500.00
 * Orders                       47
 * Avg Order Value   LKR 265.96
 * ----------------------------------
 * Cash             LKR  8,200.00
 * Card             LKR  4,300.00
 * ----------------------------------
 * [CUT]
 * ```
 *
 * The report is forwarded to [PrinterManager] which handles connection,
 * retry and queue management. On success the print job is accepted (or queued).
 *
 * @param receiptBuilder ESC/POS byte generator (shared:hal).
 * @param printerManager Printer gateway (shared:hal).
 */
class PrintReportUseCase(
    private val receiptBuilder: EscPosReceiptBuilder,
    private val printerManager: PrinterManager,
) {

    /**
     * Prints a condensed sales summary report.
     *
     * @param report The [GenerateSalesReportUseCase.SalesReport] to print.
     * @return [Result.success] when the print job is accepted (or queued).
     *         [Result.failure] if all retries are exhausted.
     */
    suspend fun printSalesSummary(report: GenerateSalesReportUseCase.SalesReport): Result<Unit> {
        return try {
            val bytes = buildSalesReportBytes(report)
            printerManager.print(bytes).fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { e -> Result.failure(Exception("Print failed: ${e.message}", e)) },
            )
        } catch (e: Exception) {
            Result.failure(Exception("Sales report build failed: ${e.message}", e))
        }
    }

    /**
     * Assembles the ESC/POS byte sequence for the condensed sales summary.
     * Uses the column width from [EscPosReceiptBuilder] config (58 mm = 32 chars, 80 mm = 48 chars).
     */
    private fun buildSalesReportBytes(report: GenerateSalesReportUseCase.SalesReport): ByteArray {
        val ESC: Byte = 0x1B.toByte()
        val GS:  Byte = 0x1D.toByte()
        val LF:  Byte = 0x0A.toByte()

        fun String.escBytes() = this.toByteArray(Charsets.UTF_8)
        fun line(text: String) = (text + "\n").escBytes()
        fun separator(width: Int = 32) = ("-".repeat(width) + "\n").escBytes()

        val header    = byteArrayOf(ESC, 0x40)          // Initialize
        val boldOn    = byteArrayOf(ESC, 0x45, 0x01)
        val boldOff   = byteArrayOf(ESC, 0x45, 0x00)
        val centerOn  = byteArrayOf(ESC, 0x61, 0x01)
        val leftAlign = byteArrayOf(ESC, 0x61, 0x00)
        val cut       = byteArrayOf(GS, 0x56, 0x42, 0x00)

        val sections = mutableListOf<ByteArray>()
        sections += header
        sections += centerOn
        sections += boldOn
        sections += line("SALES REPORT SUMMARY")
        sections += boldOff
        sections += line("=".repeat(32))
        sections += leftAlign
        sections += line("Period: ${report.from.toString().take(10)} to ${report.to.toString().take(10)}")
        sections += separator()
        sections += line(padRow("Total Sales", "LKR ${"%.2f".format(report.totalSales)}"))
        sections += line(padRow("Orders", report.orderCount.toString()))
        sections += line(padRow("Avg Order Value", "LKR ${"%.2f".format(report.avgOrderValue)}"))
        sections += separator()
        report.salesByPaymentMethod.forEach { (method, amount) ->
            sections += line(padRow(method.name, "LKR ${"%.2f".format(amount)}"))
        }
        sections += separator()
        sections += byteArrayOf(LF, LF)
        sections += cut

        return sections.fold(byteArrayOf()) { acc, bytes -> acc + bytes }
    }

    /**
     * Pads a key-value pair to fill a 32-character line.
     * e.g. `"Total Sales"` and `"LKR 500.00"` → `"Total Sales    LKR 500.00"`
     */
    private fun padRow(label: String, value: String, width: Int = 32): String {
        val padding = (width - label.length - value.length).coerceAtLeast(1)
        return label + " ".repeat(padding) + value
    }
}

package com.zyntasolutions.zyntapos.feature.reports.printer

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.printer.ReportPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.PrinterConfig
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager

/**
 * Infrastructure adapter that implements [ReportPrinterPort] using the HAL printer pipeline.
 *
 * This class is the **sole owner** of all sales-summary report printing infrastructure:
 * 1. Assembles ESC/POS bytes from [GenerateSalesReportUseCase.SalesReport] data.
 * 2. Opens the printer transport via [PrinterManager.connect].
 * 3. Delivers the byte buffer via [PrinterManager.print].
 *
 * Lives in `:composeApp:feature:reports` — the only module in the reports feature that can
 * safely import from both `:shared:domain` and `:shared:hal` without circular dependencies.
 *
 * @param printerManager  HAL gateway for ESC/POS command delivery.
 */
class ReportPrinterAdapter(
    private val printerManager: PrinterManager,
) : ReportPrinterPort {

    override suspend fun printSalesSummary(
        report: GenerateSalesReportUseCase.SalesReport,
    ): Result<Unit> {
        return try {
            val bytes = buildSalesReportBytes(report)

            val connectResult = printerManager.connect()
            if (connectResult.isFailure) {
                val msg = connectResult.exceptionOrNull()?.message
                ZyntaLogger.e(TAG, "Printer connect failed: $msg")
                return Result.failure(Exception("Printer connection failed: $msg"))
            }

            printerManager.print(bytes).fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { e ->
                    ZyntaLogger.e(TAG, "Sales report print failed: ${e.message}")
                    Result.failure(Exception("Print failed: ${e.message}", e))
                },
            )
        } catch (e: Exception) {
            ZyntaLogger.e(TAG, "Sales report build threw: ${e.message}")
            Result.failure(Exception("Sales report build failed: ${e.message}", e))
        }
    }

    /**
     * Assembles the ESC/POS byte sequence for the condensed sales summary.
     * Column width: 58 mm = 32 chars, 80 mm = 48 chars (driven by [PrinterConfig]).
     */
    private fun buildSalesReportBytes(report: GenerateSalesReportUseCase.SalesReport): ByteArray {
        val ESC: Byte = 0x1B.toByte()
        val GS:  Byte = 0x1D.toByte()
        val LF:  Byte = 0x0A.toByte()

        fun line(text: String) = (text + "\n").toByteArray(Charsets.UTF_8)
        fun separator(width: Int = 32) = ("-".repeat(width) + "\n").toByteArray(Charsets.UTF_8)

        val header    = byteArrayOf(ESC, 0x40)
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
        sections += line(
            "Period: ${report.from.toString().take(10)} to ${report.to.toString().take(10)}",
        )
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

    private fun padRow(label: String, value: String, width: Int = 32): String {
        val padding = (width - label.length - value.length).coerceAtLeast(1)
        return label + " ".repeat(padding) + value
    }

    companion object {
        private const val TAG = "ReportPrinterAdapter"
    }
}

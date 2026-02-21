package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.printer.ReportPrinterPort

/**
 * Prints a condensed sales summary report to the connected thermal printer.
 *
 * ### Layering rationale
 * All ESC/POS byte assembly, transport connection, and retry logic are delegated
 * to [ReportPrinterPort], keeping this class free of HAL dependencies.
 *
 * ```
 * :composeApp:feature:reports   ← calls printSalesSummary()
 *        ↓
 * :shared:domain                ← PrintReportUseCase + ReportPrinterPort (this file)
 *        ↑
 * :shared:hal                   ← ReportPrinterAdapter (implements ReportPrinterPort)
 * ```
 *
 * @param printerPort Adapter responsible for the complete report print pipeline.
 *
 * @see ReportPrinterPort
 * @see com.zyntasolutions.zyntapos.feature.reports.printer.ReportPrinterAdapter
 */
class PrintReportUseCase(
    private val printerPort: ReportPrinterPort,
) {
    /**
     * Prints a condensed sales summary report.
     *
     * @param report The [GenerateSalesReportUseCase.SalesReport] to print.
     * @return [Result.success] when the print job is accepted or queued.
     *         [Result.failure] if all retries are exhausted.
     */
    suspend fun printSalesSummary(report: GenerateSalesReportUseCase.SalesReport): Result<Unit> =
        printerPort.printSalesSummary(report)
}

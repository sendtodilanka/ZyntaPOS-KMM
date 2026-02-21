package com.zyntasolutions.zyntapos.domain.printer

import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase

/**
 * Output port that abstracts condensed sales-summary report printing from the domain layer.
 *
 * Defined in `:shared:domain` so that [com.zyntasolutions.zyntapos.domain.usecase.reports.PrintReportUseCase]
 * remains free of HAL dependencies. Implementations live in the infrastructure layer —
 * currently [com.zyntasolutions.zyntapos.feature.reports.printer.ReportPrinterAdapter]
 * in `:composeApp:feature:reports`.
 *
 * ### Adapter contract
 * Implementations are responsible for:
 * 1. Assembling ESC/POS bytes from [GenerateSalesReportUseCase.SalesReport] data.
 * 2. Connecting the printer transport.
 * 3. Delivering the byte buffer.
 *
 * @see com.zyntasolutions.zyntapos.feature.reports.printer.ReportPrinterAdapter
 */
interface ReportPrinterPort {

    /**
     * Prints a condensed sales-summary thermal report.
     *
     * @param report The [GenerateSalesReportUseCase.SalesReport] to print.
     * @return [Result.success] when the print job is accepted or queued.
     *         [Result.failure] wrapping the underlying [Throwable] on transport error.
     */
    suspend fun printSalesSummary(report: GenerateSalesReportUseCase.SalesReport): Result<Unit>
}

package com.zyntasolutions.zyntapos.domain.printer

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase

/**
 * Domain output port for A4-format document printing.
 *
 * Handles full-page PDF document generation and delivery for:
 * - Tax invoices (customer-facing, legally required for B2B sales in Sri Lanka)
 * - Z-reports (end-of-shift register summary)
 * - Sales report exports
 *
 * Implementations live in the feature layer:
 * - `:composeApp:feature:pos` — `A4InvoicePrinterAdapter`
 * - `:composeApp:feature:register` — `A4ZReportPrinterAdapter`
 */
interface A4InvoicePrinterPort {

    /**
     * Generates and delivers an A4 tax invoice PDF for the given [order].
     *
     * The PDF is rendered with the store logo, IRD-compliant invoice layout,
     * full line-item table, tax summary, and payment details. On desktop the
     * system print dialog is shown; on Android the PDF is shared via share sheet.
     *
     * @param order Completed [Order] to invoice.
     * @return [Result.Success] on delivery; [Result.Error] on render/print failure.
     */
    suspend fun printA4Invoice(order: Order): Result<Unit>

    /**
     * Generates and delivers an A4 Z-report PDF for the given register [session].
     *
     * @param session The [RegisterSession] being closed.
     * @return [Result.Success] on delivery; [Result.Error] on render/print failure.
     */
    suspend fun printA4ZReport(session: RegisterSession): Result<Unit>

    /**
     * Generates and exports an A4 sales report PDF for the given [report].
     *
     * On desktop the system print/save dialog is shown; on Android the PDF is
     * shared via the system share sheet.
     *
     * @param report The [GenerateSalesReportUseCase.SalesReport] to render.
     * @return [Result.Success] on delivery; [Result.Error] on render/export failure.
     */
    suspend fun printA4SalesReport(report: GenerateSalesReportUseCase.SalesReport): Result<Unit>
}

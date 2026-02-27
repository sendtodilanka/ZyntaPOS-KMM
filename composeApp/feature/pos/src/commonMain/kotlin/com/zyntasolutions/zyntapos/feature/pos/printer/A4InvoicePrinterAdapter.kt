package com.zyntasolutions.zyntapos.feature.pos.printer

import com.zyntasolutions.zyntapos.core.result.HalException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase

/**
 * Infrastructure adapter that implements [A4InvoicePrinterPort] for A4 PDF document generation.
 *
 * In Phase 1 this adapter generates a plain-text representation of the invoice/Z-report
 * and delegates to the platform-provided [A4PrintDelegate] to open the system print dialog
 * (Desktop) or share sheet (Android). Full PDF rendering with logo, layout, and IRD-compliant
 * formatting is Phase 2.
 *
 * Bound in `:composeApp:feature:pos` [PosModule] as [A4InvoicePrinterPort].
 */
class A4InvoicePrinterAdapter(
    private val delegate: A4PrintDelegate,
) : A4InvoicePrinterPort {

    override suspend fun printA4Invoice(order: Order): Result<Unit> =
        runCatching {
            val content = buildA4InvoiceText(order)
            delegate.printDocument(
                title = "Tax Invoice — Order #${order.orderNumber}",
                content = content,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(HalException(t.message ?: "A4 invoice print failed", cause = t)) },
        )

    override suspend fun printA4ZReport(session: RegisterSession): Result<Unit> =
        runCatching {
            val content = buildA4ZReportText(session)
            delegate.printDocument(
                title = "Z-Report — Session ${session.id}",
                content = content,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(HalException(t.message ?: "A4 Z-report print failed", cause = t)) },
        )

    override suspend fun printA4SalesReport(report: GenerateSalesReportUseCase.SalesReport): Result<Unit> =
        runCatching {
            val content = buildA4SalesReportText(report)
            delegate.printDocument(
                title = "Sales Report",
                content = content,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(HalException(t.message ?: "A4 sales report print failed", cause = t)) },
        )

    // ── Text renderers (Phase 1: plain text; Phase 2: replace with PDF layout) ─

    private fun buildA4InvoiceText(order: Order): String = buildString {
        appendLine("=".repeat(60))
        appendLine("TAX INVOICE")
        appendLine("=".repeat(60))
        appendLine("Order #: ${order.orderNumber}")
        appendLine("Date   : ${order.createdAt}")
        appendLine("-".repeat(60))
        appendLine("ITEM                          QTY    UNIT    TOTAL")
        appendLine("-".repeat(60))
        order.items.forEach { item ->
            appendLine("%-30s %4s  %7.2f  %7.2f".format(
                item.productName.take(30), item.quantity.toInt(),
                item.unitPrice, item.lineTotal,
            ))
        }
        appendLine("-".repeat(60))
        appendLine("SUBTOTAL: %,.2f".format(order.subtotal))
        appendLine("TAX     : %,.2f".format(order.taxAmount))
        appendLine("TOTAL   : %,.2f".format(order.total))
        appendLine("=".repeat(60))
    }

    private fun buildA4ZReportText(session: RegisterSession): String = buildString {
        appendLine("=".repeat(60))
        appendLine("Z-REPORT — END OF SESSION")
        appendLine("=".repeat(60))
        appendLine("Session ID : ${session.id}")
        appendLine("Opened     : ${session.openedAt}")
        appendLine("Closed     : ${session.closedAt ?: "Open"}")
        appendLine("Opening    : %,.2f".format(session.openingBalance))
        appendLine("=".repeat(60))
    }

    private fun buildA4SalesReportText(report: GenerateSalesReportUseCase.SalesReport): String = buildString {
        appendLine("=".repeat(60))
        appendLine("SALES REPORT")
        appendLine("=".repeat(60))
        appendLine("Total Sales : %,.2f".format(report.totalSales))
        appendLine("Orders      : ${report.orderCount}")
        appendLine("=".repeat(60))
    }
}

/**
 * Platform-provided delegate for delivering A4 documents.
 *
 * On JVM desktop: opens the system print dialog.
 * On Android: shares via the share sheet.
 *
 * Implementations live in `jvmMain` and `androidMain` source sets.
 */
interface A4PrintDelegate {
    suspend fun printDocument(title: String, content: String)
}

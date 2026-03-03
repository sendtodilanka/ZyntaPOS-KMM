package com.zyntasolutions.zyntapos.feature.reports

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateCustomerReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateExpenseReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JVM (Desktop) implementation of [ReportExporter].
 *
 * CSV files are written via [FileWriter]. PDF generation uses a simple HTML-to-PDF
 * approach via Apache PDFBox (PDFBox dependency registered in jvmMain).
 * The user selects the output directory via a [JFileChooser] dialog.
 *
 * All file I/O is dispatched on [Dispatchers.IO] to avoid blocking the UI thread.
 */
class JvmReportExporter : ReportExporter {

    private val dateStamp: String
        get() = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

    override suspend fun exportSalesCsv(report: GenerateSalesReportUseCase.SalesReport): String =
        withContext(Dispatchers.IO) {
            val dir = chooseSaveDirectory() ?: throw Exception("Export cancelled by user")
            val file = File(dir, "sales_report_$dateStamp.csv")
            FileWriter(file).use { writer ->
                writer.appendLine("Sales Report — ${report.from} to ${report.to}")
                writer.appendLine("Total Sales,${report.totalSales}")
                writer.appendLine("Order Count,${report.orderCount}")
                writer.appendLine("Average Order Value,${report.avgOrderValue}")
                writer.appendLine()
                writer.appendLine("Payment Method,Amount")
                report.salesByPaymentMethod.forEach { (method, amount) ->
                    writer.appendLine("${method.name},$amount")
                }
                writer.appendLine()
                writer.appendLine("Product ID,Revenue")
                report.topProducts.forEach { (productId, revenue) ->
                    writer.appendLine("$productId,$revenue")
                }
            }
            file.absolutePath
        }

    override suspend fun exportSalesPdf(report: GenerateSalesReportUseCase.SalesReport): String =
        withContext(Dispatchers.IO) {
            val dir = chooseSaveDirectory() ?: throw Exception("Export cancelled by user")
            val file = File(dir, "sales_report_$dateStamp.pdf")
            // Build HTML content then render to PDF via PDFBox
            val html = buildSalesHtml(report)
            PdfBoxRenderer.renderHtmlToPdf(html, file)
            file.absolutePath
        }

    override suspend fun exportStockCsv(report: GenerateStockReportUseCase.StockReport): String =
        withContext(Dispatchers.IO) {
            val dir = chooseSaveDirectory() ?: throw Exception("Export cancelled by user")
            val file = File(dir, "stock_report_$dateStamp.csv")
            FileWriter(file).use { writer ->
                writer.appendLine("Stock Report")
                writer.appendLine("Product,Category,Qty,Cost Price,Status")
                report.allProducts.forEach { product ->
                    val status = when {
                        product.stockQty <= 0                   -> "Out of Stock"
                        product.stockQty < product.minStockQty  -> "Low Stock"
                        else                                    -> "OK"
                    }
                    writer.appendLine("${product.name},${product.categoryId},${product.stockQty},${product.costPrice},$status")
                }
                writer.appendLine()
                writer.appendLine("Low Stock Items: ${report.lowStockItems.size}")
                writer.appendLine("Dead Stock Items: ${report.deadStockItems.size}")
            }
            file.absolutePath
        }

    override suspend fun exportStockPdf(report: GenerateStockReportUseCase.StockReport): String =
        withContext(Dispatchers.IO) {
            val dir = chooseSaveDirectory() ?: throw Exception("Export cancelled by user")
            val file = File(dir, "stock_report_$dateStamp.pdf")
            val html = buildStockHtml(report)
            PdfBoxRenderer.renderHtmlToPdf(html, file)
            file.absolutePath
        }

    override suspend fun exportCustomerCsv(report: GenerateCustomerReportUseCase.CustomerReport): String =
        withContext(Dispatchers.IO) {
            val dir = chooseSaveDirectory() ?: throw Exception("Export cancelled by user")
            val file = File(dir, "customer_report_$dateStamp.csv")
            FileWriter(file).use { writer ->
                writer.appendLine("Customer Report")
                writer.appendLine("Total Customers,${report.totalCustomers}")
                writer.appendLine("Registered,${report.registeredCustomers}")
                writer.appendLine("Walk-In,${report.walkInCustomers}")
                writer.appendLine("Credit Enabled,${report.creditEnabledCustomers}")
                writer.appendLine("Total Loyalty Points,${report.totalLoyaltyPoints}")
                writer.appendLine()
                writer.appendLine("Top Customers by Loyalty Points")
                writer.appendLine("Name,Phone,Loyalty Points,Group")
                report.topByLoyaltyPoints.forEach { c ->
                    writer.appendLine("${c.name},${c.phone},${c.loyaltyPoints},${c.groupId ?: ""}")
                }
            }
            file.absolutePath
        }

    override suspend fun exportExpenseCsv(report: GenerateExpenseReportUseCase.ExpenseReport): String =
        withContext(Dispatchers.IO) {
            val dir = chooseSaveDirectory() ?: throw Exception("Export cancelled by user")
            val file = File(dir, "expense_report_$dateStamp.csv")
            FileWriter(file).use { writer ->
                writer.appendLine("Expense Report — ${report.from} to ${report.to}")
                writer.appendLine("Status,Total Amount,Count")
                writer.appendLine("Approved,${report.totalApproved},${report.approvedCount}")
                writer.appendLine("Pending,${report.totalPending},${report.pendingCount}")
                writer.appendLine("Rejected,${report.totalRejected},${report.rejectedCount}")
                writer.appendLine()
                writer.appendLine("Category Breakdown (Approved)")
                writer.appendLine("Category ID,Total")
                report.byCategory.forEach { (categoryId, total) ->
                    writer.appendLine("${categoryId ?: "Uncategorised"},$total")
                }
            }
            file.absolutePath
        }

    private fun chooseSaveDirectory(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select Export Directory"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else null
    }

    private fun buildSalesHtml(report: GenerateSalesReportUseCase.SalesReport) = buildString {
        append("<html><body><h2>Sales Report</h2>")
        append("<p><b>Period:</b> ${report.from} – ${report.to}</p>")
        append("<p><b>Total Sales:</b> LKR ${"%.2f".format(report.totalSales)}</p>")
        append("<p><b>Orders:</b> ${report.orderCount} | <b>Avg:</b> LKR ${"%.2f".format(report.avgOrderValue)}</p>")
        append("<h3>Payment Breakdown</h3><table border='1'>")
        append("<tr><th>Method</th><th>Amount</th></tr>")
        report.salesByPaymentMethod.forEach { (m, a) -> append("<tr><td>${m.name}</td><td>${"%.2f".format(a)}</td></tr>") }
        append("</table>")
        append("<h3>Top Products</h3><table border='1'>")
        append("<tr><th>Product</th><th>Revenue</th></tr>")
        report.topProducts.forEach { (id, rev) -> append("<tr><td>$id</td><td>${"%.2f".format(rev)}</td></tr>") }
        append("</table></body></html>")
    }

    private fun buildStockHtml(report: GenerateStockReportUseCase.StockReport) = buildString {
        append("<html><body><h2>Stock Report</h2>")
        append("<p>Total Active Products: ${report.allProducts.size}</p>")
        append("<p>Low Stock: ${report.lowStockItems.size} | Dead Stock: ${report.deadStockItems.size}</p>")
        append("<table border='1'><tr><th>Product</th><th>Category</th><th>Qty</th><th>Status</th></tr>")
        report.allProducts.forEach { p ->
            val s = when { p.stockQty <= 0 -> "Out"; p.stockQty < p.minStockQty -> "Low"; else -> "OK" }
            append("<tr><td>${p.name}</td><td>${p.categoryId}</td><td>${p.stockQty}</td><td>$s</td></tr>")
        }
        append("</table></body></html>")
    }
}

/**
 * Minimal PDFBox-based HTML-to-PDF renderer for JVM Desktop.
 *
 * NOTE: For Phase 1 the HTML is serialised as plain text into the PDF.
 * In Phase 2 this should be replaced with a proper HTML→PDF library (e.g., Flying Saucer / OpenHTMLtoPDF).
 */
private object PdfBoxRenderer {
    fun renderHtmlToPdf(html: String, outputFile: File) {
        // Phase 1: simple text PDF via PDFBox PDDocument
        try {
            val doc = org.apache.pdfbox.pdmodel.PDDocument()
            val page = org.apache.pdfbox.pdmodel.PDPage()
            doc.addPage(page)
            val stream = org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)
            stream.beginText()
            stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 10f)
            stream.newLineAtOffset(25f, 750f)
            // Strip HTML tags for plain text fallback
            val plain = html.replace(Regex("<[^>]+>"), "").lines()
            plain.take(60).forEach { line ->
                stream.showText(line.take(100))
                stream.newLineAtOffset(0f, -14f)
            }
            stream.endText()
            stream.close()
            doc.save(outputFile)
            doc.close()
        } catch (e: Exception) {
            ZyntaLogger.w("JvmReportExporter", "PDFBox not available, falling back to HTML output: ${e.message}")
            // If PDFBox not available, write HTML as .pdf extension (fallback)
            outputFile.writeText(html)
        }
    }
}

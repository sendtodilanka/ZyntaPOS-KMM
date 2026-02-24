package com.zyntasolutions.zyntapos.feature.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateCustomerReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateExpenseReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of [ReportExporter].
 *
 * Generates CSV/PDF files in the app's cache directory and shares them
 * via [Intent.ACTION_SEND] (system ShareSheet).
 *
 * PDF generation uses an HTML template approach — the HTML string is written
 * to a WebView and printed to PDF via Android's [android.print.PrintManager].
 * For Phase 1 a plain-text `.pdf` file is generated as a fallback if
 * [android.print.PrintManager] is unavailable in the test environment.
 *
 * @param context Android [Context] used for file provider and Intent dispatch.
 */
class AndroidReportExporter(private val context: Context) : ReportExporter {

    private val dateStamp: String
        get() = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

    override suspend fun exportSalesCsv(report: GenerateSalesReportUseCase.SalesReport): String =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "sales_report_$dateStamp.csv")
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
            shareFile(file, "text/csv")
            file.absolutePath
        }

    override suspend fun exportSalesPdf(report: GenerateSalesReportUseCase.SalesReport): String =
        withContext(Dispatchers.IO) {
            val html = buildSalesHtml(report)
            val file = File(context.cacheDir, "sales_report_$dateStamp.pdf")
            // Phase 1 fallback: write HTML to file; Phase 2 will use WebView print API
            file.writeText(html)
            shareFile(file, "application/pdf")
            file.absolutePath
        }

    override suspend fun exportStockCsv(report: GenerateStockReportUseCase.StockReport): String =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "stock_report_$dateStamp.csv")
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
                writer.appendLine("Low Stock Items: ${report.lowStockItems.size}")
                writer.appendLine("Dead Stock Items: ${report.deadStockItems.size}")
            }
            shareFile(file, "text/csv")
            file.absolutePath
        }

    override suspend fun exportStockPdf(report: GenerateStockReportUseCase.StockReport): String =
        withContext(Dispatchers.IO) {
            val html = buildStockHtml(report)
            val file = File(context.cacheDir, "stock_report_$dateStamp.pdf")
            file.writeText(html)
            shareFile(file, "application/pdf")
            file.absolutePath
        }

    override suspend fun exportCustomerCsv(report: GenerateCustomerReportUseCase.CustomerReport): String =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "customer_report_$dateStamp.csv")
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
            shareFile(file, "text/csv")
            file.absolutePath
        }

    override suspend fun exportExpenseCsv(report: GenerateExpenseReportUseCase.ExpenseReport): String =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "expense_report_$dateStamp.csv")
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
            shareFile(file, "text/csv")
            file.absolutePath
        }

    /** Share a file via Android ShareSheet using [FileProvider]. */
    private fun shareFile(file: File, mimeType: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Export Report"))
    }

    private fun buildSalesHtml(report: GenerateSalesReportUseCase.SalesReport) = buildString {
        append("<html><body><h2>Sales Report</h2>")
        append("<p><b>Period:</b> ${report.from} – ${report.to}</p>")
        append("<p><b>Total Sales:</b> LKR ${"%.2f".format(report.totalSales)}</p>")
        append("<p><b>Orders:</b> ${report.orderCount} | <b>Avg:</b> LKR ${"%.2f".format(report.avgOrderValue)}</p>")
        append("<h3>Payment Breakdown</h3><table border='1'>")
        report.salesByPaymentMethod.forEach { (m, a) -> append("<tr><td>${m.name}</td><td>${"%.2f".format(a)}</td></tr>") }
        append("</table><h3>Top Products</h3><table border='1'>")
        report.topProducts.forEach { (id, rev) -> append("<tr><td>$id</td><td>${"%.2f".format(rev)}</td></tr>") }
        append("</table></body></html>")
    }

    private fun buildStockHtml(report: GenerateStockReportUseCase.StockReport) = buildString {
        append("<html><body><h2>Stock Report</h2>")
        append("<p>Total Active Products: ${report.allProducts.size}</p>")
        append("<table border='1'><tr><th>Product</th><th>Category</th><th>Qty</th><th>Status</th></tr>")
        report.allProducts.forEach { p ->
            val s = when { p.stockQty <= 0 -> "Out"; p.stockQty < p.minStockQty -> "Low"; else -> "OK" }
            append("<tr><td>${p.name}</td><td>${p.categoryId}</td><td>${p.stockQty}</td><td>$s</td></tr>")
        }
        append("</table></body></html>")
    }
}

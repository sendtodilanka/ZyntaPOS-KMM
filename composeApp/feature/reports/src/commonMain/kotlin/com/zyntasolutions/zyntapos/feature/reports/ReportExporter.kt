package com.zyntasolutions.zyntapos.feature.reports

import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase

/**
 * Platform-specific report exporter interface.
 *
 * Provides CSV and PDF export for both Sales and Stock reports.
 *
 * ### Platform implementations
 * - **JVM (Desktop):** CSV written via [java.io.FileWriter]; PDF via Apache PDFBox.
 *   User selects the output directory via [javax.swing.JFileChooser].
 * - **Android:** CSV/PDF generated as a [java.io.File] in the app's cache dir,
 *   then shared via [android.content.Intent.ACTION_SEND] / system ShareSheet.
 *
 * @see JvmReportExporter (jvmMain)
 * @see AndroidReportExporter (androidMain)
 */
interface ReportExporter {
    /**
     * Export the sales report as a CSV file.
     * @return Absolute path of the written file.
     */
    suspend fun exportSalesCsv(report: GenerateSalesReportUseCase.SalesReport): String

    /**
     * Export the sales report as a PDF file.
     * @return Absolute path of the written file.
     */
    suspend fun exportSalesPdf(report: GenerateSalesReportUseCase.SalesReport): String

    /**
     * Export the stock report as a CSV file.
     * @return Absolute path of the written file.
     */
    suspend fun exportStockCsv(report: GenerateStockReportUseCase.StockReport): String

    /**
     * Export the stock report as a PDF file.
     * @return Absolute path of the written file.
     */
    suspend fun exportStockPdf(report: GenerateStockReportUseCase.StockReport): String
}

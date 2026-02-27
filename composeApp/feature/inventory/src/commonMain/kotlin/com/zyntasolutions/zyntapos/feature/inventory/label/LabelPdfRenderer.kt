package com.zyntasolutions.zyntapos.feature.inventory.label

import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.feature.inventory.BarcodeType

/**
 * Platform-specific barcode label PDF renderer.
 *
 * Implementations:
 * - JVM Desktop: [JvmLabelPdfRenderer] — Apache PDFBox 3.0.3
 * - Android:     [AndroidLabelPdfRenderer] — android.graphics.pdf.PdfDocument
 *
 * The returned [ByteArray] is a complete, valid PDF binary. Callers save or
 * share this via platform file APIs.
 *
 * @see JvmLabelPdfRenderer
 * @see AndroidLabelPdfRenderer
 */
interface LabelPdfRenderer {

    /**
     * Renders all [items] onto pages according to [template].
     *
     * Each item is printed [PrintQueueItem.quantity] times as consecutive label cells.
     * Cells fill left-to-right, then top-to-bottom before advancing to the next page.
     *
     * This is a CPU-intensive operation; call from [kotlinx.coroutines.Dispatchers.IO].
     *
     * @param items    Queue items to render (each repeated [PrintQueueItem.quantity] times).
     * @param template Paper/layout configuration.
     * @return Complete PDF as [ByteArray].
     * @throws IllegalArgumentException if [items] is empty.
     */
    suspend fun render(items: List<PrintQueueItem>, template: LabelTemplate): ByteArray

    /** Determines the [BarcodeType] for a given barcode string value. */
    fun detectBarcodeType(barcode: String): BarcodeType =
        if (barcode.length == 13 && barcode.all { it.isDigit() }) BarcodeType.EAN_13
        else BarcodeType.CODE_128
}

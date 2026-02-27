package com.zyntasolutions.zyntapos.feature.inventory.label

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Android implementation of [LabelPdfRenderer] using [android.graphics.pdf.PdfDocument].
 *
 * Android PdfDocument uses pixels at 72 DPI, which aligns directly with PDF points
 * (1pt = 1px at 72 DPI). Y-axis is top-down (same as our layout engine), so no flip needed.
 *
 * @param context Application context (unused for now, reserved for font loading in Phase 2).
 */
class AndroidLabelPdfRenderer(
    @Suppress("unused") private val context: Context,
) : LabelPdfRenderer {

    override suspend fun render(items: List<PrintQueueItem>, template: LabelTemplate): ByteArray =
        withContext(Dispatchers.IO) {
            require(items.isNotEmpty()) { "Cannot render an empty print job" }

            val positions = LabelLayoutEngine.computePositions(template, items.size)
            val pageCount = LabelLayoutEngine.pageCount(template, items.size)

            val pdfDoc = PdfDocument()
            try {
                val pageWidth: Int
                val pageHeight: Int
                when (template.paperType) {
                    LabelTemplate.PaperType.A4_SHEET -> {
                        pageWidth  = LabelLayoutEngine.A4_WIDTH_PT.toInt()
                        pageHeight = LabelLayoutEngine.A4_HEIGHT_PT.toInt()
                    }
                    LabelTemplate.PaperType.CONTINUOUS_ROLL -> {
                        pageWidth  = (template.paperWidthMm * LabelLayoutEngine.MM_TO_PT).toInt()
                        pageHeight = LabelLayoutEngine.continuousPageHeightPt(template, items.size).toInt()
                    }
                }

                val barPaint = Paint().apply {
                    color       = android.graphics.Color.BLACK
                    style       = Paint.Style.FILL
                    isAntiAlias = false
                }
                val textPaint = Paint().apply {
                    color       = android.graphics.Color.BLACK
                    isAntiAlias = true
                }
                val boldTextPaint = Paint().apply {
                    color          = android.graphics.Color.BLACK
                    isAntiAlias    = true
                    isFakeBoldText = true
                }

                // Group label positions by page
                positions.zip(items).groupBy { (pos, _) -> pos.pageIndex }
                    .forEach { (pageIdx, group) ->
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
                        val page     = pdfDoc.startPage(pageInfo)
                        val canvas: Canvas = page.canvas

                        group.forEach { (pos, item) ->
                            drawLabel(canvas, item, pos.xPt, pos.yPt, pos.widthPt, pos.heightPt,
                                      barPaint, textPaint, boldTextPaint)
                        }

                        pdfDoc.finishPage(page)
                    }

                val baos = ByteArrayOutputStream()
                pdfDoc.writeTo(baos)
                baos.toByteArray()
            } finally {
                pdfDoc.close()
            }
        }

    private fun drawLabel(
        canvas: Canvas,
        item: PrintQueueItem,
        x: Float, y: Float,
        width: Float, height: Float,
        barPaint: Paint,
        textPaint: Paint,
        boldTextPaint: Paint,
    ) {
        val barcodeType = detectBarcodeType(item.barcode)
        val modules     = BarcodeBarEncoder.encode(item.barcode, barcodeType)

        // ── Barcode bars ─────────────────────────────────────────────────────
        val barAreaWidth  = width * 0.88f
        val barAreaHeight = height * 0.58f
        val barXStart     = x + width * 0.06f
        val barYTop       = y + height * 0.04f
        val moduleWidth   = barAreaWidth / modules.size

        modules.forEachIndexed { i, isBlack ->
            if (isBlack) {
                val bx = barXStart + i * moduleWidth
                canvas.drawRect(bx, barYTop, bx + moduleWidth - 0.2f, barYTop + barAreaHeight, barPaint)
            }
        }

        // ── Product name ──────────────────────────────────────────────────────
        val nameFontSize = (height * 0.09f).coerceIn(5f, 8f)
        boldTextPaint.textSize = nameFontSize
        canvas.drawText(item.productName.take(24), x + 2f, y + height * 0.72f, boldTextPaint)

        // ── Price ─────────────────────────────────────────────────────────────
        val priceFontSize = (height * 0.11f).coerceIn(6f, 10f)
        boldTextPaint.textSize = priceFontSize
        canvas.drawText("%.2f".format(item.price), x + 2f, y + height * 0.86f, boldTextPaint)

        // ── SKU / barcode digits ──────────────────────────────────────────────
        val skuFontSize = (height * 0.075f).coerceIn(4f, 7f)
        textPaint.textSize = skuFontSize
        canvas.drawText((item.sku ?: item.barcode).take(20), x + 2f, y + height * 0.96f, textPaint)
    }
}

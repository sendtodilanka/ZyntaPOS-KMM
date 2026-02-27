package com.zyntasolutions.zyntapos.feature.inventory.label

import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.feature.inventory.BarcodeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/**
 * JVM (Desktop) implementation of [LabelPdfRenderer] using Apache PDFBox 3.0.3.
 *
 * Barcode bars are drawn as filled rectangles via [PDPageContentStream].
 * PDFBox uses a bottom-left coordinate origin, so Y coordinates are flipped.
 */
class JvmLabelPdfRenderer : LabelPdfRenderer {

    override suspend fun render(items: List<PrintQueueItem>, template: LabelTemplate): ByteArray =
        withContext(Dispatchers.IO) {
            require(items.isNotEmpty()) { "Cannot render an empty print job" }

            val doc = PDDocument()
            try {
                val positions = LabelLayoutEngine.computePositions(template, items.size)
                val pageCount = LabelLayoutEngine.pageCount(template, items.size)

                // Create all pages up front
                val pages: List<PDPage> = (0 until pageCount).map { _ ->
                    val page = when (template.paperType) {
                        LabelTemplate.PaperType.A4_SHEET -> PDPage(PDRectangle.A4)
                        LabelTemplate.PaperType.CONTINUOUS_ROLL -> {
                            val w = (template.paperWidthMm * LabelLayoutEngine.MM_TO_PT).toFloat()
                            val h = LabelLayoutEngine.continuousPageHeightPt(template, items.size)
                            PDPage(PDRectangle(w, h))
                        }
                    }
                    doc.addPage(page)
                    page
                }

                val helvetica     = PDType1Font(Standard14Fonts.FontName.HELVETICA)
                val helveticaBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

                // Group positions by page
                positions.zip(items).groupBy { (pos, _) -> pos.pageIndex }.forEach { (pageIdx, group) ->
                    val page       = pages[pageIdx]
                    val pageHeight = page.mediaBox.height
                    val stream     = PDPageContentStream(doc, page)
                    try {
                        group.forEach { (pos, item) ->
                            // PDFBox Y-axis is bottom-left: flip
                            val pdfY = pageHeight - pos.yPt - pos.heightPt
                            drawLabel(stream, item, pos.xPt, pdfY, pos.widthPt, pos.heightPt,
                                      helvetica, helveticaBold)
                        }
                    } finally {
                        stream.close()
                    }
                }

                val baos = ByteArrayOutputStream()
                doc.save(baos)
                baos.toByteArray()
            } finally {
                doc.close()
            }
        }

    private fun drawLabel(
        stream: PDPageContentStream,
        item: PrintQueueItem,
        x: Float, y: Float,
        width: Float, height: Float,
        font: PDType1Font,
        boldFont: PDType1Font,
    ) {
        val barcodeType = detectBarcodeType(item.barcode)
        val modules     = BarcodeBarEncoder.encode(item.barcode, barcodeType)

        // ── Barcode bars (top 58% of label height) ──────────────────────────
        val barAreaHeight = height * 0.58f
        val barAreaWidth  = width * 0.88f
        val barXStart     = x + width * 0.06f
        val barY          = y + height * 0.38f  // bottom of bar area in PDFBox coords
        val moduleWidth   = barAreaWidth / modules.size

        modules.forEachIndexed { i, isBlack ->
            if (isBlack) {
                stream.addRect(barXStart + i * moduleWidth, barY, moduleWidth - 0.2f, barAreaHeight)
                stream.fill()
            }
        }

        // ── Product name (line 1, ~36% from bottom of cell) ─────────────────
        val nameFontSize = (height * 0.09f).coerceIn(5f, 8f)
        val nameText     = item.productName.take(24)
        stream.beginText()
        stream.setFont(boldFont, nameFontSize)
        stream.newLineAtOffset(x + 2f, y + height * 0.27f)
        stream.showText(nameText)
        stream.endText()

        // ── Price (line 2, ~19% from bottom) ─────────────────────────────────
        val priceFontSize = (height * 0.11f).coerceIn(6f, 10f)
        val priceText     = "%.2f".format(item.price)
        stream.beginText()
        stream.setFont(boldFont, priceFontSize)
        stream.newLineAtOffset(x + 2f, y + height * 0.14f)
        stream.showText(priceText)
        stream.endText()

        // ── SKU / barcode number (line 3, ~5% from bottom) ───────────────────
        val skuFontSize = (height * 0.075f).coerceIn(4f, 7f)
        val skuText     = (item.sku ?: item.barcode).take(20)
        stream.beginText()
        stream.setFont(font, skuFontSize)
        stream.newLineAtOffset(x + 2f, y + height * 0.04f)
        stream.showText(skuText)
        stream.endText()
    }
}

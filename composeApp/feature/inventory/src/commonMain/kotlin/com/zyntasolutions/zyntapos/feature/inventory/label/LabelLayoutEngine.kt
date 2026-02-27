package com.zyntasolutions.zyntapos.feature.inventory.label

import com.zyntasolutions.zyntapos.domain.model.LabelTemplate

/**
 * Pure coordinate-math engine for barcode label printing.
 *
 * Converts [LabelTemplate] dimensions (in mm) to PDF point coordinates
 * (1 pt = 1/72 inch = 0.3528 mm, so 1 mm ≈ 2.8346 pt).
 *
 * Coordinate system: top-left origin (y grows downward).
 * Callers using PDFBox (bottom-left origin) must flip: `yPdf = pageHeightPt - yPt - heightPt`.
 */
object LabelLayoutEngine {

    const val MM_TO_PT = 72.0 / 25.4  // 2.8346...

    /** A4 page dimensions in points (ISO 216). */
    const val A4_WIDTH_PT  = 595.276f
    const val A4_HEIGHT_PT = 841.890f

    /** The position and size of a single label cell in PDF points. */
    data class LabelPosition(
        val pageIndex: Int,
        val xPt: Float,
        val yPt: Float,       // top-left corner, y grows downward from top of page
        val widthPt: Float,
        val heightPt: Float,
    )

    /**
     * Computes the [LabelPosition] for each label in a print job.
     *
     * @param template    The paper/layout configuration.
     * @param totalLabels Total number of labels to print (already quantity-expanded).
     * @return List of [LabelPosition]s, one per label, in order.
     */
    fun computePositions(template: LabelTemplate, totalLabels: Int): List<LabelPosition> {
        if (totalLabels <= 0) return emptyList()

        val labelWidthPt  = (template.labelWidthMm  * MM_TO_PT).toFloat()
        val labelHeightPt = (template.labelHeightMm * MM_TO_PT).toFloat()
        val gapHPt        = (template.gapHorizontalMm * MM_TO_PT).toFloat()
        val gapVPt        = (template.gapVerticalMm   * MM_TO_PT).toFloat()
        val marginTopPt   = (template.marginTopMm   * MM_TO_PT).toFloat()
        val marginLeftPt  = (template.marginLeftMm  * MM_TO_PT).toFloat()

        return when (template.paperType) {
            LabelTemplate.PaperType.CONTINUOUS_ROLL -> computeContinuous(
                totalLabels, template.columns, labelWidthPt, labelHeightPt,
                gapHPt, gapVPt, marginTopPt, marginLeftPt,
            )
            LabelTemplate.PaperType.A4_SHEET -> computeA4(
                totalLabels, template.columns, template.rows,
                labelWidthPt, labelHeightPt, gapHPt, gapVPt, marginTopPt, marginLeftPt,
            )
        }
    }

    /** Total number of PDF pages needed. CONTINUOUS always returns 1. */
    fun pageCount(template: LabelTemplate, totalLabels: Int): Int =
        when (template.paperType) {
            LabelTemplate.PaperType.CONTINUOUS_ROLL -> 1
            LabelTemplate.PaperType.A4_SHEET -> {
                val perPage = template.columns * template.rows
                if (perPage <= 0) 1 else (totalLabels + perPage - 1) / perPage
            }
        }

    /**
     * Continuous-roll page height in points.
     * Width is always [template.paperWidthMm] converted to pt.
     */
    fun continuousPageHeightPt(template: LabelTemplate, totalLabels: Int): Float {
        val rows = (totalLabels + template.columns - 1) / template.columns
        return ((template.marginTopMm + rows * template.labelHeightMm +
                (rows - 1) * template.gapVerticalMm) * MM_TO_PT).toFloat()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun computeContinuous(
        totalLabels: Int,
        columns: Int,
        labelWidthPt: Float,
        labelHeightPt: Float,
        gapHPt: Float,
        gapVPt: Float,
        marginTopPt: Float,
        marginLeftPt: Float,
    ): List<LabelPosition> = buildList {
        for (i in 0 until totalLabels) {
            val col = i % columns
            val row = i / columns
            add(
                LabelPosition(
                    pageIndex = 0,
                    xPt       = marginLeftPt + col * (labelWidthPt + gapHPt),
                    yPt       = marginTopPt + row * (labelHeightPt + gapVPt),
                    widthPt   = labelWidthPt,
                    heightPt  = labelHeightPt,
                )
            )
        }
    }

    private fun computeA4(
        totalLabels: Int,
        columns: Int,
        rows: Int,
        labelWidthPt: Float,
        labelHeightPt: Float,
        gapHPt: Float,
        gapVPt: Float,
        marginTopPt: Float,
        marginLeftPt: Float,
    ): List<LabelPosition> {
        val labelsPerPage = (columns * rows).coerceAtLeast(1)
        return buildList {
            for (i in 0 until totalLabels) {
                val pageIndex = i / labelsPerPage
                val posOnPage = i % labelsPerPage
                val col       = posOnPage % columns
                val row       = posOnPage / columns
                add(
                    LabelPosition(
                        pageIndex = pageIndex,
                        xPt       = marginLeftPt + col * (labelWidthPt + gapHPt),
                        yPt       = marginTopPt + row * (labelHeightPt + gapVPt),
                        widthPt   = labelWidthPt,
                        heightPt  = labelHeightPt,
                    )
                )
            }
        }
    }
}

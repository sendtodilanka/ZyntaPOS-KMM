package com.zyntasolutions.zyntapos.domain.model

/**
 * Reusable paper layout template for barcode label printing.
 *
 * Two paper modes:
 * - [PaperType.CONTINUOUS_ROLL]: thermal roll (58mm/80mm). [rows] = 0 (unlimited).
 * - [PaperType.A4_SHEET]: A4 laser/inkjet. [rows] is the labels-per-column count.
 *
 * All measurements in millimetres (Double).
 */
data class LabelTemplate(
    val id: String,
    val name: String,
    val paperType: PaperType,
    val paperWidthMm: Double,
    val labelHeightMm: Double,
    val columns: Int,
    val rows: Int,
    val gapHorizontalMm: Double,
    val gapVerticalMm: Double,
    val marginTopMm: Double,
    val marginBottomMm: Double,
    val marginLeftMm: Double,
    val marginRightMm: Double,
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
) {
    enum class PaperType { CONTINUOUS_ROLL, A4_SHEET }

    /** Computed label width from paper width, margins, gap, and column count. */
    val labelWidthMm: Double
        get() = (paperWidthMm - marginLeftMm - marginRightMm - (columns - 1) * gapHorizontalMm) / columns.toDouble()
}

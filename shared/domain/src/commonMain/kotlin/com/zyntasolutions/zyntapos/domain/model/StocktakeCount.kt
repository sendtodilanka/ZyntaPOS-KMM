package com.zyntasolutions.zyntapos.domain.model

/**
 * A single product count entry within a [StocktakeSession].
 *
 * Captures both the system (book) quantity at the time of counting and the
 * physically counted quantity, allowing variance calculation.
 *
 * @property productId    UUID of the product being counted.
 * @property barcode      Barcode used to identify the product during scanning.
 * @property productName  Display name for the product (denormalised for reports).
 * @property systemQty    Book quantity from the system at session start.
 * @property countedQty   Physically counted quantity; `null` if not yet counted.
 * @property variance     Difference (`countedQty - systemQty`); `null` if not yet counted.
 * @property scannedAt    Timestamp of the most recent scan/update for this entry.
 */
data class StocktakeCount(
    val productId: String,
    val barcode: String,
    val productName: String,
    val systemQty: Int,
    val countedQty: Int? = null,
    val variance: Int? = null,
    val scannedAt: Long,
) {
    /** Computed variance: `countedQty - systemQty`. Returns `null` if not yet counted. */
    val computedVariance: Int?
        get() = countedQty?.let { it - systemQty }
}

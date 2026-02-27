package com.zyntasolutions.zyntapos.domain.model

/**
 * A single item to print on a barcode label.
 *
 * Passed to [com.zyntasolutions.zyntapos.domain.printer.LabelPrinterPort.printLabels]
 * and to the inventory label-print use cases. The printer port implementation
 * maps these fields to ZPL, TSPL, or PDF output according to the active
 * [LabelPrinterConfig].
 *
 * @param productId   UUID of the source [Product] (used for audit trail).
 * @param productName Human-readable product name rendered on the label.
 * @param barcode     Barcode value to encode (EAN-13, Code 128, QR, etc.).
 * @param price       Regular selling price.
 * @param salePrice   Optional promotional / sale price. Rendered beside [price]
 *                    when [LabelTemplate.showSalePrice] is `true`.
 * @param expiryDate  Human-readable expiry string (e.g., "2026-12-31") rendered
 *                    when [LabelTemplate.showExpiryDate] is `true`.
 * @param batchNumber Lot / batch number rendered when
 *                    [LabelTemplate.showBatchNumber] is `true`.
 * @param copies      Number of identical label copies to print for this item.
 *                    Must be ≥ 1.
 */
data class LabelPrintItem(
    val productId: String,
    val productName: String,
    val barcode: String,
    val price: Double,
    val salePrice: Double? = null,
    val expiryDate: String? = null,
    val batchNumber: String? = null,
    val copies: Int = 1,
) {
    init {
        require(copies >= 1) { "LabelPrintItem.copies must be >= 1, was $copies" }
    }
}

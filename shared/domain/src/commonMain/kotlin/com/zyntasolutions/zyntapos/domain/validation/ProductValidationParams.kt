package com.zyntasolutions.zyntapos.domain.validation

/**
 * Plain-value carrier for product form field validation.
 *
 * This type lives in `:shared:domain` so that [ProductValidator] has no compile-time
 * dependency on any UI or feature-module class.  The presentation layer is responsible
 * for mapping its own form state to this object before calling the validator.
 *
 * All fields are raw [String] values as received from text inputs.  The validator
 * itself handles blank / null / non-parseable edge cases.
 *
 * @param name        Product display name (required, non-blank).
 * @param barcode     EAN/Code128 barcode string (optional; if non-blank must be ≥ 4 chars).
 * @param sku         Stock-Keeping Unit code (optional, no format constraint in Phase 1).
 * @param categoryId  Selected category identifier (required, non-blank).
 * @param unitId      Selected unit-of-measure identifier (required, non-blank).
 * @param price       Retail selling price as a string (required, must parse to ≥ 0.0).
 * @param costPrice   Cost / purchase price as a string (optional; if non-blank must parse to ≥ 0.0).
 * @param stockQty    Initial on-hand quantity as a string (optional; if non-blank must parse to ≥ 0.0).
 * @param minStockQty Low-stock alert threshold as a string (optional; if non-blank must parse to ≥ 0.0).
 */
data class ProductValidationParams(
    val name: String,
    val barcode: String = "",
    val sku: String = "",
    val categoryId: String,
    val unitId: String,
    val price: String,
    val costPrice: String = "",
    val stockQty: String = "",
    val minStockQty: String = "",
)

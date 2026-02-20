package com.zyntasolutions.zyntapos.domain.model

/**
 * A specific variant of a [Product] (e.g., "Red / Large", "500ml").
 *
 * Variants share the parent product's category and tax group but may differ
 * in price, barcode, and stock level.
 *
 * @property id Unique identifier (UUID v4).
 * @property productId FK to the parent [Product].
 * @property name Human-readable variant label (e.g., "Blue — XL").
 * @property attributes Key-value map of variant attributes
 *                      (e.g., `{"Color": "Blue", "Size": "XL"}`).
 * @property price Override sell price. If null, inherits [Product.price].
 * @property stock On-hand quantity specific to this variant.
 * @property barcode Optional variant-specific barcode. Must be unique if set.
 */
data class ProductVariant(
    val id: String,
    val productId: String,
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val price: Double? = null,
    val stock: Double = 0.0,
    val barcode: String? = null,
)

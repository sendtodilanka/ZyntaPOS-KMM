package com.zyntasolutions.zyntapos.domain.model

/**
 * A named tax configuration applied to products at the point of sale.
 *
 * Tax calculation behaviour depends on [isInclusive]:
 * - **Exclusive** (`isInclusive = false`): `taxAmount = lineTotal * (rate / 100)`
 * - **Inclusive** (`isInclusive = true`): `taxAmount = lineTotal - (lineTotal / (1 + rate / 100))`
 *
 * Both modes are handled by `CalculateOrderTotalsUseCase`.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Display label (e.g., "VAT 15%", "Service Charge 10%").
 * @property rate Tax percentage in the range 0.0–100.0.
 * @property isInclusive If true the product's [Product.price] already includes this tax.
 *                       If false the tax is added on top of the price at checkout.
 * @property isActive If false this tax group is hidden from new product assignments
 *                    but remains on existing products for historical accuracy.
 */
data class TaxGroup(
    val id: String,
    val name: String,
    val rate: Double,
    val isInclusive: Boolean = false,
    val isActive: Boolean = true,
) {
    init {
        require(rate in 0.0..100.0) { "Tax rate must be between 0.0 and 100.0, got $rate" }
    }
}

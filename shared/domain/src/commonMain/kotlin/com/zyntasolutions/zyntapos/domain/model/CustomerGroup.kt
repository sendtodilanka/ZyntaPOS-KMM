package com.zyntasolutions.zyntapos.domain.model

/**
 * A group that assigns shared pricing rules to a set of customers.
 *
 * Group pricing is applied automatically at POS when a customer belonging
 * to this group is selected. The discount is layered on top of the base price.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Display name (e.g., "VIP", "Wholesale", "Staff").
 * @property description Optional description.
 * @property discountType Discount calculation method. Null = no automatic discount.
 * @property discountValue Amount or percentage depending on [discountType].
 * @property priceType Determines which price tier is used for this group.
 */
data class CustomerGroup(
    val id: String,
    val name: String,
    val description: String? = null,
    val discountType: DiscountType? = null,
    val discountValue: Double = 0.0,
    val priceType: PriceType = PriceType.RETAIL,
) {
    enum class PriceType { RETAIL, WHOLESALE, CUSTOM }

    init {
        require(discountValue >= 0.0) { "Discount value cannot be negative" }
    }
}

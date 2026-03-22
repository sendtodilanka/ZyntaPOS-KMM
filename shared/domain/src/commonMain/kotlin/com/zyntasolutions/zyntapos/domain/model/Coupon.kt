package com.zyntasolutions.zyntapos.domain.model

/**
 * A discount coupon that can be applied to orders at the POS.
 *
 * @property id Unique identifier (UUID v4).
 * @property code Short alphanumeric code entered by the cashier (e.g., "SUMMER20").
 * @property name Human-readable coupon name.
 * @property discountType How the discount is calculated.
 * @property discountValue Amount off (FLAT) or percentage off (PERCENTAGE).
 * @property minimumPurchase Minimum cart total required to apply the coupon.
 * @property maximumDiscount Maximum monetary discount cap for PERCENTAGE coupons. Null = unlimited.
 * @property usageLimit Maximum total redemptions. Null = unlimited.
 * @property usageCount Number of times this coupon has been redeemed so far.
 * @property perCustomerLimit Max uses per customer. Null = unlimited.
 * @property scope Restricts coupon to a subset of cart items or customers.
 * @property scopeIds IDs of products, categories, or customers the coupon applies to.
 * @property validFrom Epoch millis from which the coupon is valid.
 * @property validTo Epoch millis at which the coupon expires.
 * @property isActive Whether the coupon is enabled.
 */
data class Coupon(
    val id: String,
    val code: String,
    val name: String,
    val discountType: DiscountType,
    val discountValue: Double,
    val minimumPurchase: Double = 0.0,
    val maximumDiscount: Double? = null,
    val usageLimit: Int? = null,
    val usageCount: Int = 0,
    val perCustomerLimit: Int? = null,
    val scope: CouponScope = CouponScope.CART,
    val scopeIds: List<String> = emptyList(),
    val validFrom: Long,
    val validTo: Long,
    val isActive: Boolean = true,
    /** Store this coupon is scoped to. Null = global (applies to all stores). */
    val storeId: String? = null,
) {
    enum class CouponScope { CART, PRODUCT, CATEGORY, CUSTOMER }

    init {
        require(discountValue >= 0.0) { "Discount value cannot be negative" }
        require(minimumPurchase >= 0.0) { "Minimum purchase cannot be negative" }
        require(usageCount >= 0) { "Usage count cannot be negative" }
        require(validFrom < validTo) { "validFrom must be before validTo" }
    }

    /** Returns true if the coupon has available redemptions. */
    fun hasAvailableRedemptions(): Boolean = usageLimit == null || usageCount < usageLimit
}

/**
 * Records that a specific coupon was applied to an order.
 *
 * @property id Unique identifier (UUID v4).
 * @property couponId FK to the applied [Coupon].
 * @property orderId FK to the order.
 * @property customerId FK to the customer, if a customer was attached to the order.
 * @property discountAmount Actual monetary discount applied to this order.
 * @property usedAt Epoch millis when the coupon was redeemed.
 */
data class CouponUsage(
    val id: String,
    val couponId: String,
    val orderId: String,
    val customerId: String? = null,
    val discountAmount: Double,
    val usedAt: Long,
)

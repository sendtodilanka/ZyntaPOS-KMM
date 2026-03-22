package com.zyntasolutions.zyntapos.feature.coupons

import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.DiscountType

/**
 * Immutable UI state for the Coupons feature screens.
 *
 * Consumed by [CouponListScreen] and [CouponDetailScreen].
 *
 * @property coupons Full list of coupons (filtered per [showActiveOnly]).
 * @property showActiveOnly If true, only active and currently-valid coupons are shown.
 * @property selectedCoupon Coupon loaded for detail/edit; null = list mode.
 * @property formState Mutable draft for create/edit coupon form.
 * @property usageHistory Usage ledger for the selected coupon.
 * @property isLoading True while an async operation is in flight.
 * @property error Transient error message; null = no error.
 * @property successMessage Transient success message; null = no message.
 */
data class CouponState(
    // ── List ──────────────────────────────────────────────────────────────
    val coupons: List<Coupon> = emptyList(),
    val showActiveOnly: Boolean = false,

    // ── Detail / Edit ─────────────────────────────────────────────────────
    val selectedCoupon: Coupon? = null,
    val formState: CouponFormState = CouponFormState(),
    val usageHistory: List<CouponUsage> = emptyList(),

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/**
 * Mutable form fields for coupon create/edit operations.
 *
 * All numeric/date fields stored as [String] for direct TextField binding;
 * parsed on save.
 */
data class CouponFormState(
    val id: String? = null,
    val code: String = "",
    val name: String = "",
    val discountType: String = DiscountType.PERCENT.name,
    val discountValue: String = "",
    val minimumPurchase: String = "0",
    val maximumDiscount: String = "",
    val usageLimit: String = "",
    val perCustomerLimit: String = "",
    val validFrom: String = "",
    val validTo: String = "",
    val isActive: Boolean = true,
    /** C2.4: Store scope — null = global (all stores), non-null = store-specific. */
    val storeId: String? = null,
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)

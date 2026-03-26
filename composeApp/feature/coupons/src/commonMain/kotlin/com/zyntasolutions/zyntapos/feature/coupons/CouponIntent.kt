package com.zyntasolutions.zyntapos.feature.coupons

/**
 * All user actions for the Coupons feature, dispatched via
 * [CouponViewModel.handleIntent].
 */
sealed interface CouponIntent {

    // ── List ──────────────────────────────────────────────────────────────
    /** Trigger initial coupon list load (or refresh). */
    data object LoadCoupons : CouponIntent

    /** Toggle between showing all coupons vs. active-only. */
    data class ToggleActiveFilter(val showActiveOnly: Boolean) : CouponIntent

    // ── Detail Navigation ─────────────────────────────────────────────────
    /** Navigate to create/edit form. [couponId] null → create new. */
    data class SelectCoupon(val couponId: String?) : CouponIntent

    // ── Form Editing ──────────────────────────────────────────────────────
    /** Update a string field in the coupon form. */
    data class UpdateFormField(val field: String, val value: String) : CouponIntent

    /** Toggle the is_active switch on the form. */
    data class UpdateIsActive(val isActive: Boolean) : CouponIntent

    /** G12: Update the coupon targeting scope (CART, PRODUCT, CATEGORY, CUSTOMER). */
    data class UpdateScope(val scope: String) : CouponIntent

    /** G12: Toggle a scope ID (add/remove from selected list). */
    data class ToggleScopeId(val id: String) : CouponIntent

    /** G12: Auto-generate a random coupon code. */
    data object GenerateCode : CouponIntent

    /** Validate and persist the current [CouponFormState]. */
    data object SaveCoupon : CouponIntent

    /** Delete a coupon by id. */
    data class DeleteCoupon(val couponId: String) : CouponIntent

    /** Flip the active flag on a coupon from the list row (quick toggle). */
    data class ToggleCouponActive(val couponId: String, val isActive: Boolean) : CouponIntent

    /** Dismiss any transient error or success message. */
    data object DismissMessage : CouponIntent

    /** G12: Load coupon analytics — redemption counts, total discount given, top redeemed. */
    data object LoadAnalytics : CouponIntent
}

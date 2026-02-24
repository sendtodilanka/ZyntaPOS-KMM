package com.zyntasolutions.zyntapos.feature.coupons

/**
 * One-shot side effects for the Coupons feature, delivered via the effects Channel.
 */
sealed interface CouponEffect {
    /** Navigate to coupon detail/edit screen. [couponId] null → create new. */
    data class NavigateToDetail(val couponId: String?) : CouponEffect

    /** Navigate back to the coupon list. */
    data object NavigateToList : CouponEffect

    /** Show a transient error snackbar/toast. */
    data class ShowError(val message: String) : CouponEffect

    /** Show a transient success snackbar/toast. */
    data class ShowSuccess(val message: String) : CouponEffect
}

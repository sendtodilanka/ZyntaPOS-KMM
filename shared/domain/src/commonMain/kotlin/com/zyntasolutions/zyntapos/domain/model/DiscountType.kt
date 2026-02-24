package com.zyntasolutions.zyntapos.domain.model

/** Discount calculation mode. */
enum class DiscountType {
    /** Discount is a fixed monetary amount (e.g., LKR 50 off). */
    FIXED,

    /** Discount is a percentage of the line total or order total (e.g., 10% off). */
    PERCENT,

    /** Buy-one-get-one — handled by the promotion engine, not the coupon discount calculator. */
    BOGO,
}

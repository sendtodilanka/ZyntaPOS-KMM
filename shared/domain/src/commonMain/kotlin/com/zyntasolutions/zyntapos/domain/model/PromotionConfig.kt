package com.zyntasolutions.zyntapos.domain.model

/**
 * Typed configuration for a [Promotion] rule.
 *
 * Each [PromotionType] variant maps to a specific [PromotionConfig] subclass.
 * The config is stored as JSON in the database and parsed by [CouponRepositoryImpl]
 * at read time; the domain layer only works with typed values.
 *
 * Replaces the untyped [Promotion.config] JSON string (C2.4 technical debt item).
 */
sealed class PromotionConfig {

    /** Fallback config used when the stored JSON cannot be parsed. */
    data object Unknown : PromotionConfig()

    /**
     * Configuration for [PromotionType.BUY_X_GET_Y] promotions.
     *
     * @property buyQty  Number of units the customer must buy.
     * @property getQty  Number of units the customer receives for free/discounted.
     * @property targetProductId  Specific product ID this rule applies to. `null` = any product.
     * @property discountPct Percentage discount on the free units (100 = fully free, 50 = half-price).
     */
    data class BuyXGetY(
        val buyQty: Int,
        val getQty: Int,
        val targetProductId: String? = null,
        val discountPct: Double = 100.0,
    ) : PromotionConfig()

    /**
     * Configuration for [PromotionType.BUNDLE] promotions.
     *
     * @property productIds  Product IDs that must all be present in the cart for the bundle to apply.
     * @property bundlePrice Fixed price for the entire bundle (overrides individual product prices).
     */
    data class Bundle(
        val productIds: List<String>,
        val bundlePrice: Double,
    ) : PromotionConfig()

    /**
     * Configuration for [PromotionType.FLASH_SALE] promotions.
     *
     * @property discountPct Percentage off the regular price (e.g., 20.0 = 20% off).
     * @property targetProductIds  Product IDs subject to the flash sale. Empty = all products.
     * @property targetCategoryIds Category IDs subject to the flash sale. Empty = all categories.
     */
    data class FlashSale(
        val discountPct: Double,
        val targetProductIds: List<String> = emptyList(),
        val targetCategoryIds: List<String> = emptyList(),
    ) : PromotionConfig()

    /**
     * Configuration for [PromotionType.SCHEDULED] recurring promotions.
     *
     * @property discountPct Percentage off on the scheduled days.
     * @property dayOfWeek ISO day-of-week (1 = Monday, 7 = Sunday). `null` = applies every day.
     */
    data class Scheduled(
        val discountPct: Double,
        val dayOfWeek: Int? = null,
    ) : PromotionConfig()
}

package com.zyntasolutions.zyntapos.domain.model

/**
 * A pricing rule that overrides a product's base price for specific stores.
 *
 * Pricing rules enable region-based pricing by allowing administrators to
 * set store-specific prices with optional time-bounded validity periods.
 *
 * ### Price Resolution Order (in [GetEffectiveProductPriceUseCase]):
 * 1. [StoreProductOverride.localPrice] — per-store override (highest priority)
 * 2. **PricingRule** — store-level or global rule (this model) ← active rule with highest priority
 * 3. [MasterProduct.basePrice] — global default
 * 4. [Product.price] — legacy fallback
 *
 * @property id Unique identifier (UUID v4).
 * @property productId FK to the product this rule applies to.
 * @property storeId FK to the target store. Null = global rule (applies to all stores without a specific rule).
 * @property price The override price when this rule is active.
 * @property costPrice Optional override cost price for margin calculations.
 * @property priority Higher value = higher precedence when multiple rules match.
 * @property validFrom Start of the validity window (epoch milliseconds). Null = no start constraint.
 * @property validTo End of the validity window (epoch milliseconds). Null = no end constraint.
 * @property isActive Whether this rule is currently enabled.
 * @property description Human-readable label for this pricing rule (e.g., "Summer Sale", "Colombo Region").
 * @property createdAt Epoch milliseconds of record creation.
 * @property updatedAt Epoch milliseconds of the last modification.
 */
data class PricingRule(
    val id: String,
    val productId: String,
    val storeId: String? = null,
    val price: Double,
    val costPrice: Double? = null,
    val priority: Int = 0,
    val validFrom: Long? = null,
    val validTo: Long? = null,
    val isActive: Boolean = true,
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

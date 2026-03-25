package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.model.PromotionConfig
import com.zyntasolutions.zyntapos.domain.model.PromotionType

/**
 * C2.4: Evaluates a list of active store promotions against the current cart
 * and returns the total monetary discount to apply.
 *
 * Promotions are evaluated in [Promotion.priority] order (highest first). The first
 * matching promotion of each type wins (no stacking between promotions of the same type,
 * but different types can stack — e.g., a FLASH_SALE can coexist with a BUY_X_GET_Y).
 *
 * ### Promotion evaluation rules
 * - **FLASH_SALE**: Applies [PromotionConfig.FlashSale.discountPct] to matching products/categories.
 *   If [PromotionConfig.FlashSale.targetProductIds] and [PromotionConfig.FlashSale.targetCategoryIds]
 *   are both empty, applies to the entire cart subtotal.
 * - **BUY_X_GET_Y**: If the cart contains at least [PromotionConfig.BuyXGetY.buyQty] units of the
 *   target product (or any product if `targetProductId` is null), discounts
 *   [PromotionConfig.BuyXGetY.getQty] units by [PromotionConfig.BuyXGetY.discountPct] percent.
 * - **BUNDLE**: Checks if all [PromotionConfig.Bundle.productIds] are present in the cart.
 *   If so, the discount is the difference between the bundle price and the sum of individual prices.
 * - **SCHEDULED**: Same as FLASH_SALE discount calculation (schedule validity handled by
 *   [GetStorePromotionsUseCase] which filters by active/valid dates).
 *
 * This use case is pure (no repository calls) — it operates only on the provided cart items
 * and already-fetched promotions.
 */
class ApplyStorePromotionsUseCase {

    /**
     * @param cartItems   Current cart line items.
     * @param promotions  Active promotions for the current store (sorted by priority descending).
     * @return Total monetary discount to apply to the cart. Never negative.
     */
    operator fun invoke(
        cartItems: List<CartItem>,
        promotions: List<Promotion>,
    ): Double {
        if (cartItems.isEmpty() || promotions.isEmpty()) return 0.0

        var totalDiscount = 0.0
        val cartSubtotal = cartItems.sumOf { it.lineTotal }

        // Track which promotion types have already been applied (no same-type stacking)
        val appliedTypes = mutableSetOf<PromotionType>()

        for (promotion in promotions.sortedByDescending { it.priority }) {
            if (promotion.type in appliedTypes) continue

            val discount = when (val cfg = promotion.config) {
                is PromotionConfig.FlashSale  -> calculateFlashSaleDiscount(cartItems, cfg, cartSubtotal)
                is PromotionConfig.BuyXGetY   -> calculateBuyXGetYDiscount(cartItems, cfg)
                is PromotionConfig.Bundle     -> calculateBundleDiscount(cartItems, cfg)
                is PromotionConfig.Scheduled  -> calculateFlashSaleDiscount(
                    cartItems,
                    PromotionConfig.FlashSale(cfg.discountPct),
                    cartSubtotal,
                )
                is PromotionConfig.Unknown    -> 0.0
            }

            if (discount > 0.0) {
                totalDiscount += discount
                appliedTypes += promotion.type
            }
        }

        return totalDiscount.coerceAtLeast(0.0).coerceAtMost(cartSubtotal)
    }

    private fun calculateFlashSaleDiscount(
        cartItems: List<CartItem>,
        cfg: PromotionConfig.FlashSale,
        cartSubtotal: Double,
    ): Double {
        val base = when {
            cfg.targetProductIds.isEmpty() && cfg.targetCategoryIds.isEmpty() -> cartSubtotal
            else -> cartItems
                .filter { item ->
                    item.productId in cfg.targetProductIds ||
                        item.categoryId in cfg.targetCategoryIds
                }
                .sumOf { it.lineTotal }
        }
        return base * cfg.discountPct / 100.0
    }

    private fun calculateBuyXGetYDiscount(
        cartItems: List<CartItem>,
        cfg: PromotionConfig.BuyXGetY,
    ): Double {
        val eligibleItems = if (cfg.targetProductId != null) {
            cartItems.filter { it.productId == cfg.targetProductId }
        } else {
            cartItems
        }

        val totalQty = eligibleItems.sumOf { it.quantity }
        if (totalQty < cfg.buyQty) return 0.0

        val sets = (totalQty / (cfg.buyQty + cfg.getQty)).toLong()
        if (sets == 0L) return 0.0

        // Find unit price from the cheapest matching item (most customer-friendly)
        val unitPrice = eligibleItems.minOfOrNull { it.unitPrice } ?: return 0.0
        val freeUnits = sets * cfg.getQty
        return freeUnits * unitPrice * cfg.discountPct / 100.0
    }

    private fun calculateBundleDiscount(
        cartItems: List<CartItem>,
        cfg: PromotionConfig.Bundle,
    ): Double {
        if (cfg.productIds.isEmpty()) return 0.0
        val bundleItemsInCart = cartItems.filter { it.productId in cfg.productIds }
        val coveredProductIds = bundleItemsInCart.map { it.productId }.toSet()
        if (!coveredProductIds.containsAll(cfg.productIds)) return 0.0

        val individualTotal = bundleItemsInCart
            .filter { it.productId in cfg.productIds }
            .sumOf { it.unitPrice }
        return (individualTotal - cfg.bundlePrice).coerceAtLeast(0.0)
    }
}

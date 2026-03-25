package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.model.PromotionConfig
import com.zyntasolutions.zyntapos.domain.model.PromotionType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ApplyStorePromotionsUseCase] — pure function, no coroutines needed.
 *
 * Coverage:
 * - Empty cart / empty promotions → 0.0
 * - FLASH_SALE: entire cart discount
 * - FLASH_SALE: product-targeted discount
 * - FLASH_SALE: category-targeted discount
 * - FLASH_SALE: no matching products in cart → 0.0
 * - BUY_X_GET_Y: basic BOGO (buy 2 get 1 free)
 * - BUY_X_GET_Y: not enough quantity → 0.0
 * - BUY_X_GET_Y: targeted product only
 * - BUNDLE: all bundle products in cart → discount
 * - BUNDLE: missing bundle product → 0.0
 * - SCHEDULED: computed same as FLASH_SALE
 * - Unknown config → 0.0
 * - Same-type stacking prevention (only first FLASH_SALE wins)
 * - Cross-type stacking allowed (FLASH_SALE + BUY_X_GET_Y both apply)
 * - Priority ordering (higher priority applied first)
 * - Discount never exceeds cart subtotal
 */
class ApplyStorePromotionsUseCaseTest {

    private val useCase = ApplyStorePromotionsUseCase()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeFlashSalePromotion(
        id: String = "promo-flash",
        discountPct: Double = 10.0,
        targetProductIds: List<String> = emptyList(),
        targetCategoryIds: List<String> = emptyList(),
        priority: Int = 0,
    ) = Promotion(
        id = id,
        name = "Flash Sale",
        type = PromotionType.FLASH_SALE,
        config = PromotionConfig.FlashSale(
            discountPct = discountPct,
            targetProductIds = targetProductIds,
            targetCategoryIds = targetCategoryIds,
        ),
        validFrom = 0L,
        validTo = Long.MAX_VALUE,
        priority = priority,
        isActive = true,
    )

    private fun makeBogoPromotion(
        id: String = "promo-bogo",
        buyQty: Int = 2,
        getQty: Int = 1,
        discountPct: Double = 100.0,
        targetProductId: String? = null,
        priority: Int = 0,
    ) = Promotion(
        id = id,
        name = "Buy $buyQty Get $getQty",
        type = PromotionType.BUY_X_GET_Y,
        config = PromotionConfig.BuyXGetY(
            buyQty = buyQty,
            getQty = getQty,
            discountPct = discountPct,
            targetProductId = targetProductId,
        ),
        validFrom = 0L,
        validTo = Long.MAX_VALUE,
        priority = priority,
        isActive = true,
    )

    private fun makeBundlePromotion(
        id: String = "promo-bundle",
        productIds: List<String> = listOf("p1", "p2"),
        bundlePrice: Double = 15.0,
        priority: Int = 0,
    ) = Promotion(
        id = id,
        name = "Bundle Deal",
        type = PromotionType.BUNDLE,
        config = PromotionConfig.Bundle(productIds = productIds, bundlePrice = bundlePrice),
        validFrom = 0L,
        validTo = Long.MAX_VALUE,
        priority = priority,
        isActive = true,
    )

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty cart returns 0`() {
        val result = useCase(emptyList(), listOf(makeFlashSalePromotion()))
        assertEquals(0.0, result)
    }

    @Test
    fun `empty promotions returns 0`() {
        val cart = listOf(buildCartItem(unitPrice = 100.0, lineTotal = 100.0))
        assertEquals(0.0, useCase(cart, emptyList()))
    }

    // ── FLASH_SALE ────────────────────────────────────────────────────────────

    @Test
    fun `flash sale on entire cart - 10 percent off`() {
        val cart = listOf(
            buildCartItem(productId = "p1", unitPrice = 50.0, lineTotal = 50.0),
            buildCartItem(productId = "p2", unitPrice = 50.0, lineTotal = 50.0),
        )
        val result = useCase(cart, listOf(makeFlashSalePromotion(discountPct = 10.0)))
        assertEquals(10.0, result, 0.001)
    }

    @Test
    fun `flash sale targeted at specific product`() {
        val cart = listOf(
            buildCartItem(productId = "prod-target", unitPrice = 60.0, lineTotal = 60.0),
            buildCartItem(productId = "prod-other", unitPrice = 40.0, lineTotal = 40.0),
        )
        val result = useCase(
            cart,
            listOf(makeFlashSalePromotion(discountPct = 20.0, targetProductIds = listOf("prod-target"))),
        )
        // 20% of 60 = 12
        assertEquals(12.0, result, 0.001)
    }

    @Test
    fun `flash sale targeted at category`() {
        val cart = listOf(
            buildCartItem(productId = "p1", categoryId = "cat-drinks", unitPrice = 30.0, lineTotal = 30.0),
            buildCartItem(productId = "p2", categoryId = "cat-food", unitPrice = 70.0, lineTotal = 70.0),
        )
        val result = useCase(
            cart,
            listOf(makeFlashSalePromotion(discountPct = 50.0, targetCategoryIds = listOf("cat-drinks"))),
        )
        // 50% of 30 = 15
        assertEquals(15.0, result, 0.001)
    }

    @Test
    fun `flash sale with no matching products returns 0`() {
        val cart = listOf(buildCartItem(productId = "prod-other", unitPrice = 100.0, lineTotal = 100.0))
        val result = useCase(
            cart,
            listOf(makeFlashSalePromotion(discountPct = 25.0, targetProductIds = listOf("prod-missing"))),
        )
        assertEquals(0.0, result)
    }

    // ── BUY_X_GET_Y ───────────────────────────────────────────────────────────

    @Test
    fun `bogo buy 2 get 1 free - 3 items in cart`() {
        // 3 items at £10 each → 1 free item = £10 discount
        val cart = listOf(
            buildCartItem(productId = "p1", unitPrice = 10.0, quantity = 3.0, lineTotal = 30.0),
        )
        val result = useCase(cart, listOf(makeBogoPromotion(buyQty = 2, getQty = 1, discountPct = 100.0)))
        assertEquals(10.0, result, 0.001)
    }

    @Test
    fun `bogo not enough quantity returns 0`() {
        val cart = listOf(buildCartItem(productId = "p1", unitPrice = 10.0, quantity = 1.0, lineTotal = 10.0))
        val result = useCase(cart, listOf(makeBogoPromotion(buyQty = 2, getQty = 1)))
        assertEquals(0.0, result)
    }

    @Test
    fun `bogo targeted at specific product - other items ignored`() {
        val cart = listOf(
            buildCartItem(productId = "target", unitPrice = 20.0, quantity = 3.0, lineTotal = 60.0),
            buildCartItem(productId = "other", unitPrice = 50.0, quantity = 5.0, lineTotal = 250.0),
        )
        val result = useCase(
            cart,
            listOf(makeBogoPromotion(buyQty = 2, getQty = 1, discountPct = 100.0, targetProductId = "target")),
        )
        // 3 target items: 1 set of (2+1), 1 free item at £20 = discount £20
        assertEquals(20.0, result, 0.001)
    }

    // ── BUNDLE ────────────────────────────────────────────────────────────────

    @Test
    fun `bundle - all products in cart - discount applied`() {
        val cart = listOf(
            buildCartItem(productId = "p1", unitPrice = 10.0, lineTotal = 10.0),
            buildCartItem(productId = "p2", unitPrice = 12.0, lineTotal = 12.0),
        )
        // Individual = 22, bundle price = 15 → discount = 7
        val result = useCase(cart, listOf(makeBundlePromotion(productIds = listOf("p1", "p2"), bundlePrice = 15.0)))
        assertEquals(7.0, result, 0.001)
    }

    @Test
    fun `bundle - missing product - no discount`() {
        val cart = listOf(buildCartItem(productId = "p1", unitPrice = 10.0, lineTotal = 10.0))
        val result = useCase(
            cart,
            listOf(makeBundlePromotion(productIds = listOf("p1", "p2-missing"), bundlePrice = 5.0)),
        )
        assertEquals(0.0, result)
    }

    // ── SCHEDULED ────────────────────────────────────────────────────────────

    @Test
    fun `scheduled promotion computed like flash sale`() {
        val cart = listOf(buildCartItem(productId = "p1", unitPrice = 100.0, lineTotal = 100.0))
        val scheduled = Promotion(
            id = "promo-sched",
            name = "Weekend Sale",
            type = PromotionType.SCHEDULED,
            config = PromotionConfig.Scheduled(discountPct = 15.0),
            validFrom = 0L,
            validTo = Long.MAX_VALUE,
            isActive = true,
        )
        val result = useCase(cart, listOf(scheduled))
        assertEquals(15.0, result, 0.001)
    }

    // ── Unknown config ────────────────────────────────────────────────────────

    @Test
    fun `unknown config returns 0`() {
        val cart = listOf(buildCartItem(unitPrice = 100.0, lineTotal = 100.0))
        val unknown = Promotion(
            id = "promo-unk",
            name = "Unknown",
            type = PromotionType.FLASH_SALE,
            config = PromotionConfig.Unknown,
            validFrom = 0L,
            validTo = Long.MAX_VALUE,
            isActive = true,
        )
        assertEquals(0.0, useCase(cart, listOf(unknown)))
    }

    // ── Stacking rules ────────────────────────────────────────────────────────

    @Test
    fun `same type does not stack - highest priority wins`() {
        val cart = listOf(buildCartItem(unitPrice = 100.0, lineTotal = 100.0))
        val highPriority = makeFlashSalePromotion(id = "high", discountPct = 30.0, priority = 10)
        val lowPriority  = makeFlashSalePromotion(id = "low",  discountPct = 10.0, priority = 1)
        val result = useCase(cart, listOf(lowPriority, highPriority))
        // Only 30% applied (not 30+10=40)
        assertEquals(30.0, result, 0.001)
    }

    @Test
    fun `different types stack - flash sale and bogo both apply`() {
        val cart = listOf(
            buildCartItem(productId = "p1", unitPrice = 10.0, quantity = 3.0, lineTotal = 30.0),
        )
        val flash = makeFlashSalePromotion(discountPct = 10.0, priority = 2)   // 10% of 30 = 3
        val bogo  = makeBogoPromotion(buyQty = 2, getQty = 1, discountPct = 100.0, priority = 1) // 1 free = 10
        val result = useCase(cart, listOf(flash, bogo))
        // 3 + 10 = 13 (both applied)
        assertEquals(13.0, result, 0.001)
    }

    // ── Safety cap ───────────────────────────────────────────────────────────

    @Test
    fun `discount never exceeds cart subtotal`() {
        val cart = listOf(buildCartItem(unitPrice = 10.0, lineTotal = 10.0))
        val promotion = makeFlashSalePromotion(discountPct = 200.0) // 200% would be 20 on a 10 cart
        val result = useCase(cart, listOf(promotion))
        assertTrue(result <= 10.0)
    }
}

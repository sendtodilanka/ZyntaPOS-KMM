package com.zyntasolutions.zyntapos.domain.usecase.coupons

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.model.PromotionType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeCouponRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

private fun buildPromotion(
    id: String = "promo-01",
    name: String = "Summer Sale",
    storeIds: List<String> = emptyList(),
    isActive: Boolean = true,
): Promotion {
    val now = Clock.System.now().toEpochMilliseconds()
    return Promotion(
        id = id,
        name = name,
        type = PromotionType.FLASH_SALE,
        validFrom = now - 86_400_000L,
        validTo = now + 86_400_000L,
        isActive = isActive,
        storeIds = storeIds,
        priority = 0,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// GetStorePromotionsUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetStorePromotionsUseCaseTest {

    @Test
    fun `globalPromotion_includedForAnyStore`() = runTest {
        val repo = FakeCouponRepository()
        repo.promotions.add(buildPromotion(id = "promo-global", storeIds = emptyList()))

        GetStorePromotionsUseCase(repo).invoke("store-01").test {
            val list = awaitItem()
            assertTrue(list.any { it.id == "promo-global" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `storeSpecificPromotion_includedOnlyForThatStore`() = runTest {
        val repo = FakeCouponRepository()
        repo.promotions.add(buildPromotion(id = "promo-A", storeIds = listOf("store-01")))
        repo.promotions.add(buildPromotion(id = "promo-B", storeIds = listOf("store-02")))

        GetStorePromotionsUseCase(repo).invoke("store-01").test {
            val list = awaitItem()
            assertTrue(list.any { it.id == "promo-A" })
            assertTrue(list.none { it.id == "promo-B" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emptyPromotions_emitsEmptyList`() = runTest {
        GetStorePromotionsUseCase(FakeCouponRepository()).invoke("store-01").test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

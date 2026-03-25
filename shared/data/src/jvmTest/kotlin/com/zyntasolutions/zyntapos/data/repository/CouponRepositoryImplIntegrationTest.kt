package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.model.PromotionConfig
import com.zyntasolutions.zyntapos.domain.model.PromotionType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CouponRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates CouponRepositoryImpl against a real in-memory SQLite database.
 * No mocks — all assertions exercise actual SQLDelight-generated queries.
 *
 * Coverage:
 *  A. insert coupon → getByCode retrieves the coupon with correct fields
 *  B. insert coupon → getById round-trip preserves all fields
 *  C. getAll returns all coupons (active and inactive) as a Flow
 *  D. getActiveCoupons filters by is_active = 1 and valid time window
 *  E. update changes discount value and name
 *  F. toggleActive(false) deactivates a coupon; getActiveCoupons excludes it
 *  G. delete removes the coupon; getByCode returns Result.Error
 *  H. getByCode for non-existent code returns Result.Error
 *  I. recordRedemption increments usage_count and inserts coupon_usage row
 *  J. getCustomerUsageCount returns correct count after redemption
 *  K. insertPromotion → getPromotionById round-trip
 *  L. updatePromotion changes type and priority
 *  M. deletePromotion removes the promotion; getPromotionById returns Result.Error
 */
class CouponRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: CouponRepositoryImpl

    // Epoch millis: validFrom must be strictly before validTo
    private val validFrom = 1_000_000L
    private val validTo   = 9_000_000_000L

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        val syncEnqueuer = SyncEnqueuer(db)
        repo = CouponRepositoryImpl(db, syncEnqueuer)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeCoupon(
        id: String           = "coupon-1",
        code: String         = "SAVE10",
        name: String         = "Save 10",
        discountType: DiscountType = DiscountType.FIXED,
        discountValue: Double = 10.0,
        isActive: Boolean    = true,
        from: Long           = validFrom,
        to: Long             = validTo,
    ) = Coupon(
        id            = id,
        code          = code,
        name          = name,
        discountType  = discountType,
        discountValue = discountValue,
        validFrom     = from,
        validTo       = to,
        isActive      = isActive,
    )

    private fun makePromotion(
        id: String   = "promo-1",
        name: String = "Flash Sale",
        type: PromotionType = PromotionType.FLASH_SALE,
        from: Long   = validFrom,
        to: Long     = validTo,
        priority: Int = 1,
        isActive: Boolean = true,
    ) = Promotion(
        id        = id,
        name      = name,
        type      = type,
        config    = PromotionConfig.FlashSale(discountPct = 10.0),
        validFrom = from,
        validTo   = to,
        priority  = priority,
        isActive  = isActive,
    )

    // ── A. insert → getByCode ─────────────────────────────────────────────────

    @Test
    fun insert_then_getByCode_returns_correct_coupon() = runTest {
        val coupon = makeCoupon(code = "SUMMER20", discountValue = 20.0)
        val insertResult = repo.insert(coupon)
        assertIs<Result.Success<Unit>>(insertResult)

        val result = repo.getByCode("SUMMER20")
        assertIs<Result.Success<Coupon>>(result)
        assertEquals("SUMMER20", result.data.code)
        assertEquals(20.0,       result.data.discountValue)
    }

    // ── B. insert → getById round-trip ────────────────────────────────────────

    @Test
    fun insert_then_getById_preserves_all_fields() = runTest {
        val coupon = makeCoupon(
            id            = "coupon-rt",
            code          = "ROUNDTRIP",
            name          = "Round Trip Coupon",
            discountType  = DiscountType.PERCENT,
            discountValue = 15.0,
        )
        repo.insert(coupon)

        val result = repo.getById("coupon-rt")
        assertIs<Result.Success<Coupon>>(result)
        val retrieved = result.data
        assertEquals("coupon-rt",          retrieved.id)
        assertEquals("ROUNDTRIP",          retrieved.code)
        assertEquals("Round Trip Coupon",  retrieved.name)
        assertEquals(DiscountType.PERCENT, retrieved.discountType)
        assertEquals(15.0,                 retrieved.discountValue)
        assertTrue(retrieved.isActive)
    }

    // ── C. getAll returns all coupons as a Flow ───────────────────────────────

    @Test
    fun getAll_returns_all_coupons_including_inactive() = runTest {
        repo.insert(makeCoupon(id = "c-active",   code = "ACTIVE",   isActive = true))
        repo.insert(makeCoupon(id = "c-inactive", code = "INACTIVE", isActive = false))

        repo.getAll().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            cancel()
        }
    }

    // ── D. getActiveCoupons filters by validity window ────────────────────────

    @Test
    fun getActiveCoupons_returns_only_active_coupons_within_time_window() = runTest {
        // now = 5_000_000L falls inside validFrom..validTo
        val nowMillis = 5_000_000L

        repo.insert(makeCoupon(id = "c-active",   code = "ACTIVE",   isActive = true,
            from = 1_000_000L, to = 9_000_000_000L))
        repo.insert(makeCoupon(id = "c-inactive", code = "INACTIVE", isActive = false,
            from = 1_000_000L, to = 9_000_000_000L))
        // expired coupon: valid_to is before nowMillis
        repo.insert(makeCoupon(id = "c-expired",  code = "EXPIRED",  isActive = true,
            from = 1_000L, to = 2_000L))

        repo.getActiveCoupons(nowMillis).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("c-active", items[0].id)
            cancel()
        }
    }

    // ── E. update changes discount value ─────────────────────────────────────

    @Test
    fun update_changes_discount_value_and_name() = runTest {
        val original = makeCoupon(id = "c-upd", code = "UPDATE", discountValue = 5.0, name = "Old Name")
        repo.insert(original)

        val updated = original.copy(discountValue = 25.0, name = "New Name")
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val retrieved = repo.getById("c-upd")
        assertIs<Result.Success<Coupon>>(retrieved)
        assertEquals(25.0,      retrieved.data.discountValue)
        assertEquals("New Name", retrieved.data.name)
    }

    // ── F. toggleActive(false) deactivates a coupon ──────────────────────────

    @Test
    fun toggleActive_false_deactivates_coupon_and_excludes_from_active_list() = runTest {
        val coupon = makeCoupon(id = "c-tog", code = "TOGGLE", isActive = true)
        repo.insert(coupon)

        val toggleResult = repo.toggleActive("c-tog", false)
        assertIs<Result.Success<Unit>>(toggleResult)

        // Verify the coupon is now inactive via getById
        val retrieved = repo.getById("c-tog")
        assertIs<Result.Success<Coupon>>(retrieved)
        assertEquals(false, retrieved.data.isActive)

        // Verify it no longer appears in active coupons
        repo.getActiveCoupons(5_000_000L).test {
            val items = awaitItem()
            assertTrue(items.none { it.id == "c-tog" })
            cancel()
        }
    }

    // ── G. delete removes the coupon ─────────────────────────────────────────

    @Test
    fun delete_removes_coupon_and_subsequent_getByCode_returns_error() = runTest {
        repo.insert(makeCoupon(id = "c-del", code = "DELETE_ME"))

        val deleteResult = repo.delete("c-del")
        assertIs<Result.Success<Unit>>(deleteResult)

        val result = repo.getByCode("DELETE_ME")
        assertIs<Result.Error>(result)
    }

    // ── H. getByCode for non-existent code returns Result.Error ─────────────

    @Test
    fun getByCode_for_nonexistent_code_returns_error() = runTest {
        val result = repo.getByCode("DOES_NOT_EXIST")
        assertIs<Result.Error>(result)
    }

    // ── I. recordRedemption increments usage_count ────────────────────────────

    @Test
    fun recordRedemption_increments_usage_count() = runTest {
        val coupon = makeCoupon(id = "c-redeem", code = "REDEEM")
        repo.insert(coupon)

        val usage = CouponUsage(
            id             = "usage-1",
            couponId       = "c-redeem",
            orderId        = "order-1",
            customerId     = "cust-1",
            discountAmount = 10.0,
            usedAt         = 5_000_000L,
        )
        val redeemResult = repo.recordRedemption(usage)
        assertIs<Result.Success<Unit>>(redeemResult)

        // usage_count should be 1 after one redemption
        val retrieved = repo.getById("c-redeem")
        assertIs<Result.Success<Coupon>>(retrieved)
        assertEquals(1, retrieved.data.usageCount)
    }

    // ── J. getCustomerUsageCount returns correct count ────────────────────────

    @Test
    fun getCustomerUsageCount_returns_correct_count_after_redemption() = runTest {
        val coupon = makeCoupon(id = "c-count", code = "COUNT")
        repo.insert(coupon)

        repo.recordRedemption(CouponUsage(
            id = "u-1", couponId = "c-count", orderId = "ord-1",
            customerId = "cust-A", discountAmount = 5.0, usedAt = 5_000_000L,
        ))
        repo.recordRedemption(CouponUsage(
            id = "u-2", couponId = "c-count", orderId = "ord-2",
            customerId = "cust-A", discountAmount = 5.0, usedAt = 5_000_001L,
        ))
        repo.recordRedemption(CouponUsage(
            id = "u-3", couponId = "c-count", orderId = "ord-3",
            customerId = "cust-B", discountAmount = 5.0, usedAt = 5_000_002L,
        ))

        val countA = repo.getCustomerUsageCount("c-count", "cust-A")
        assertIs<Result.Success<Int>>(countA)
        assertEquals(2, countA.data)

        val countB = repo.getCustomerUsageCount("c-count", "cust-B")
        assertIs<Result.Success<Int>>(countB)
        assertEquals(1, countB.data)
    }

    // ── K. insertPromotion → getPromotionById round-trip ─────────────────────

    @Test
    fun insertPromotion_then_getPromotionById_preserves_fields() = runTest {
        val promotion = makePromotion(
            id       = "promo-rt",
            name     = "Bundle Deal",
            type     = PromotionType.BUNDLE,
            priority = 5,
        )
        val insertResult = repo.insertPromotion(promotion)
        assertIs<Result.Success<Unit>>(insertResult)

        val result = repo.getPromotionById("promo-rt")
        assertIs<Result.Success<Promotion>>(result)
        assertEquals("promo-rt",        result.data.id)
        assertEquals("Bundle Deal",     result.data.name)
        assertEquals(PromotionType.BUNDLE, result.data.type)
        assertEquals(5,                 result.data.priority)
        assertTrue(result.data.isActive)
    }

    // ── L. updatePromotion changes type and priority ──────────────────────────

    @Test
    fun updatePromotion_changes_type_and_priority() = runTest {
        val promotion = makePromotion(id = "promo-upd", type = PromotionType.FLASH_SALE, priority = 1)
        repo.insertPromotion(promotion)

        val updated = promotion.copy(type = PromotionType.BUY_X_GET_Y, priority = 10)
        val updateResult = repo.updatePromotion(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val result = repo.getPromotionById("promo-upd")
        assertIs<Result.Success<Promotion>>(result)
        assertEquals(PromotionType.BUY_X_GET_Y, result.data.type)
        assertEquals(10,                        result.data.priority)
    }

    // ── M. deletePromotion removes the promotion ──────────────────────────────

    @Test
    fun deletePromotion_removes_promotion_and_getPromotionById_returns_error() = runTest {
        val promotion = makePromotion(id = "promo-del")
        repo.insertPromotion(promotion)

        val deleteResult = repo.deletePromotion("promo-del")
        assertIs<Result.Success<Unit>>(deleteResult)

        val result = repo.getPromotionById("promo-del")
        assertIs<Result.Error>(result)
    }

    // ── N. getAllPromotions returns inserted promotions as a Flow ─────────────

    @Test
    fun getAllPromotions_returns_all_inserted_promotions() = runTest {
        repo.insertPromotion(makePromotion(id = "p-1", name = "Promo One"))
        repo.insertPromotion(makePromotion(id = "p-2", name = "Promo Two"))

        repo.getAllPromotions().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            cancel()
        }
    }
}

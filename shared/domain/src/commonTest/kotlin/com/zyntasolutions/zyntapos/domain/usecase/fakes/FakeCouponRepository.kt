package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// Coupon Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildCoupon(
    id: String = "coupon-01",
    code: String = "SUMMER20",
    name: String = "Summer Sale",
    discountType: DiscountType = DiscountType.PERCENT,
    discountValue: Double = 20.0,
    minimumPurchase: Double = 0.0,
    maximumDiscount: Double? = null,
    usageLimit: Int? = null,
    usageCount: Int = 0,
    perCustomerLimit: Int? = null,
    isActive: Boolean = true,
    validFrom: Long = Clock.System.now().toEpochMilliseconds() - 86_400_000L, // -1 day
    validTo: Long = Clock.System.now().toEpochMilliseconds() + 86_400_000L,   // +1 day
) = Coupon(
    id = id, code = code, name = name,
    discountType = discountType, discountValue = discountValue,
    minimumPurchase = minimumPurchase, maximumDiscount = maximumDiscount,
    usageLimit = usageLimit, usageCount = usageCount,
    perCustomerLimit = perCustomerLimit,
    validFrom = validFrom, validTo = validTo, isActive = isActive,
)

// ─────────────────────────────────────────────────────────────────────────────
// Fake CouponRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [CouponRepository].
 */
class FakeCouponRepository : CouponRepository {
    val coupons = mutableListOf<Coupon>()
    val usages = mutableListOf<CouponUsage>()
    val promotions = mutableListOf<Promotion>()
    var shouldFail = false

    private val _couponsFlow = MutableStateFlow<List<Coupon>>(emptyList())
    private val _promotionsFlow = MutableStateFlow<List<Promotion>>(emptyList())

    override fun getAll(): Flow<List<Coupon>> = _couponsFlow

    override fun getActiveCoupons(nowEpochMillis: Long): Flow<List<Coupon>> =
        MutableStateFlow(coupons.filter {
            it.isActive && it.validFrom <= nowEpochMillis && it.validTo >= nowEpochMillis
        })

    override suspend fun getByCode(code: String): Result<Coupon> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return coupons.find { it.code == code }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Coupon not found: $code"))
    }

    override suspend fun getById(id: String): Result<Coupon> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return coupons.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Coupon not found: $id"))
    }

    override suspend fun insert(coupon: Coupon): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        coupons.add(coupon)
        _couponsFlow.value = coupons.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(coupon: Coupon): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        coupons.removeAll { it.id == coupon.id }
        coupons.add(coupon)
        _couponsFlow.value = coupons.toList()
        return Result.Success(Unit)
    }

    override suspend fun toggleActive(id: String, isActive: Boolean): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = coupons.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Coupon not found: $id"))
        coupons[idx] = coupons[idx].copy(isActive = isActive)
        _couponsFlow.value = coupons.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        coupons.removeAll { it.id == id }
        _couponsFlow.value = coupons.toList()
        return Result.Success(Unit)
    }

    override suspend fun recordRedemption(usage: CouponUsage): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        usages.add(usage)
        val idx = coupons.indexOfFirst { it.id == usage.couponId }
        if (idx >= 0) coupons[idx] = coupons[idx].copy(usageCount = coupons[idx].usageCount + 1)
        return Result.Success(Unit)
    }

    override suspend fun getCustomerUsageCount(couponId: String, customerId: String): Result<Int> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val count = usages.count { it.couponId == couponId && it.customerId == customerId }
        return Result.Success(count)
    }

    override fun getUsageByCoupon(couponId: String): Flow<List<CouponUsage>> =
        MutableStateFlow(usages.filter { it.couponId == couponId })

    override fun getAllPromotions(): Flow<List<Promotion>> = _promotionsFlow

    override fun getActivePromotions(nowEpochMillis: Long): Flow<List<Promotion>> =
        MutableStateFlow(promotions.filter {
            it.isActive && it.validFrom <= nowEpochMillis && it.validTo >= nowEpochMillis
        })

    override suspend fun getPromotionById(id: String): Result<Promotion> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return promotions.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Promotion not found: $id"))
    }

    override suspend fun insertPromotion(promotion: Promotion): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        promotions.add(promotion)
        _promotionsFlow.value = promotions.toList()
        return Result.Success(Unit)
    }

    override suspend fun updatePromotion(promotion: Promotion): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        promotions.removeAll { it.id == promotion.id }
        promotions.add(promotion)
        _promotionsFlow.value = promotions.toList()
        return Result.Success(Unit)
    }

    override suspend fun deletePromotion(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        promotions.removeAll { it.id == id }
        _promotionsFlow.value = promotions.toList()
        return Result.Success(Unit)
    }
}

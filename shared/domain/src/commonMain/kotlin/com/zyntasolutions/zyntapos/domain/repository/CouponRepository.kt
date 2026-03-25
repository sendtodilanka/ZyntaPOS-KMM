package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.Promotion
import kotlinx.coroutines.flow.Flow

/**
 * Contract for coupon and promotion CRUD and redemption.
 */
interface CouponRepository {

    // ── Coupons ──────────────────────────────────────────────────────────────

    /** Emits all coupons, most recently created first. Re-emits on change. */
    fun getAll(): Flow<List<Coupon>>

    /**
     * Emits coupons that are currently valid (is_active = 1 AND valid_from <= now AND valid_to >= now).
     * Re-emits when the set changes.
     */
    fun getActiveCoupons(nowEpochMillis: Long): Flow<List<Coupon>>

    /**
     * C2.4: Emits coupons applicable to a specific store (global + store-specific).
     * Global coupons have store_id = NULL; store-specific ones match the given storeId.
     */
    fun getActiveCouponsForStore(nowEpochMillis: Long, storeId: String): Flow<List<Coupon>>

    /** Looks up a coupon by its [code]. Returns [Result.Error] if not found. */
    suspend fun getByCode(code: String): Result<Coupon>

    /** Returns a coupon by [id]. */
    suspend fun getById(id: String): Result<Coupon>

    /** Inserts a new coupon and enqueues a sync operation. */
    suspend fun insert(coupon: Coupon): Result<Unit>

    /** Updates all mutable fields of [coupon] and enqueues a sync operation. */
    suspend fun update(coupon: Coupon): Result<Unit>

    /** Soft-disables a coupon. */
    suspend fun toggleActive(id: String, isActive: Boolean): Result<Unit>

    /** Hard-deletes a coupon. */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Records coupon redemption atomically:
     * 1. Increments [Coupon.usageCount]
     * 2. Inserts a [CouponUsage] ledger entry
     */
    suspend fun recordRedemption(usage: CouponUsage): Result<Unit>

    /**
     * Returns how many times the given customer has used the coupon.
     * Returns 0 if [customerId] is null (anonymous usage check not needed).
     */
    suspend fun getCustomerUsageCount(couponId: String, customerId: String): Result<Int>

    /** Returns usage ledger for a coupon. */
    fun getUsageByCoupon(couponId: String): Flow<List<CouponUsage>>

    // ── Promotions ────────────────────────────────────────────────────────────

    /** Emits all promotions ordered by priority descending. Re-emits on change. */
    fun getAllPromotions(): Flow<List<Promotion>>

    /** Emits promotions currently valid and active. */
    fun getActivePromotions(nowEpochMillis: Long): Flow<List<Promotion>>

    /**
     * C2.4: Emits promotions applicable to a specific store (global + store-targeted).
     * Global promotions have empty storeIds; store-targeted ones contain the given storeId.
     */
    fun getActivePromotionsForStore(nowEpochMillis: Long, storeId: String): Flow<List<Promotion>>

    /** Returns a promotion by [id]. */
    suspend fun getPromotionById(id: String): Result<Promotion>

    /** Inserts a new promotion and enqueues a sync operation. */
    suspend fun insertPromotion(promotion: Promotion): Result<Unit>

    /** Updates a promotion and enqueues a sync operation. */
    suspend fun updatePromotion(promotion: Promotion): Result<Unit>

    /** Hard-deletes a promotion. */
    suspend fun deletePromotion(id: String): Result<Unit>

    /**
     * Upserts a promotion received from the server sync delta.
     * Payload is the raw JSONB string from the sync operation.
     * Marks the local row as SYNCED after apply.
     */
    suspend fun upsertPromotionFromSync(payload: String)
}

package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Coupon_usage
import com.zyntasolutions.zyntapos.db.Coupons
import com.zyntasolutions.zyntapos.db.Promotions
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.model.PromotionType
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class CouponRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : CouponRepository {

    private val cq get() = db.couponsQueries
    private val pq get() = db.promotionsQueries

    override fun getAll(): Flow<List<Coupon>> =
        cq.getAllCoupons().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toCouponDomain) }

    override fun getActiveCoupons(nowEpochMillis: Long): Flow<List<Coupon>> =
        cq.getActiveCoupons(nowEpochMillis, nowEpochMillis)
            .asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toCouponDomain) }

    override suspend fun getByCode(code: String): Result<Coupon> = withContext(Dispatchers.IO) {
        runCatching {
            cq.getCouponByCode(code).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Coupon not found: $code"))
        }.fold(
            onSuccess = { Result.Success(toCouponDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getById(id: String): Result<Coupon> = withContext(Dispatchers.IO) {
        runCatching {
            cq.getCouponById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Coupon not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toCouponDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(coupon: Coupon): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            val scopeIdsJson = coupon.scopeIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            cq.insertCoupon(
                id = coupon.id, code = coupon.code, name = coupon.name,
                discount_type = coupon.discountType.name, discount_value = coupon.discountValue,
                minimum_purchase = coupon.minimumPurchase, maximum_discount = coupon.maximumDiscount,
                usage_limit = coupon.usageLimit?.toLong(), usage_count = coupon.usageCount.toLong(),
                per_customer_limit = coupon.perCustomerLimit?.toLong(),
                scope = coupon.scope.name, scope_ids = scopeIdsJson,
                valid_from = coupon.validFrom, valid_to = coupon.validTo,
                is_active = if (coupon.isActive) 1L else 0L,
                created_at = now, updated_at = now, sync_status = "PENDING",
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.COUPON, coupon.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(coupon: Coupon): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            val scopeIdsJson = coupon.scopeIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            cq.updateCoupon(
                code = coupon.code, name = coupon.name,
                discount_type = coupon.discountType.name, discount_value = coupon.discountValue,
                minimum_purchase = coupon.minimumPurchase, maximum_discount = coupon.maximumDiscount,
                usage_limit = coupon.usageLimit?.toLong(),
                per_customer_limit = coupon.perCustomerLimit?.toLong(),
                scope = coupon.scope.name, scope_ids = scopeIdsJson,
                valid_from = coupon.validFrom, valid_to = coupon.validTo,
                is_active = if (coupon.isActive) 1L else 0L,
                updated_at = now, sync_status = "PENDING", id = coupon.id,
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.COUPON, coupon.id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun toggleActive(id: String, isActive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            cq.toggleCouponActive(is_active = if (isActive) 1L else 0L, updated_at = now, id = id)
            syncEnqueuer.enqueue(SyncOperation.EntityType.COUPON, id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Toggle failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { cq.deleteCoupon(id) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    override suspend fun recordRedemption(usage: CouponUsage): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                cq.incrementCouponUsage(updated_at = now, id = usage.couponId)
                cq.insertCouponUsage(
                    id = usage.id, coupon_id = usage.couponId, order_id = usage.orderId,
                    customer_id = usage.customerId, discount_amount = usage.discountAmount,
                    used_at = usage.usedAt, sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.COUPON_USAGE, usage.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Record redemption failed", cause = t)) },
        )
    }

    override suspend fun getCustomerUsageCount(couponId: String, customerId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            cq.countCustomerUsage(couponId, customerId).executeAsOne().toInt()
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getUsageByCoupon(couponId: String): Flow<List<CouponUsage>> =
        cq.getUsageByCoupon(couponId)
            .asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toUsageDomain) }

    override fun getAllPromotions(): Flow<List<Promotion>> =
        pq.getAllPromotions().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toPromotionDomain) }

    override fun getActivePromotions(nowEpochMillis: Long): Flow<List<Promotion>> =
        pq.getActivePromotions(nowEpochMillis, nowEpochMillis)
            .asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toPromotionDomain) }

    override suspend fun getPromotionById(id: String): Result<Promotion> = withContext(Dispatchers.IO) {
        runCatching {
            pq.getPromotionById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Promotion not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toPromotionDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insertPromotion(promotion: Promotion): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            pq.insertPromotion(
                id = promotion.id, name = promotion.name, type = promotion.type.name,
                config = promotion.config, valid_from = promotion.validFrom, valid_to = promotion.validTo,
                priority = promotion.priority.toLong(),
                is_active = if (promotion.isActive) 1L else 0L,
                created_at = now, updated_at = now, sync_status = "PENDING",
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.PROMOTION, promotion.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun updatePromotion(promotion: Promotion): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            pq.updatePromotion(
                name = promotion.name, type = promotion.type.name, config = promotion.config,
                valid_from = promotion.validFrom, valid_to = promotion.validTo,
                priority = promotion.priority.toLong(),
                is_active = if (promotion.isActive) 1L else 0L,
                updated_at = now, sync_status = "PENDING", id = promotion.id,
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.PROMOTION, promotion.id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun deletePromotion(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { pq.deletePromotion(id) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    private fun toCouponDomain(row: Coupons): Coupon {
        val scopeIds = runCatching {
            Json.parseToJsonElement(row.scope_ids).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
        return Coupon(
            id = row.id, code = row.code, name = row.name,
            discountType = runCatching { DiscountType.valueOf(row.discount_type) }.getOrDefault(DiscountType.FIXED),
            discountValue = row.discount_value, minimumPurchase = row.minimum_purchase,
            maximumDiscount = row.maximum_discount,
            usageLimit = row.usage_limit?.toInt(), usageCount = row.usage_count.toInt(),
            perCustomerLimit = row.per_customer_limit?.toInt(),
            scope = runCatching { Coupon.CouponScope.valueOf(row.scope) }.getOrDefault(Coupon.CouponScope.CART),
            scopeIds = scopeIds,
            validFrom = row.valid_from, validTo = row.valid_to,
            isActive = row.is_active == 1L,
        )
    }

    private fun toUsageDomain(row: Coupon_usage) = CouponUsage(
        id = row.id, couponId = row.coupon_id, orderId = row.order_id,
        customerId = row.customer_id, discountAmount = row.discount_amount, usedAt = row.used_at,
    )

    private fun toPromotionDomain(row: Promotions) = Promotion(
        id = row.id, name = row.name,
        type = runCatching { PromotionType.valueOf(row.type) }.getOrDefault(PromotionType.FLASH_SALE),
        config = row.config, validFrom = row.valid_from, validTo = row.valid_to,
        priority = row.priority.toInt(), isActive = row.is_active == 1L,
    )
}

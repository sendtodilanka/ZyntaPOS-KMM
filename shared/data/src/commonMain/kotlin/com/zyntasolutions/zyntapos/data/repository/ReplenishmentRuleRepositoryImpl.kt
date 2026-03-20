package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.ReplenishmentRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed implementation of [ReplenishmentRuleRepository] (C1.5).
 *
 * All writes are wrapped in transactions and enqueue a [SyncOperation]
 * so changes are propagated to the backend.
 */
class ReplenishmentRuleRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : ReplenishmentRuleRepository {

    private val q get() = db.replenishment_rulesQueries

    override fun getAll(): Flow<List<ReplenishmentRule>> =
        q.getAllRules()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun getByWarehouse(warehouseId: String): Flow<List<ReplenishmentRule>> =
        q.getRulesByWarehouse(warehouseId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAutoApproveRules(): Result<List<ReplenishmentRule>> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getAutoApproveRules().executeAsList().map { it.toDomain() }
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun getByProductAndWarehouse(
        productId: String,
        warehouseId: String,
    ): Result<ReplenishmentRule?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getRuleByProductAndWarehouse(productId, warehouseId).executeAsOneOrNull()?.toDomain()
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun upsert(rule: ReplenishmentRule): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                val existing = q.getRuleByProductAndWarehouse(rule.productId, rule.warehouseId)
                    .executeAsOneOrNull()

                db.transaction {
                    if (existing == null) {
                        q.insertRule(
                            id            = rule.id,
                            product_id    = rule.productId,
                            warehouse_id  = rule.warehouseId,
                            supplier_id   = rule.supplierId,
                            reorder_point = rule.reorderPoint,
                            reorder_qty   = rule.reorderQty,
                            auto_approve  = if (rule.autoApprove) 1L else 0L,
                            is_active     = if (rule.isActive) 1L else 0L,
                            created_at    = rule.createdAt,
                            updated_at    = now,
                        )
                    } else {
                        q.updateRule(
                            supplier_id   = rule.supplierId,
                            reorder_point = rule.reorderPoint,
                            reorder_qty   = rule.reorderQty,
                            auto_approve  = if (rule.autoApprove) 1L else 0L,
                            is_active     = if (rule.isActive) 1L else 0L,
                            updated_at    = now,
                            id            = existing.id,
                        )
                    }
                }
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.REPLENISHMENT_RULE,
                    rule.id,
                    if (existing == null) SyncOperation.Operation.INSERT else SyncOperation.Operation.UPDATE,
                )
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Upsert rule failed", cause = t)) },
            )
        }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction { q.deleteRule(id) }
            syncEnqueuer.enqueue(SyncOperation.EntityType.REPLENISHMENT_RULE, id, SyncOperation.Operation.DELETE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete rule failed", cause = t)) },
        )
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    private fun com.zyntasolutions.zyntapos.db.GetAllRules.toDomain() = ReplenishmentRule(
        id            = id,
        productId     = product_id,
        warehouseId   = warehouse_id,
        supplierId    = supplier_id,
        reorderPoint  = reorder_point,
        reorderQty    = reorder_qty,
        autoApprove   = auto_approve == 1L,
        isActive      = is_active == 1L,
        createdAt     = created_at,
        updatedAt     = updated_at,
        productName   = product_name,
        warehouseName = warehouse_name,
        supplierName  = supplier_name,
    )

    private fun com.zyntasolutions.zyntapos.db.GetRulesByWarehouse.toDomain() = ReplenishmentRule(
        id            = id,
        productId     = product_id,
        warehouseId   = warehouse_id,
        supplierId    = supplier_id,
        reorderPoint  = reorder_point,
        reorderQty    = reorder_qty,
        autoApprove   = auto_approve == 1L,
        isActive      = is_active == 1L,
        createdAt     = created_at,
        updatedAt     = updated_at,
        productName   = product_name,
        warehouseName = warehouse_name,
        supplierName  = supplier_name,
    )

    private fun com.zyntasolutions.zyntapos.db.GetAutoApproveRules.toDomain() = ReplenishmentRule(
        id            = id,
        productId     = product_id,
        warehouseId   = warehouse_id,
        supplierId    = supplier_id,
        reorderPoint  = reorder_point,
        reorderQty    = reorder_qty,
        autoApprove   = auto_approve == 1L,
        isActive      = is_active == 1L,
        createdAt     = created_at,
        updatedAt     = updated_at,
        productName   = product_name,
        warehouseName = warehouse_name,
        supplierName  = supplier_name,
    )

    private fun com.zyntasolutions.zyntapos.db.GetRuleByProductAndWarehouse.toDomain() = ReplenishmentRule(
        id            = id,
        productId     = product_id,
        warehouseId   = warehouse_id,
        supplierId    = supplier_id,
        reorderPoint  = reorder_point,
        reorderQty    = reorder_qty,
        autoApprove   = auto_approve == 1L,
        isActive      = is_active == 1L,
        createdAt     = created_at,
        updatedAt     = updated_at,
        productName   = product_name,
        warehouseName = warehouse_name,
        supplierName  = supplier_name,
    )
}

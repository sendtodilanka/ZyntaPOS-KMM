package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Warehouse_racks
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class WarehouseRackRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : WarehouseRackRepository {

    private val q get() = db.warehouse_racksQueries

    override fun getByWarehouse(warehouseId: String): Flow<List<WarehouseRack>> =
        q.selectByWarehouse(warehouseId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<WarehouseRack> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Warehouse rack not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(rack: WarehouseRack): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertRack(
                    id = rack.id,
                    warehouse_id = rack.warehouseId,
                    name = rack.name,
                    description = rack.description,
                    capacity = rack.capacity?.toLong(),
                    created_at = now,
                    updated_at = now,
                    deleted_at = null,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.WAREHOUSE_RACK,
                    rack.id,
                    SyncOperation.Operation.INSERT,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(rack: WarehouseRack): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateRack(
                    name = rack.name,
                    description = rack.description,
                    capacity = rack.capacity?.toLong(),
                    updated_at = now,
                    id = rack.id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.WAREHOUSE_RACK,
                    rack.id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String, deletedAt: Long, updatedAt: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                db.transaction {
                    q.softDelete(deleted_at = deletedAt, updated_at = updatedAt, id = id)
                    syncEnqueuer.enqueue(
                        SyncOperation.EntityType.WAREHOUSE_RACK,
                        id,
                        SyncOperation.Operation.DELETE,
                    )
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
            )
        }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Warehouse_racks) = WarehouseRack(
        id = row.id,
        warehouseId = row.warehouse_id,
        name = row.name,
        description = row.description,
        capacity = row.capacity?.toInt(),
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}

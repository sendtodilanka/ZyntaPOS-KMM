package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Store_products
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.StoreProductOverride
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.StoreProductOverrideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Concrete implementation of [StoreProductOverrideRepository].
 *
 * Local price/stock overrides are writable on POS devices. Write operations
 * enqueue via [SyncEnqueuer] to push changes back to the backend.
 */
class StoreProductOverrideRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : StoreProductOverrideRepository {

    private val q get() = db.store_productsQueries

    override fun getByStore(storeId: String): Flow<List<StoreProductOverride>> =
        q.getStoreProductsByStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getOverride(masterProductId: String, storeId: String): Result<StoreProductOverride> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getStoreProduct(masterProductId, storeId).executeAsOneOrNull()
                    ?: return@withContext Result.Error(
                        DatabaseException("Override not found: master=$masterProductId store=$storeId", operation = "getStoreProduct")
                    )
            }.fold(
                onSuccess = { row -> Result.Success(toDomain(row)) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun upsertFromSync(override: StoreProductOverride): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.upsertStoreProduct(
                id                = override.id,
                master_product_id = override.masterProductId,
                store_id          = override.storeId,
                local_price       = override.localPrice,
                local_cost_price  = override.localCostPrice,
                local_stock_qty   = override.localStockQty,
                min_stock_qty     = override.minStockQty,
                is_active         = if (override.isActive) 1L else 0L,
                created_at        = override.createdAt.toEpochMilliseconds(),
                updated_at        = override.updatedAt.toEpochMilliseconds(),
                sync_status       = "SYNCED",
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Upsert failed", cause = t)) },
        )
    }

    override suspend fun updateLocalPrice(masterProductId: String, storeId: String, price: Double?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    q.updateLocalPrice(
                        local_price = price,
                        updated_at = now,
                        master_product_id = masterProductId,
                        store_id = storeId,
                    )
                    val row = q.getStoreProduct(masterProductId, storeId).executeAsOneOrNull()
                    if (row != null) {
                        syncEnqueuer.enqueue("STORE_PRODUCT", row.id, SyncOperation.Operation.UPDATE)
                    }
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update price failed", cause = t)) },
            )
        }

    override suspend fun updateLocalStock(masterProductId: String, storeId: String, qty: Double): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                q.updateLocalStock(
                    local_stock_qty = qty,
                    updated_at = now,
                    master_product_id = masterProductId,
                    store_id = storeId,
                )
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update stock failed", cause = t)) },
            )
        }

    companion object {
        private val syncJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    /** Applies a server-authoritative store product override from a JSON sync payload. */
    suspend fun upsertFromSyncPayload(payload: String) = withContext(Dispatchers.IO) {
        val dto = syncJson.decodeFromString<StoreProductSyncDto>(payload)
        val isActive = if (dto.isActive) 1L else 0L
        q.upsertStoreProduct(
            id                = dto.id,
            master_product_id = dto.masterProductId,
            store_id          = dto.storeId,
            local_price       = dto.localPrice,
            local_cost_price  = dto.localCostPrice,
            local_stock_qty   = dto.localStockQty,
            min_stock_qty     = dto.minStockQty,
            is_active         = isActive,
            created_at        = dto.createdAt,
            updated_at        = dto.updatedAt,
            sync_status       = "SYNCED",
        )
    }

    private fun toDomain(row: Store_products): StoreProductOverride = StoreProductOverride(
        id              = row.id,
        masterProductId = row.master_product_id,
        storeId         = row.store_id,
        localPrice      = row.local_price,
        localCostPrice  = row.local_cost_price,
        localStockQty   = row.local_stock_qty,
        minStockQty     = row.min_stock_qty,
        isActive        = row.is_active == 1L,
        createdAt       = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt       = Instant.fromEpochMilliseconds(row.updated_at),
    )
}

/** Internal DTO for deserializing store product sync payloads. */
@Serializable
internal data class StoreProductSyncDto(
    val id: String,
    @SerialName("master_product_id") val masterProductId: String,
    @SerialName("store_id") val storeId: String,
    @SerialName("local_price") val localPrice: Double? = null,
    @SerialName("local_cost_price") val localCostPrice: Double? = null,
    @SerialName("local_stock_qty") val localStockQty: Double = 0.0,
    @SerialName("min_stock_qty") val minStockQty: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.RackProduct
import com.zyntasolutions.zyntapos.domain.repository.RackProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed implementation of [RackProductRepository].
 *
 * Maps products to rack bin locations using the `rack_products` table.
 * Queries join with the `products` table to populate display fields.
 */
class RackProductRepositoryImpl(
    private val db: ZyntaDatabase,
) : RackProductRepository {

    private val q get() = db.rack_productsQueries

    override fun getByRack(rackId: String): Flow<List<RackProduct>> =
        q.getProductsByRack(rackId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { r ->
                    RackProduct(
                        id = r.id,
                        rackId = r.rack_id,
                        productId = r.product_id,
                        quantity = r.quantity,
                        binLocation = r.bin_location,
                        updatedAt = r.updated_at,
                        productName = r.product_name,
                        productSku = r.sku,
                        productBarcode = r.barcode,
                    )
                }
            }

    override suspend fun upsert(rackProduct: RackProduct): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                q.upsertRackProduct(
                    id = rackProduct.id,
                    rack_id = rackProduct.rackId,
                    product_id = rackProduct.productId,
                    quantity = rackProduct.quantity,
                    bin_location = rackProduct.binLocation,
                    created_at = now,
                    updated_at = now,
                )
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Upsert failed", cause = t)) },
            )
        }

    override suspend fun delete(rackId: String, productId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.deleteRackProduct(rack_id = rackId, product_id = productId)
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
            )
        }
}

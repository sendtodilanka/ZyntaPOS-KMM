package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.ProductVariantMapper
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.ProductVariantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Concrete implementation of [ProductVariantRepository] delegating to SQLDelight.
 */
class ProductVariantRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : ProductVariantRepository {

    private val q get() = db.product_variantsQueries

    override fun getByProductId(productId: String): Flow<List<ProductVariant>> =
        q.getVariantsByProductId(productId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(ProductVariantMapper::toDomain) }

    override suspend fun getById(id: String): Result<ProductVariant> = withContext(Dispatchers.IO) {
        runCatching {
            q.getVariantById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Variant not found: $id", operation = "getVariantById")
                )
        }.fold(
            onSuccess = { Result.Success(ProductVariantMapper.toDomain(it)) },
            onFailure = { Result.Error(DatabaseException(it.message ?: "DB error", cause = it)) },
        )
    }

    override suspend fun getByBarcode(barcode: String): Result<ProductVariant> = withContext(Dispatchers.IO) {
        runCatching {
            q.getVariantByBarcode(barcode).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("No variant for barcode: $barcode", operation = "getVariantByBarcode")
                )
        }.fold(
            onSuccess = { Result.Success(ProductVariantMapper.toDomain(it)) },
            onFailure = { Result.Error(DatabaseException(it.message ?: "DB error", cause = it)) },
        )
    }

    override suspend fun insert(variant: ProductVariant): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertVariant(
                    id = variant.id,
                    product_id = variant.productId,
                    name = variant.name,
                    attributes = ProductVariantMapper.attributesToJson(variant.attributes),
                    price = variant.price,
                    stock = variant.stock,
                    barcode = variant.barcode,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.PRODUCT, variant.productId, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { Result.Error(DatabaseException(it.message ?: "Insert variant failed", cause = it)) },
        )
    }

    override suspend fun update(variant: ProductVariant): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateVariant(
                    name = variant.name,
                    attributes = ProductVariantMapper.attributesToJson(variant.attributes),
                    price = variant.price,
                    stock = variant.stock,
                    barcode = variant.barcode,
                    updated_at = now,
                    sync_status = "PENDING",
                    id = variant.id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.PRODUCT, variant.productId, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { Result.Error(DatabaseException(it.message ?: "Update variant failed", cause = it)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { q.deleteVariant(id) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { Result.Error(DatabaseException(it.message ?: "Delete variant failed", cause = it)) },
        )
    }

    override suspend fun deleteByProductId(productId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { q.deleteVariantsByProductId(productId) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { Result.Error(DatabaseException(it.message ?: "Delete variants failed", cause = it)) },
        )
    }

    override suspend fun replaceAll(
        productId: String,
        variants: List<ProductVariant>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.deleteVariantsByProductId(productId)
                variants.forEach { v ->
                    q.insertVariant(
                        id = v.id,
                        product_id = productId,
                        name = v.name,
                        attributes = ProductVariantMapper.attributesToJson(v.attributes),
                        price = v.price,
                        stock = v.stock,
                        barcode = v.barcode,
                        created_at = now,
                        updated_at = now,
                        sync_status = "PENDING",
                    )
                }
                if (variants.isNotEmpty()) {
                    syncEnqueuer.enqueue(SyncOperation.EntityType.PRODUCT, productId, SyncOperation.Operation.UPDATE)
                }
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { Result.Error(DatabaseException(it.message ?: "Replace variants failed", cause = it)) },
        )
    }
}

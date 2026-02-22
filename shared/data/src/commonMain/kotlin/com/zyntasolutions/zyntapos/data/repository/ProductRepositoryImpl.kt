package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.ProductMapper
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Concrete implementation of [ProductRepository] delegating to SQLDelight queries.
 *
 * - Reactive queries via [asFlow] + [mapToList] re-emit on every table write.
 * - FTS5 full-text search via `searchProducts` — each token gets a prefix wildcard (e.g. "coff*").
 * - Soft-delete sets `is_active = 0`; products are never hard-deleted.
 * - Every mutating operation enqueues a [SyncOperation] via [SyncEnqueuer].
 *
 * @param db            Encrypted [ZyntaDatabase] singleton.
 * @param syncEnqueuer  Helper for writing to `pending_operations`.
 */
class ProductRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : ProductRepository {

    private val q get() = db.productsQueries

    // ── Read ─────────────────────────────────────────────────────────────────

    override fun getAll(): Flow<List<Product>> =
        q.getAllProducts()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(ProductMapper::toDomain) }

    override suspend fun getById(id: String): Result<Product> = withContext(Dispatchers.IO) {
        runCatching {
            q.getProductById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Product not found: $id", operation = "getProductById")
                )
        }.fold(
            onSuccess = { row -> Result.Success(ProductMapper.toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun search(query: String, categoryId: String?): Flow<List<Product>> =
        when {
            query.isBlank() && categoryId == null -> getAll()
            query.isBlank() -> q.getProductsByCategory(categoryId!!)
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map(ProductMapper::toDomain) }
            else -> {
                val ftsQuery = toFtsQuery(query)
                q.searchProducts(ftsQuery)
                    .asFlow()
                    .mapToList(Dispatchers.IO)
                    .map { rows ->
                        rows.map(ProductMapper::toDomain)
                            .filter { p -> categoryId == null || p.categoryId == categoryId }
                    }
            }
        }

    override suspend fun getByBarcode(barcode: String): Result<Product> = withContext(Dispatchers.IO) {
        runCatching {
            q.getProductByBarcode(barcode).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("No product for barcode: $barcode", operation = "getByBarcode")
                )
        }.fold(
            onSuccess = { row -> Result.Success(ProductMapper.toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        q.countProducts().executeAsOne().toInt()
    }

    // ── Write ────────────────────────────────────────────────────────────────

    override suspend fun insert(product: Product): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProductMapper.toInsertParams(product)
            db.transaction {
                q.insertProduct(
                    id = p.id, name = p.name, barcode = p.barcode, sku = p.sku,
                    category_id = p.categoryId, unit_id = p.unitId, price = p.price,
                    cost_price = p.costPrice, tax_group_id = p.taxGroupId,
                    stock_qty = p.stockQty, min_stock_qty = p.minStockQty,
                    image_url = p.imageUrl, description = p.description,
                    is_active = p.isActive, created_at = p.createdAt,
                    updated_at = p.updatedAt, sync_status = p.syncStatus,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.PRODUCT, p.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", operation = "insertProduct", cause = t)) },
        )
    }

    override suspend fun update(product: Product): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProductMapper.toInsertParams(product)
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateProduct(
                    name = p.name, barcode = p.barcode, sku = p.sku,
                    category_id = p.categoryId, unit_id = p.unitId,
                    price = p.price, cost_price = p.costPrice,
                    tax_group_id = p.taxGroupId, min_stock_qty = p.minStockQty,
                    image_url = p.imageUrl, description = p.description,
                    is_active = p.isActive, updated_at = now,
                    sync_status = "PENDING", id = p.id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.PRODUCT, p.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", operation = "updateProduct", cause = t)) },
        )
    }

    // ── FTS ────────────────────────────────────────────────────────────────

    /**
     * Converts a user-typed search string into an FTS5 query with prefix matching.
     * "coff cak" → "coff* cak*" (each token gets a prefix wildcard).
     */
    private fun toFtsQuery(raw: String): String =
        raw.trim()
            .replace("\"", "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }

    // ── Delete ────────────────────────────────────────────────────────────────

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val row = q.getProductById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Product not found: $id", operation = "deleteProduct")
                )
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                // Soft-delete: set is_active = 0
                q.updateProduct(
                    name = row.name, barcode = row.barcode, sku = row.sku,
                    category_id = row.category_id, unit_id = row.unit_id,
                    price = row.price, cost_price = row.cost_price,
                    tax_group_id = row.tax_group_id, min_stock_qty = row.min_stock_qty,
                    image_url = row.image_url, description = row.description,
                    is_active = 0L, updated_at = now,
                    sync_status = "PENDING", id = id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.PRODUCT, id, SyncOperation.Operation.DELETE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", operation = "deleteProduct", cause = t)) },
        )
    }
}

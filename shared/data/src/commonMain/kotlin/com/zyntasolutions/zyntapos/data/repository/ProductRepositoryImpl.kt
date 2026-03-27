package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.core.pagination.PaginatedResult
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.ProductMapper
import com.zyntasolutions.zyntapos.data.remote.dto.ProductDto
import com.zyntasolutions.zyntapos.data.util.SyncJson
import com.zyntasolutions.zyntapos.data.util.dbCall
import com.zyntasolutions.zyntapos.data.util.toFtsQuery
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

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

    override suspend fun getById(id: String): Result<Product> = dbCall("getProductById") {
        val row = q.getProductById(id).executeAsOneOrNull()
            ?: throw DatabaseException("Product not found: $id", operation = "getProductById")
        ProductMapper.toDomain(row)
    }

    override fun search(query: String, categoryId: String?): Flow<List<Product>> =
        when {
            query.isBlank() && categoryId == null -> getAll()
            query.isBlank() -> q.getProductsByCategory(categoryId!!)
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map(ProductMapper::toDomain) }
            else -> {
                q.searchProducts(query.toFtsQuery())
                    .asFlow()
                    .mapToList(Dispatchers.IO)
                    .map { rows ->
                        rows.map(ProductMapper::toDomain)
                            .filter { p -> categoryId == null || p.categoryId == categoryId }
                    }
            }
        }

    override suspend fun getByBarcode(barcode: String): Result<Product> = dbCall("getProductByBarcode") {
        val row = q.getProductByBarcode(barcode).executeAsOneOrNull()
            ?: throw DatabaseException("No product for barcode: $barcode", operation = "getByBarcode")
        ProductMapper.toDomain(row)
    }

    override suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        q.countProducts().executeAsOne().toInt()
    }

    // ── Write ────────────────────────────────────────────────────────────────

    override suspend fun insert(product: Product): Result<Unit> = dbCall("insertProduct") {
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
                master_product_id = p.masterProductId,
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.PRODUCT, p.id, SyncOperation.Operation.INSERT)
        }
    }

    override suspend fun update(product: Product): Result<Unit> = dbCall("updateProduct") {
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
    }

    // ── Sync (server-originated) ────────────────────────────────────────

    /**
     * Applies a server-authoritative product snapshot from a sync delta payload.
     *
     * Uses INSERT (new) or UPDATE (existing) depending on local presence.
     * Does NOT enqueue a [SyncOperation] — server data must not be re-pushed.
     * [sync_status] is always "SYNCED" for server-originated records.
     */
    suspend fun upsertFromSync(payload: String) = withContext(Dispatchers.IO) {
        val dto = SyncJson.decodeFromString<ProductDto>(payload)
        val exists = q.getProductById(dto.id).executeAsOneOrNull() != null
        val isActive = if (dto.isActive) 1L else 0L
        if (exists) {
            q.updateProduct(
                name = dto.name, barcode = dto.barcode, sku = dto.sku,
                category_id = dto.categoryId, unit_id = dto.unitId ?: "",
                price = dto.price, cost_price = dto.costPrice,
                tax_group_id = dto.taxGroupId, min_stock_qty = dto.minStockQty,
                image_url = dto.imageUrl, description = dto.description,
                is_active = isActive, updated_at = dto.updatedAt,
                sync_status = "SYNCED", id = dto.id,
            )
        } else {
            q.insertProduct(
                id = dto.id, name = dto.name, barcode = dto.barcode, sku = dto.sku,
                category_id = dto.categoryId, unit_id = dto.unitId ?: "", price = dto.price,
                cost_price = dto.costPrice, tax_group_id = dto.taxGroupId,
                stock_qty = dto.stockQty, min_stock_qty = dto.minStockQty,
                image_url = dto.imageUrl, description = dto.description,
                is_active = isActive, created_at = dto.createdAt,
                updated_at = dto.updatedAt, sync_status = "SYNCED",
                master_product_id = dto.masterProductId,
            )
        }
    }

    // ── Paginated ──────────────────────────────────────────────────────────────

    override suspend fun getPage(
        pageRequest: PageRequest,
        categoryId: String?,
        searchQuery: String?,
    ): PaginatedResult<Product> = withContext(Dispatchers.IO) {
        val limit = pageRequest.limit.toLong()
        val offset = pageRequest.offset.toLong()

        val (rows, totalCount) = when {
            searchQuery != null && searchQuery.isNotBlank() -> {
                val ftsQuery = searchQuery.toFtsQuery()
                val items = q.searchProductsPage(ftsQuery, limit, offset).executeAsList()
                    .map(ProductMapper::toDomain)
                    .filter { p -> categoryId == null || p.categoryId == categoryId }
                val count = q.countSearchProducts(ftsQuery).executeAsOne()
                items to count
            }
            categoryId != null -> {
                val items = q.getProductsByCategoryPage(categoryId, limit, offset).executeAsList()
                    .map(ProductMapper::toDomain)
                val count = q.countProductsByCategory(categoryId).executeAsOne()
                items to count
            }
            else -> {
                val items = q.getProductsPage(limit, offset).executeAsList()
                    .map(ProductMapper::toDomain)
                val count = q.countProducts().executeAsOne()
                items to count
            }
        }

        PaginatedResult(
            items = rows,
            totalCount = totalCount,
            hasMore = (pageRequest.offset + rows.size) < totalCount,
        )
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    override suspend fun delete(id: String): Result<Unit> = dbCall("deleteProduct") {
        val row = q.getProductById(id).executeAsOneOrNull()
            ?: throw DatabaseException("Product not found: $id", operation = "deleteProduct")
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
    }
}

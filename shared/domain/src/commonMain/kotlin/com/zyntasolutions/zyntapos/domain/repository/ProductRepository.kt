package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.core.pagination.PaginatedResult
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Product
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all product catalogue operations.
 *
 * All [Flow]-returning methods reflect live SQLDelight queries — callers receive
 * new emissions whenever the underlying table changes. This drives the reactive
 * product grid in the POS and Inventory screens.
 *
 * FTS5 full-text search is delegated to the data layer; this interface exposes
 * only the intent, not the mechanism.
 */
interface ProductRepository {

    /**
     * Emits the full active product list, ordered by name.
     * Re-emits on any insert, update, or delete in the products table.
     */
    fun getAll(): Flow<List<Product>>

    /**
     * Returns a single [Product] by its UUID [id].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no product with that ID exists.
     */
    suspend fun getById(id: String): Result<Product>

    /**
     * Emits products whose name, SKU, or description match [query],
     * optionally filtered to a specific [categoryId].
     *
     * The underlying implementation uses FTS5 for sub-50 ms response at 10K+ rows.
     * Passing an empty [query] returns the same result as [getAll] (optionally filtered by category).
     *
     * @param query      Free-text search string.
     * @param categoryId Optional category filter. Pass `null` to search across all categories.
     */
    fun search(query: String, categoryId: String? = null): Flow<List<Product>>

    /**
     * Looks up a product by its [barcode] (EAN-13, Code128, QR, etc.).
     *
     * Designed for the critical sub-200 ms POS barcode scan path — must complete
     * within one SQLite index lookup.
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no product with that barcode exists.
     */
    suspend fun getByBarcode(barcode: String): Result<Product>

    /**
     * Inserts a new [product] into the local database and enqueues a sync operation.
     *
     * The caller must ensure barcode and SKU uniqueness before calling this method
     * (use [getByBarcode] + a SKU lookup beforehand, enforced by [CreateProductUseCase]).
     */
    suspend fun insert(product: Product): Result<Unit>

    /**
     * Persists all mutable fields of the given [product] and enqueues a sync operation.
     *
     * The [product] must already exist in the database (matched by [Product.id]).
     */
    suspend fun update(product: Product): Result<Unit>

    /**
     * Soft-deletes the product identified by [id] by setting `is_active = 0`.
     *
     * Products are never hard-deleted to preserve historical order line integrity.
     * The data layer must also enqueue a sync operation for the deletion.
     */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Returns the total number of products in the local database.
     *
     * Used for quick diagnostics (e.g., empty-state checks, import previews).
     */
    suspend fun getCount(): Int

    // ── Paginated queries (for infinite-scroll UI) ─────────────────────────

    /**
     * Returns a page of active products, ordered by name.
     *
     * @param pageRequest Offset-based pagination parameters.
     * @param categoryId Optional category filter. Pass `null` for all categories.
     * @param searchQuery Optional FTS5 search query. Pass `null` for no filtering.
     */
    suspend fun getPage(
        pageRequest: PageRequest,
        categoryId: String? = null,
        searchQuery: String? = null,
    ): PaginatedResult<Product>
}

package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

/**
 * Searches products by name, barcode, SKU, or category using full-text search (FTS5).
 *
 * ### Business Rules
 * 1. Delegates to [ProductRepository.search] which executes FTS5 queries against
 *    the `product_fts` virtual table (name, barcode, sku, description fields).
 * 2. A **debounce** of [DEBOUNCE_MS] (300 ms) is applied to the returned [Flow],
 *    preventing excessive DB queries during rapid keystrokes.
 * 3. An empty [query] returns ALL active products (via a non-filtered query).
 * 4. Results are ordered by relevance score (FTS5 rank) descending.
 *
 * @param productRepository FTS5-capable search gateway.
 */
class SearchProductsUseCase(
    private val productRepository: ProductRepository,
) {
    /**
     * @param query      Free-text search string (name, barcode, SKU, category).
     *                   Pass an empty string to retrieve all products.
     * @param categoryId Optional category filter applied after FTS5 matching.
     * @return A debounced [Flow] emitting ranked [Product] lists on each query change.
     */
    @OptIn(FlowPreview::class)
    operator fun invoke(
        query: String,
        categoryId: String? = null,
    ): Flow<List<Product>> =
        productRepository.search(query, categoryId)
            .debounce(DEBOUNCE_MS)

    companion object {
        /** Debounce delay in milliseconds — prevents DB thrash on rapid keystrokes. */
        const val DEBOUNCE_MS = 300L
    }
}

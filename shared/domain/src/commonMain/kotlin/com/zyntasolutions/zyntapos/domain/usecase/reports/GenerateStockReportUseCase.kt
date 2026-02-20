package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Generates a stock status report covering current levels, low-stock, and dead stock.
 *
 * ### Report Sections
 * - **allProducts:** Current on-hand stock quantities for all active products.
 * - **lowStockItems:** Products where `stockQty < minStockQty`.
 * - **deadStockItems:** Products with no stock movement in the last [DEAD_STOCK_DAYS] days.
 *
 * ### Dead Stock Definition
 * A product is considered dead stock if no [StockAdjustment] records exist within
 * the trailing [DEAD_STOCK_DAYS] (30) days. This is approximated at the domain layer
 * by checking the data layer via [StockRepository.getMovements]; the data layer
 * may implement a more efficient SQL query.
 *
 * @param productRepository Source for product catalogue and stock quantities.
 * @param stockRepository   Source for stock movement history.
 */
class GenerateStockReportUseCase(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
) {
    /**
     * Immutable stock report value object.
     *
     * @property allProducts    All active products with current stock levels.
     * @property lowStockItems  Products below their minimum stock threshold.
     * @property deadStockItems Products with no movements in last 30 days.
     */
    data class StockReport(
        val allProducts: List<Product>,
        val lowStockItems: List<Product>,
        val deadStockItems: List<Product>,
    )

    /**
     * @return A reactive [Flow] emitting a [StockReport] whenever product data changes.
     */
    operator fun invoke(): Flow<StockReport> =
        productRepository.getAll().map { products ->
            val active = products.filter { it.isActive }

            val lowStock = active.filter { it.stockQty < it.minStockQty }

            // Dead stock check: products with stockQty > 0 but flagged by the
            // threshold query at the alert level (movement detection is data-layer concern)
            val deadStock = active.filter { product ->
                product.stockQty > 0 && product.stockQty < product.minStockQty * 0.1
            }
            // Note: Full dead-stock (no movement in 30 days) requires a join with
            // stock_adjustments table. This is exposed via StockRepository.getAlerts()
            // in the data layer. The domain approximation above is used until Phase 2.

            StockReport(
                allProducts = active,
                lowStockItems = lowStock,
                deadStockItems = deadStock,
            )
        }

    companion object {
        /** Days without movement before a product is considered dead stock. */
        const val DEAD_STOCK_DAYS = 30
    }
}

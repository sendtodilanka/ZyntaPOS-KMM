package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Aggregates completed sales orders over a date range into a summary report.
 *
 * ### Report Contents
 * - **totalSales:** Sum of all [Order.total] for COMPLETED orders in the range.
 * - **orderCount:** Number of completed orders.
 * - **avgOrderValue:** `totalSales / orderCount` (0 if no orders).
 * - **topProducts:** Top 10 products by revenue (productId → totalRevenue).
 * - **salesByPaymentMethod:** Revenue breakdown per [PaymentMethod].
 *
 * Only orders with status [OrderStatus.COMPLETED] are included.
 * VOIDED and HELD orders are excluded from all aggregates.
 *
 * @param orderRepository Source of historical order data.
 */
class GenerateSalesReportUseCase(
    private val orderRepository: OrderRepository,
) {

    /**
     * Immutable report value object returned by this use case.
     *
     * @property from                 Report start date (inclusive).
     * @property to                   Report end date (inclusive).
     * @property totalSales           Grand total revenue.
     * @property orderCount           Number of completed orders.
     * @property avgOrderValue        Average order value.
     * @property topProducts          Map of productId → total revenue, sorted descending, top 10.
     * @property salesByPaymentMethod Map of [PaymentMethod] → total amount.
     */
    data class SalesReport(
        val from: Instant,
        val to: Instant,
        val totalSales: Double,
        val orderCount: Int,
        val avgOrderValue: Double,
        val topProducts: Map<String, Double>,
        val salesByPaymentMethod: Map<PaymentMethod, Double>,
    )

    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting a new [SalesReport] whenever the underlying order data changes.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<SalesReport> =
        orderRepository.getByDateRange(from, to).map { orders ->
            val completed = orders.filter { it.status == OrderStatus.COMPLETED }

            val totalSales = completed.sumOf { it.total }
            val orderCount = completed.size
            val avgOrderValue = if (orderCount > 0) totalSales / orderCount else 0.0

            val productRevenue = mutableMapOf<String, Double>()
            for (order in completed) {
                for (item in order.items) {
                    productRevenue[item.productId] =
                        (productRevenue[item.productId] ?: 0.0) + item.lineTotal
                }
            }
            val topProducts = productRevenue.entries
                .sortedByDescending { it.value }
                .take(10)
                .associate { it.key to it.value }

            val salesByMethod = completed.groupBy { it.paymentMethod }
                .mapValues { (_, orderList) -> orderList.sumOf { it.total } }

            SalesReport(
                from = from,
                to = to,
                totalSales = totalSales,
                orderCount = orderCount,
                avgOrderValue = avgOrderValue,
                topProducts = topProducts,
                salesByPaymentMethod = salesByMethod,
            )
        }
}

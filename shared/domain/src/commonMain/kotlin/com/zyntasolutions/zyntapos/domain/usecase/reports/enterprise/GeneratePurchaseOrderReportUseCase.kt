package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.PurchaseOrderData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a purchase order report for a given date range.
 *
 * Returns all purchase orders raised within the period, including order number,
 * supplier, line items, total value, order status, and expected/actual delivery dates.
 * Supports procurement tracking and accounts-payable reconciliation.
 *
 * @param reportRepository Source for purchase order data.
 */
class GeneratePurchaseOrderReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [PurchaseOrderData] within the date range.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<PurchaseOrderData>> = flow {
        emit(reportRepository.getPurchaseOrders(from, to))
    }
}

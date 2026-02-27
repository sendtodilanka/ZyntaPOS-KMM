package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.SupplierPurchaseData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a supplier purchase report summarising all purchase activity per supplier
 * within a date range.
 *
 * Returns per-supplier totals including number of purchase orders, total spend,
 * average order value, and lead-time metrics.
 *
 * @param reportRepository Source for supplier purchase data.
 */
class GenerateSupplierPurchaseReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [SupplierPurchaseData] per supplier.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<SupplierPurchaseData>> = flow {
        emit(reportRepository.getSupplierPurchases(from, to))
    }
}

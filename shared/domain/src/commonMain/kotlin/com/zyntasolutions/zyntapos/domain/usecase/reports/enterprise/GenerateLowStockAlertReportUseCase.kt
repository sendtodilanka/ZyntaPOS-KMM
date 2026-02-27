package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StockReorderData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates a low-stock alert report listing products requiring immediate reorder attention.
 *
 * Returns all products where current stock quantity is at or below the configured
 * reorder point. Provides suggested order quantities and preferred supplier details
 * to accelerate the purchasing workflow.
 *
 * @param reportRepository Source for low-stock alert data.
 */
class GenerateLowStockAlertReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @return A [Flow] emitting the list of [StockReorderData] for products requiring reorder.
     */
    operator fun invoke(): Flow<List<StockReorderData>> = flow {
        emit(reportRepository.getStockReorderAlerts())
    }
}

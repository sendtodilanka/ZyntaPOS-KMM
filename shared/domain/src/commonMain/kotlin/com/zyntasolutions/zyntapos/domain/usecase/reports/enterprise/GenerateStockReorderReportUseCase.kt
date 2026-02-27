package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StockReorderData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates a stock reorder alert report listing products that have fallen below reorder level.
 *
 * Surfaces all products where current stock quantity is at or below the configured
 * reorder point, along with suggested order quantities and preferred suppliers.
 *
 * @param reportRepository Source for stock reorder alert data.
 */
class GenerateStockReorderReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @return A [Flow] emitting the list of [StockReorderData] for products requiring reorder.
     */
    operator fun invoke(): Flow<List<StockReorderData>> = flow {
        emit(reportRepository.getStockReorderAlerts())
    }
}

package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StockAgingData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates a stock aging report identifying products with no sales activity.
 *
 * Flags products that have had no sales movement beyond the specified threshold,
 * enabling buyers to action slow-moving or dead inventory before it becomes a write-off.
 *
 * @param reportRepository Source for stock aging data.
 */
class GenerateStockAgingReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param noSalesDays Number of consecutive days without a sale to qualify a product
     *                    as aged stock. Defaults to 30 days.
     * @return A [Flow] emitting the list of [StockAgingData] for aged products.
     */
    operator fun invoke(noSalesDays: Int = 30): Flow<List<StockAgingData>> = flow {
        emit(reportRepository.getStockAging(noSalesDays))
    }
}

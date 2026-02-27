package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.InventoryValuationData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates an inventory valuation report showing the current monetary value of all stock.
 *
 * Returns per-product inventory valuation data including quantity on hand, unit cost,
 * and total stock value. The aggregate total represents the book value of current
 * inventory for accounting and insurance purposes.
 *
 * @param reportRepository Source for inventory valuation data.
 */
class GenerateInventoryValuationReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @return A [Flow] emitting the list of [InventoryValuationData] for all stocked products.
     */
    operator fun invoke(): Flow<List<InventoryValuationData>> = flow {
        emit(reportRepository.getInventoryValuation())
    }
}

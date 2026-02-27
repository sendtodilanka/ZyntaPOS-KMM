package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.WarehouseInventoryData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates a complete warehouse inventory snapshot report.
 *
 * Returns current stock levels, storage locations (rack/bin), and quantity
 * on hand for all products across all warehouse locations.
 *
 * @param reportRepository Source for warehouse inventory data.
 */
class GenerateWarehouseInventoryReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @return A [Flow] emitting the complete list of [WarehouseInventoryData] across all warehouses.
     */
    operator fun invoke(): Flow<List<WarehouseInventoryData>> = flow {
        emit(reportRepository.getWarehouseInventory())
    }
}

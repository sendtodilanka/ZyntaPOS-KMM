package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StockTransferRecord
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a report of all inter-store stock transfers for a date range.
 *
 * Lists every transfer record including source store, destination store,
 * product details, quantities transferred, and transfer status.
 *
 * @param reportRepository Source for inter-store transfer data.
 */
class GenerateInterStoreTransferReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [StockTransferRecord] within the range.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<StockTransferRecord>> = flow {
        emit(reportRepository.getInterStoreTransfers(from, to))
    }
}

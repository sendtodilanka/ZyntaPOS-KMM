package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Retrieves KPIs for all stores within a date range.
 *
 * Wraps [ReportRepository.getMultiStoreComparison] which queries
 * local orders grouped by store_id, joined with the stores table
 * for name resolution.
 */
class GetMultiStoreKPIsUseCase(
    private val reportRepository: ReportRepository,
) {
    operator fun invoke(from: Instant, to: Instant): Flow<List<StoreSalesData>> = flow {
        emit(reportRepository.getMultiStoreComparison(from, to))
    }
}

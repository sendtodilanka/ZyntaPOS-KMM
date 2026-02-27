package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.ProductVolumeData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns the top products ranked by units sold for a given date range. */
class GenerateTopProductsReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from  Start of the reporting window (inclusive).
     * @param to    End of the reporting window (inclusive).
     * @param limit Maximum number of products to return (default 20).
     * @return A [Flow] emitting a list of [ProductVolumeData] ordered by
     *         [ProductVolumeData.rank] ascending (rank 1 = highest volume).
     */
    operator fun invoke(
        from: Instant,
        to: Instant,
        limit: Int = 20,
    ): Flow<List<ProductVolumeData>> = flow {
        emit(reportRepository.getTopProductsByVolume(from, to, limit))
    }
}

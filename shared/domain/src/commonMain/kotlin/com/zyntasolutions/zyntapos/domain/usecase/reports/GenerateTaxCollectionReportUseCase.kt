package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.TaxCollectionData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns tax collection totals grouped by tax group for a given date range. */
class GenerateTaxCollectionReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting a list of [TaxCollectionData] with taxable amount
     *         and tax collected per tax group.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<TaxCollectionData>> = flow {
        emit(reportRepository.getTaxCollection(from, to))
    }
}

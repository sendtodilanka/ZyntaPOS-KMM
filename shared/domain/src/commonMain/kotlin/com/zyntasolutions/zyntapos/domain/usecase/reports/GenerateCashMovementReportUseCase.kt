package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.CashMovementRecord
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns the cash-in / cash-out movement log for a given date range. */
class GenerateCashMovementReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting a list of [CashMovementRecord] ordered by
     *         [CashMovementRecord.recordedAt] ascending.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<CashMovementRecord>> = flow {
        emit(reportRepository.getCashMovementLog(from, to))
    }
}

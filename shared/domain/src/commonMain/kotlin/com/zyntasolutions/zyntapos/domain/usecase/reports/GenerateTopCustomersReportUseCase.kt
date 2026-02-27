package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.CustomerSpendData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns the top customers ranked by total spend for a given date range. */
class GenerateTopCustomersReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from  Start of the reporting window (inclusive).
     * @param to    End of the reporting window (inclusive).
     * @param limit Maximum number of customers to return (default 20).
     * @return A [Flow] emitting a list of [CustomerSpendData] ordered by
     *         [CustomerSpendData.totalSpend] descending.
     */
    operator fun invoke(
        from: Instant,
        to: Instant,
        limit: Int = 20,
    ): Flow<List<CustomerSpendData>> = flow {
        emit(reportRepository.getTopCustomers(from, to, limit))
    }
}

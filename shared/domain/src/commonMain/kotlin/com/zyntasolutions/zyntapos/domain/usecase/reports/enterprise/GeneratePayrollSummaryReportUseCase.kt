package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.PayrollSummaryData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates a payroll summary report for a specific pay period.
 *
 * Returns per-employee payroll records including gross pay, deductions,
 * net pay, and payment status for the given pay period.
 *
 * @param reportRepository Source for payroll summary data.
 */
class GeneratePayrollSummaryReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param payPeriodId Identifier of the pay period to report on.
     * @return A [Flow] emitting the list of [PayrollSummaryData] per employee.
     */
    operator fun invoke(payPeriodId: String): Flow<List<PayrollSummaryData>> = flow {
        emit(reportRepository.getPayrollSummary(payPeriodId))
    }
}

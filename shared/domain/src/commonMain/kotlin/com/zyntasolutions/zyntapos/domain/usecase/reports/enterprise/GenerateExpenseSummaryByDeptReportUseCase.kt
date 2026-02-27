package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.DeptExpenseData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates an expense summary report grouped by department for a given date range.
 *
 * Returns total expenditure, number of expense entries, and category breakdown
 * per department. Supports cost-centre budgeting, departmental P&L, and
 * expense approval workflows.
 *
 * @param reportRepository Source for departmental expense data.
 */
class GenerateExpenseSummaryByDeptReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [DeptExpenseData] per department for the period.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<DeptExpenseData>> = flow {
        emit(reportRepository.getExpensesByDepartment(from, to))
    }
}

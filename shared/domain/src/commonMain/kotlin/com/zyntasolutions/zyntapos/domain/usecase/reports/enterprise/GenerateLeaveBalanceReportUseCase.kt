package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.LeaveBalanceData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * Generates a leave balance report showing remaining leave entitlements for all employees.
 *
 * Calculates accrued, used, and remaining leave days per leave type (annual,
 * sick, unpaid) as of the specified point in time.
 *
 * @param reportRepository Source for leave balance data.
 */
class GenerateLeaveBalanceReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param asOf The point in time at which leave balances are calculated.
     * @return A [Flow] emitting the list of [LeaveBalanceData] per employee.
     */
    operator fun invoke(asOf: Instant): Flow<List<LeaveBalanceData>> = flow {
        emit(reportRepository.getLeaveBalances(asOf))
    }
}

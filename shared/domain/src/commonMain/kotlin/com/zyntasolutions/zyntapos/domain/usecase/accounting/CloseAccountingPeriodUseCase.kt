package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository

/**
 * Closes an OPEN accounting period.
 *
 * Before closing, rebuilds the account balance cache for all accounts in the period.
 * After the rebuild, transitions the period status from OPEN to CLOSED.
 *
 * Returns ValidationException if the period is already CLOSED or LOCKED.
 */
class CloseAccountingPeriodUseCase(
    private val periodRepository: AccountingPeriodRepository,
    private val statementRepository: FinancialStatementRepository,
) {
    suspend fun execute(periodId: String, storeId: String, now: Long): Result<Unit> {
        val periodResult = periodRepository.getById(periodId)
        if (periodResult is Result.Error) return periodResult
        val period = (periodResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Accounting period not found: $periodId",
                    field = "periodId",
                    rule = "NOT_FOUND",
                ),
            )

        if (period.status == PeriodStatus.CLOSED || period.status == PeriodStatus.LOCKED) {
            return Result.Error(
                ValidationException(
                    "Accounting period '${period.periodName}' is already ${period.status.name.lowercase()}.",
                    field = "status",
                    rule = "INVALID_STATUS_TRANSITION",
                ),
            )
        }

        // Rebuild balance cache before closing
        val rebuildResult = statementRepository.rebuildAllBalances(storeId, periodId)
        if (rebuildResult is Result.Error) return rebuildResult

        return periodRepository.closePeriod(periodId, now)
    }
}

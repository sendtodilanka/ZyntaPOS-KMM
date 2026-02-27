package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository

/**
 * Re-opens a CLOSED accounting period (admin action).
 *
 * LOCKED periods cannot be reopened. Returns ValidationException if the period
 * is LOCKED or already OPEN.
 */
class ReopenAccountingPeriodUseCase(
    private val periodRepository: AccountingPeriodRepository,
) {
    suspend fun execute(periodId: String, now: Long): Result<Unit> {
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

        if (period.status == PeriodStatus.LOCKED) {
            return Result.Error(
                ValidationException(
                    "Accounting period '${period.periodName}' is permanently locked and cannot be reopened.",
                    field = "status",
                    rule = "PERIOD_LOCKED",
                ),
            )
        }

        if (period.status == PeriodStatus.OPEN) {
            return Result.Error(
                ValidationException(
                    "Accounting period '${period.periodName}' is already open.",
                    field = "status",
                    rule = "ALREADY_OPEN",
                ),
            )
        }

        return periodRepository.reopenPeriod(periodId, now)
    }
}

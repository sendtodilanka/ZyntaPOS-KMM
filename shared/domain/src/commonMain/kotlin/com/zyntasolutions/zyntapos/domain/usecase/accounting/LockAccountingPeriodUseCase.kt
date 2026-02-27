package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository

/**
 * Permanently locks a CLOSED accounting period.
 *
 * Only periods in the CLOSED state can be locked. Once locked, no changes
 * of any kind are permitted. Returns ValidationException if the period is
 * not in the CLOSED state.
 */
class LockAccountingPeriodUseCase(
    private val periodRepository: AccountingPeriodRepository,
) {
    suspend fun execute(periodId: String, lockedBy: String, lockedAt: Long): Result<Unit> {
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

        if (period.status != PeriodStatus.CLOSED) {
            return Result.Error(
                ValidationException(
                    "Accounting period '${period.periodName}' must be CLOSED before it can be locked. " +
                        "Current status: ${period.status.name}.",
                    field = "status",
                    rule = "INVALID_STATUS_TRANSITION",
                ),
            )
        }

        return periodRepository.lockPeriod(periodId, lockedBy, lockedAt)
    }
}

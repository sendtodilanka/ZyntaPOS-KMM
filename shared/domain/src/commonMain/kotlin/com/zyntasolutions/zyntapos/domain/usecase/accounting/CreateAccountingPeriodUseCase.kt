package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository

/**
 * Creates a new accounting period.
 *
 * Business rules enforced:
 * - startDate must be before endDate.
 * - No overlapping OPEN period may exist for the same storeId.
 *
 * @param period The accounting period to create.
 * @param storeId The store that owns this period (used for overlap checking).
 */
class CreateAccountingPeriodUseCase(
    private val periodRepository: AccountingPeriodRepository,
) {
    suspend fun execute(period: AccountingPeriod, storeId: String): Result<Unit> {
        if (period.startDate >= period.endDate) {
            return Result.Error(
                ValidationException(
                    "Period startDate '${period.startDate}' must be before endDate '${period.endDate}'.",
                    field = "startDate",
                    rule = "INVALID_DATE_RANGE",
                ),
            )
        }

        // Check for overlapping open periods
        val openPeriodsResult = periodRepository.getOpenPeriods(storeId)
        if (openPeriodsResult is Result.Error) return openPeriodsResult
        val openPeriods = (openPeriodsResult as Result.Success).data

        val overlapping = openPeriods.any { existing ->
            existing.id != period.id &&
                period.startDate <= existing.endDate &&
                period.endDate >= existing.startDate
        }

        if (overlapping) {
            return Result.Error(
                ValidationException(
                    "An overlapping open accounting period already exists for this store.",
                    field = "startDate",
                    rule = "OVERLAPPING_PERIOD",
                ),
            )
        }

        return periodRepository.create(period)
    }
}

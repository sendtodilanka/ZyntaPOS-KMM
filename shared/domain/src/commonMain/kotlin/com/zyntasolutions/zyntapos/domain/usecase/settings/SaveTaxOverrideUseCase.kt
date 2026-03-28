package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository

/**
 * Validates and persists a [RegionalTaxOverride] (insert or update via upsert).
 *
 * ### Business Rules
 * 1. [RegionalTaxOverride.taxGroupId] must not be blank.
 * 2. [RegionalTaxOverride.storeId] must not be blank.
 * 3. [RegionalTaxOverride.effectiveRate] must be in [0.0, 100.0] (enforced by model
 *    `require` check; caught here as a [ValidationException] for UI feedback).
 *
 * @param repository Source of truth for override persistence.
 */
class SaveTaxOverrideUseCase(
    private val repository: RegionalTaxOverrideRepository,
) {
    suspend operator fun invoke(override: RegionalTaxOverride): Result<Unit> {
        if (override.taxGroupId.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Tax group ID must not be blank.",
                    field = "taxGroupId",
                    rule = "REQUIRED",
                ),
            )
        }
        if (override.storeId.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Store ID must not be blank.",
                    field = "storeId",
                    rule = "REQUIRED",
                ),
            )
        }
        if (override.effectiveRate < 0.0 || override.effectiveRate > 100.0) {
            return Result.Error(
                ValidationException(
                    "Tax rate must be between 0.0 and 100.0.",
                    field = "effectiveRate",
                    rule = "RANGE",
                ),
            )
        }
        return repository.upsert(override)
    }
}

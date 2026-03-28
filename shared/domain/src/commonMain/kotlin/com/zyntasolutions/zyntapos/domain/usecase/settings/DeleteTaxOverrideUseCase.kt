package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository

/**
 * Deletes a [com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride] by ID.
 *
 * ### Business Rules
 * 1. [id] must not be blank.
 *
 * @param repository Source of truth for override persistence.
 */
class DeleteTaxOverrideUseCase(
    private val repository: RegionalTaxOverrideRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Tax override ID must not be blank.",
                    field = "id",
                    rule = "REQUIRED",
                ),
            )
        }
        return repository.delete(id)
    }
}

package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository

/**
 * Validates and persists a [TaxGroup] (insert or update).
 *
 * **Validation rules:**
 * - [TaxGroup.name] must not be blank.
 * - [TaxGroup.rate] must be in the range **0.0 – 100.0** (inclusive).
 *   (This mirrors the `TaxGroup` model's `init` block but is re-checked
 *   here so the use-case layer returns a typed [ValidationException]
 *   rather than an uncaught [IllegalArgumentException].)
 *
 * The persistence contract (`TaxGroupRepository`) further validates
 * name uniqueness at the database level.
 *
 * @param taxGroupRepository Persistence layer for tax group data.
 */
class SaveTaxGroupUseCase(
    private val taxGroupRepository: TaxGroupRepository,
) {

    /**
     * Saves [taxGroup] after validating all business rules.
     *
     * @param isUpdate When `true`, [TaxGroupRepository.update] is called;
     *                 otherwise [TaxGroupRepository.insert] is used.
     * @return [Result.Success] on success.
     * @return [Result.Error] wrapping a [ValidationException] for rule violations.
     */
    suspend operator fun invoke(
        taxGroup: TaxGroup,
        isUpdate: Boolean,
    ): Result<Unit> {
        // ── Name ──────────────────────────────────────────────────────────────
        if (taxGroup.name.isBlank()) {
            return Result.Error(
                ValidationException(
                    message = "Tax group name is required.",
                    field = "name",
                    rule = "REQUIRED",
                )
            )
        }

        // ── Rate range ────────────────────────────────────────────────────────
        // Note: TaxGroup.init also asserts rate in 0.0..100.0, so this guard
        // fires from the ViewModel/UI layer before constructing the model.
        @Suppress("ConvertTwoComparisonsToRangeCheck")
        if (taxGroup.rate < 0.0 || taxGroup.rate > 100.0) {
            return Result.Error(
                ValidationException(
                    message = "Tax rate must be between 0.0 and 100.0 (got ${taxGroup.rate}).",
                    field = "rate",
                    rule = "RANGE_VIOLATION",
                )
            )
        }

        return if (isUpdate) {
            taxGroupRepository.update(taxGroup)
        } else {
            taxGroupRepository.insert(taxGroup)
        }
    }
}

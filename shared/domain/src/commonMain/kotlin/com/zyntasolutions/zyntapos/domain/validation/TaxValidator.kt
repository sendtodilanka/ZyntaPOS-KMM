package com.zyntasolutions.zyntapos.domain.validation

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.TaxGroup

/**
 * Validates tax configuration rules to ensure tax groups are correctly configured
 * before they are applied to products and orders.
 *
 * All methods are pure functions (no I/O) and may be called synchronously.
 */
object TaxValidator {

    /**
     * Validates the tax rate of a [TaxGroup].
     *
     * ### Rules
     * 1. [rate] must be within the inclusive range [0.0, 100.0].
     * 2. [rate] must not be NaN or infinite.
     *
     * @param rate The tax rate percentage to validate.
     * @return [Result.Success] or [Result.Error] with [ValidationException].
     */
    fun validateRate(rate: Double): Result<Unit> {
        if (rate.isNaN() || rate.isInfinite()) {
            return Result.Error(
                ValidationException(
                    "Tax rate must be a finite number. Got $rate.",
                    field = "rate",
                    rule = "INVALID_RATE",
                ),
            )
        }
        if (rate < 0.0 || rate > 100.0) {
            return Result.Error(
                ValidationException(
                    "Tax rate must be between 0 and 100. Got $rate.",
                    field = "rate",
                    rule = "RATE_OUT_OF_RANGE",
                ),
            )
        }
        return Result.Success(Unit)
    }

    /**
     * Validates the completeness of a [TaxGroup] before persistence.
     *
     * ### Rules
     * 1. [TaxGroup.name] must not be blank.
     * 2. [TaxGroup.rate] must pass [validateRate].
     * 3. An active [TaxGroup] ([TaxGroup.isActive] == true) must have a non-blank name.
     *
     * @param taxGroup The [TaxGroup] to validate.
     * @return [Result.Success] or [Result.Error].
     */
    fun validateTaxGroup(taxGroup: TaxGroup): Result<Unit> {
        if (taxGroup.name.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Tax group name must not be blank.",
                    field = "name",
                    rule = "REQUIRED",
                ),
            )
        }

        val rateValidation = validateRate(taxGroup.rate)
        if (rateValidation is Result.Error) return rateValidation

        return Result.Success(Unit)
    }

    /**
     * Validates that a collection of tax groups has no duplicate names.
     *
     * @param taxGroups The list of [TaxGroup] objects to check.
     * @return [Result.Success] or [Result.Error] with rule `"DUPLICATE_NAME"`.
     */
    fun validateUniqueness(taxGroups: List<TaxGroup>): Result<Unit> {
        val duplicates = taxGroups.groupBy { it.name.trim().lowercase() }
            .filter { it.value.size > 1 }
            .keys

        return if (duplicates.isNotEmpty()) {
            Result.Error(
                ValidationException(
                    "Duplicate tax group names found: ${duplicates.joinToString()}.",
                    field = "name",
                    rule = "DUPLICATE_NAME",
                ),
            )
        } else {
            Result.Success(Unit)
        }
    }
}

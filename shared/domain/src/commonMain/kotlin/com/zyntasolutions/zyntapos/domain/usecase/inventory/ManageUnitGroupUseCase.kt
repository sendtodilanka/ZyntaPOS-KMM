package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import com.zyntasolutions.zyntapos.domain.repository.UnitGroupRepository

/**
 * Validates and persists a [UnitOfMeasure] (insert or update).
 *
 * **Validation rules:**
 * - [UnitOfMeasure.name] must not be blank.
 * - [UnitOfMeasure.abbreviation] must not be blank.
 * - [UnitOfMeasure.conversionRate] must be strictly > 0.0.
 *   (For base units this is always 1.0; for derived units it must be positive.)
 *
 * Base-unit promotion (ensuring exactly one base unit per group) is enforced
 * by [UnitGroupRepository] at the persistence layer.
 *
 * @param unitGroupRepository Persistence layer for unit-of-measure data.
 */
class ManageUnitGroupUseCase(
    private val unitGroupRepository: UnitGroupRepository,
) {

    /**
     * Saves [unit] after validating all business rules.
     *
     * @param isUpdate When `true`, [UnitGroupRepository.update] is called;
     *                 otherwise [UnitGroupRepository.insert] is used.
     * @return [Result.Success] on success.
     * @return [Result.Error] wrapping a [ValidationException] for rule violations.
     */
    suspend operator fun invoke(
        unit: UnitOfMeasure,
        isUpdate: Boolean,
    ): Result<Unit> {
        // ── Name ──────────────────────────────────────────────────────────────
        if (unit.name.isBlank()) {
            return Result.Error(
                ValidationException(
                    message = "Unit name is required.",
                    field = "name",
                    rule = "REQUIRED",
                )
            )
        }

        // ── Abbreviation ──────────────────────────────────────────────────────
        if (unit.abbreviation.isBlank()) {
            return Result.Error(
                ValidationException(
                    message = "Unit abbreviation is required.",
                    field = "abbreviation",
                    rule = "REQUIRED",
                )
            )
        }

        // ── Conversion rate ───────────────────────────────────────────────────
        if (unit.conversionRate <= 0.0) {
            return Result.Error(
                ValidationException(
                    message = "Conversion rate must be greater than 0 (got ${unit.conversionRate}).",
                    field = "conversionRate",
                    rule = "MIN_VALUE",
                )
            )
        }

        return if (isUpdate) {
            unitGroupRepository.update(unit)
        } else {
            unitGroupRepository.insert(unit)
        }
    }

    /** Removes the unit identified by [unitId]. */
    suspend fun delete(unitId: String): Result<Unit> {
        if (unitId.isBlank()) {
            return Result.Error(
                ValidationException(
                    message = "Unit ID must not be blank.",
                    field = "unitId",
                    rule = "REQUIRED",
                )
            )
        }
        return unitGroupRepository.delete(unitId)
    }
}

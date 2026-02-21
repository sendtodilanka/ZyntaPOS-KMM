package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Unit-of-Measure CRUD and conversion management.
 *
 * Units are organised into groups (e.g., "Weight" group → kg, g, lb).
 * Each group has exactly one base unit ([UnitOfMeasure.isBaseUnit] = true)
 * and all other units express their [UnitOfMeasure.conversionRate] relative
 * to that base unit.
 *
 * **Phase 1 scope:** flat list CRUD; advanced cross-group conversion
 * is deferred to Phase 2.
 */
interface UnitGroupRepository {

    /** Emits all [UnitOfMeasure]s ordered by name. Re-emits on any change. */
    fun getAll(): Flow<List<UnitOfMeasure>>

    /**
     * Returns a single [UnitOfMeasure] by UUID [id].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZentaException.DatabaseException]
     *         if the unit does not exist.
     */
    suspend fun getById(id: String): Result<UnitOfMeasure>

    /**
     * Inserts a new [unit].
     *
     * Validates that the unit's abbreviation is unique within its group.
     * If [UnitOfMeasure.isBaseUnit] is true, any previously designated base
     * unit in the same group must be demoted to a regular unit.
     */
    suspend fun insert(unit: UnitOfMeasure): Result<Unit>

    /**
     * Updates the mutable fields of [unit].
     *
     * Base-unit promotion rules apply the same as for [insert].
     * [UnitOfMeasure.conversionRate] must be > 0.
     */
    suspend fun update(unit: UnitOfMeasure): Result<Unit>

    /**
     * Deletes the unit identified by [id].
     *
     * Returns [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZentaException.ValidationException]
     * if any active product references this unit, or if the unit is the
     * designated base unit of its group (base unit cannot be deleted).
     */
    suspend fun delete(id: String): Result<Unit>
}

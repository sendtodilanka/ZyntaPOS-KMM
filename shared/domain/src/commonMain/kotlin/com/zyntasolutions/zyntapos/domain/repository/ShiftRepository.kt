package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule
import kotlinx.coroutines.flow.Flow

/**
 * Contract for shift schedule management.
 */
interface ShiftRepository {

    /**
     * Emits the weekly shift schedule for [storeId] between [weekStart] and [weekEnd] (ISO dates).
     * Re-emits on change.
     */
    fun getWeeklySchedule(
        storeId: String,
        weekStart: String,
        weekEnd: String,
    ): Flow<List<ShiftSchedule>>

    /** Emits all shifts for [employeeId]. Re-emits on change. */
    fun getByEmployee(employeeId: String): Flow<List<ShiftSchedule>>

    /**
     * Returns the shift for [employeeId] on [date].
     * Returns null inside [Result.Success] if no shift is scheduled.
     */
    suspend fun getByEmployeeAndDate(employeeId: String, date: String): Result<ShiftSchedule?>

    /**
     * Returns all shifts for [storeId] on a specific [date].
     */
    suspend fun getByStoreAndDate(storeId: String, date: String): Result<List<ShiftSchedule>>

    /**
     * Inserts a new shift. Fails if a shift already exists for the same employee+date.
     */
    suspend fun insert(shift: ShiftSchedule): Result<Unit>

    /** Updates an existing shift's start/end time and notes. */
    suspend fun update(shift: ShiftSchedule): Result<Unit>

    /**
     * Upserts a shift — inserts or replaces if a record already exists for the same employee+date.
     */
    suspend fun upsert(shift: ShiftSchedule): Result<Unit>

    /** Deletes a shift by [id]. */
    suspend fun deleteById(id: String): Result<Unit>

    /** Deletes the shift for [employeeId] on [date]. */
    suspend fun deleteByEmployeeAndDate(employeeId: String, date: String): Result<Unit>
}

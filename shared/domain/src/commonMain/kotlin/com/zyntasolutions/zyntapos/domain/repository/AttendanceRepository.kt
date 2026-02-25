package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.model.AttendanceSummary
import kotlinx.coroutines.flow.Flow

/**
 * Contract for attendance clock-in/out record management.
 */
interface AttendanceRepository {

    /** Emits all attendance records for [employeeId], most recent first. Re-emits on change. */
    fun getByEmployee(employeeId: String): Flow<List<AttendanceRecord>>

    /**
     * Returns attendance records for [employeeId] within a date prefix range.
     *
     * @param from ISO date prefix (YYYY-MM-DD) — inclusive.
     * @param to ISO date prefix (YYYY-MM-DD) — inclusive.
     */
    suspend fun getByEmployeeForPeriod(
        employeeId: String,
        from: String,
        to: String,
    ): Result<List<AttendanceRecord>>

    /**
     * Returns the open (not yet clocked out) attendance record for [employeeId].
     * Returns null inside [Result.Success] if no open record exists.
     */
    suspend fun getOpenRecord(employeeId: String): Result<AttendanceRecord?>

    /**
     * Returns today's attendance records for all employees in [storeId].
     *
     * @param storeId Store to query.
     * @param todayPrefix ISO date prefix: YYYY-MM-DD.
     */
    suspend fun getTodayForStore(storeId: String, todayPrefix: String): Result<List<AttendanceRecord>>

    /** Inserts a new clock-in record. */
    suspend fun insert(record: AttendanceRecord): Result<Unit>

    /** Updates the clock-out time, total hours, and status for an existing record. */
    suspend fun clockOut(
        id: String,
        clockOut: String,
        totalHours: Double,
        overtimeHours: Double,
        updatedAt: Long,
    ): Result<Unit>

    /** Updates the notes field on an attendance record. */
    suspend fun updateNotes(id: String, notes: String, updatedAt: Long): Result<Unit>

    /**
     * Computes an [AttendanceSummary] for [employeeId] over the given date range.
     *
     * The implementation may compute this in-DB or in-memory depending on data volume.
     */
    suspend fun getSummary(employeeId: String, from: String, to: String): Result<AttendanceSummary>
}

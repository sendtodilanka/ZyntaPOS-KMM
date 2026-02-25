package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for employee leave request management.
 */
interface LeaveRepository {

    /** Emits all leave records for [employeeId], most recent first. Re-emits on change. */
    fun getByEmployee(employeeId: String): Flow<List<LeaveRecord>>

    /** Emits all pending leave requests for [storeId]. Re-emits on change. */
    fun getPendingForStore(storeId: String): Flow<List<LeaveRecord>>

    /** Returns a single leave record by [id]. */
    suspend fun getById(id: String): Result<LeaveRecord>

    /**
     * Returns leave records for [employeeId] that overlap the given date range.
     *
     * @param from ISO date: YYYY-MM-DD.
     * @param to ISO date: YYYY-MM-DD.
     */
    suspend fun getByEmployeeAndPeriod(
        employeeId: String,
        from: String,
        to: String,
    ): Result<List<LeaveRecord>>

    /** Inserts a new leave request. Sets status to PENDING automatically. */
    suspend fun insert(record: LeaveRecord): Result<Unit>

    /**
     * Updates the approval status of a leave record.
     *
     * @param id Leave record ID.
     * @param status New status (APPROVED or REJECTED).
     * @param decidedBy User ID making the decision.
     * @param decidedAt Epoch millis of the decision.
     * @param rejectionReason Required when [status] = REJECTED.
     */
    suspend fun updateStatus(
        id: String,
        status: LeaveStatus,
        decidedBy: String,
        decidedAt: Long,
        rejectionReason: String? = null,
        updatedAt: Long,
    ): Result<Unit>
}

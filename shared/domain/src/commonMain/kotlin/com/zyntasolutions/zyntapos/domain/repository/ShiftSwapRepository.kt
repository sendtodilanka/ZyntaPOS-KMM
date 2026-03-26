package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapRequest
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for shift swap request persistence.
 */
interface ShiftSwapRepository {

    /** Returns the swap request with the given [id], or null if not found. */
    suspend fun getById(id: String): Result<ShiftSwapRequest?>

    /**
     * Emits pending swap requests (PENDING or TARGET_ACCEPTED) involving [employeeId]
     * as either the requesting or target employee. Re-emits on change.
     */
    fun getPendingForEmployee(employeeId: String): Flow<List<ShiftSwapRequest>>

    /**
     * Emits swap requests awaiting manager approval (TARGET_ACCEPTED).
     * Re-emits on change.
     */
    fun getPendingForManager(): Flow<List<ShiftSwapRequest>>

    /** Emits all swap requests created by [employeeId]. Re-emits on change. */
    fun getByRequestingEmployee(employeeId: String): Flow<List<ShiftSwapRequest>>

    /** Inserts a new swap request. */
    suspend fun insert(request: ShiftSwapRequest): Result<Unit>

    /** Updates the status (and optional manager notes) of an existing swap request. */
    suspend fun updateStatus(
        id: String,
        status: ShiftSwapStatus,
        managerNotes: String? = null,
        updatedAt: Long,
    ): Result<Unit>
}

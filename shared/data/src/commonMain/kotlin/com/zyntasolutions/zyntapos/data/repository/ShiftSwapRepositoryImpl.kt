package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Shift_swap_requests
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapRequest
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapStatus
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.ShiftSwapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class ShiftSwapRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : ShiftSwapRepository {

    private val q get() = db.shift_swap_requestsQueries

    override suspend fun getById(id: String): Result<ShiftSwapRequest?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getById(id).executeAsOneOrNull()?.let(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getPendingForEmployee(employeeId: String): Flow<List<ShiftSwapRequest>> =
        q.getPendingForEmployee(employeeId, employeeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getPendingForManager(): Flow<List<ShiftSwapRequest>> =
        q.getPendingForManager()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getByRequestingEmployee(employeeId: String): Flow<List<ShiftSwapRequest>> =
        q.getByRequestingEmployee(employeeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun insert(request: ShiftSwapRequest): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertSwapRequest(
                    id = request.id,
                    requesting_employee_id = request.requestingEmployeeId,
                    target_employee_id = request.targetEmployeeId,
                    requesting_shift_id = request.requestingShiftId,
                    target_shift_id = request.targetShiftId,
                    status = request.status.name,
                    reason = request.reason,
                    manager_notes = request.managerNotes,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.SHIFT_SWAP_REQUEST,
                    request.id,
                    SyncOperation.Operation.INSERT,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun updateStatus(
        id: String,
        status: ShiftSwapStatus,
        managerNotes: String?,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.updateStatus(
                    status = status.name,
                    manager_notes = managerNotes,
                    updated_at = updatedAt,
                    id = id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.SHIFT_SWAP_REQUEST,
                    id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Shift_swap_requests) = ShiftSwapRequest(
        id = row.id,
        requestingEmployeeId = row.requesting_employee_id,
        targetEmployeeId = row.target_employee_id,
        requestingShiftId = row.requesting_shift_id,
        targetShiftId = row.target_shift_id,
        status = ShiftSwapStatus.valueOf(row.status),
        reason = row.reason,
        managerNotes = row.manager_notes,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}

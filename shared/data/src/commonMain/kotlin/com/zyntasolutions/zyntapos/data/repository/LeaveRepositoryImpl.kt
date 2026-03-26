package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Leave_records
import com.zyntasolutions.zyntapos.db.Leave_requests
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.LeaveRequest
import com.zyntasolutions.zyntapos.domain.model.LeaveRequestStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveRequestType
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveType
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class LeaveRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : LeaveRepository {

    private val q get() = db.leave_recordsQueries
    private val rq get() = db.leave_requestsQueries

    override fun getByEmployee(employeeId: String): Flow<List<LeaveRecord>> =
        q.getLeaveByEmployee(employeeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getPendingForStore(storeId: String): Flow<List<LeaveRecord>> =
        q.getPendingLeaveForStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    LeaveRecord(
                        id = row.id,
                        employeeId = row.employee_id,
                        leaveType = runCatching { LeaveType.valueOf(row.leave_type) }.getOrDefault(LeaveType.PERSONAL),
                        startDate = row.start_date,
                        endDate = row.end_date,
                        reason = row.reason,
                        status = runCatching { LeaveStatus.valueOf(row.status) }.getOrDefault(LeaveStatus.PENDING),
                        approvedBy = row.approved_by,
                        approvedAt = row.approved_at,
                        rejectionReason = row.rejection_reason,
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                    )
                }
            }

    override suspend fun getById(id: String): Result<LeaveRecord> = withContext(Dispatchers.IO) {
        runCatching {
            q.getLeaveById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Leave record not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByEmployeeAndPeriod(
        employeeId: String,
        from: String,
        to: String,
    ): Result<List<LeaveRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            // SQL: start_date <= to AND end_date >= from (overlap check)
            q.getLeaveByEmployeeAndPeriod(employeeId, to, from)
                .executeAsList()
                .map(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(record: LeaveRecord): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertLeaveRecord(
                    id = record.id,
                    employee_id = record.employeeId,
                    leave_type = record.leaveType.name,
                    start_date = record.startDate,
                    end_date = record.endDate,
                    reason = record.reason,
                    status = LeaveStatus.PENDING.name,
                    approved_by = null,
                    approved_at = null,
                    rejection_reason = null,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.LEAVE_RECORD,
                    record.id,
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
        status: LeaveStatus,
        decidedBy: String,
        decidedAt: Long,
        rejectionReason: String?,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.updateLeaveStatus(
                    status = status.name,
                    approved_by = decidedBy,
                    approved_at = decidedAt,
                    rejection_reason = rejectionReason,
                    updated_at = updatedAt,
                    id = id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.LEAVE_RECORD,
                    id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update status failed", cause = t)) },
        )
    }

    // ── LeaveRequest workflow ───────────────────────────────────────────────

    override suspend fun getLeaveRequestById(id: String): Result<LeaveRequest?> = withContext(Dispatchers.IO) {
        runCatching {
            rq.getLeaveRequestById(id).executeAsOneOrNull()
        }.fold(
            onSuccess = { row -> Result.Success(row?.let(::toLeaveRequest)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getLeaveRequestsByEmployee(employeeId: String): Flow<List<LeaveRequest>> =
        rq.getLeaveRequestByEmployee(employeeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toLeaveRequest) }

    override fun getPendingLeaveRequests(): Flow<List<LeaveRequest>> =
        rq.getPendingLeaveRequests()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toLeaveRequest) }

    override suspend fun insertLeaveRequest(request: LeaveRequest): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                rq.insertLeaveRequest(
                    id = request.id,
                    employee_id = request.employeeId,
                    leave_type = request.leaveType.name,
                    start_date = request.startDate,
                    end_date = request.endDate,
                    reason = request.reason,
                    status = LeaveRequestStatus.PENDING.name,
                    approver_notes = null,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.LEAVE_REQUEST,
                    request.id,
                    SyncOperation.Operation.INSERT,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun updateLeaveRequestStatus(
        id: String,
        status: LeaveRequestStatus,
        approverNotes: String?,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                rq.updateLeaveRequestStatus(
                    status = status.name,
                    approver_notes = approverNotes,
                    updated_at = updatedAt,
                    id = id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.LEAVE_REQUEST,
                    id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update status failed", cause = t)) },
        )
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Leave_records) = LeaveRecord(
        id = row.id,
        employeeId = row.employee_id,
        leaveType = runCatching { LeaveType.valueOf(row.leave_type) }.getOrDefault(LeaveType.PERSONAL),
        startDate = row.start_date,
        endDate = row.end_date,
        reason = row.reason,
        status = runCatching { LeaveStatus.valueOf(row.status) }.getOrDefault(LeaveStatus.PENDING),
        approvedBy = row.approved_by,
        approvedAt = row.approved_at,
        rejectionReason = row.rejection_reason,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )

    private fun toLeaveRequest(row: Leave_requests) = LeaveRequest(
        id = row.id,
        employeeId = row.employee_id,
        leaveType = runCatching { LeaveRequestType.valueOf(row.leave_type) }.getOrDefault(LeaveRequestType.PERSONAL),
        startDate = row.start_date,
        endDate = row.end_date,
        reason = row.reason,
        status = runCatching { LeaveRequestStatus.valueOf(row.status) }.getOrDefault(LeaveRequestStatus.PENDING),
        approverNotes = row.approver_notes,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}

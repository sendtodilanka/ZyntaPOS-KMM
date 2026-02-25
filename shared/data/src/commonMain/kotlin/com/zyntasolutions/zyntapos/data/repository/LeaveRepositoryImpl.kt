package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Leave_records
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveType
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class LeaveRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : LeaveRepository {

    private val q get() = db.leave_recordsQueries

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
}

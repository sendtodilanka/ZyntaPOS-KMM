package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Attendance_records
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.model.AttendanceStatus
import com.zyntasolutions.zyntapos.domain.model.AttendanceSummary
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class AttendanceRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : AttendanceRepository {

    private val q get() = db.attendance_recordsQueries

    override fun getByEmployee(employeeId: String): Flow<List<AttendanceRecord>> =
        q.getAttendanceByEmployee(employeeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getByEmployeeForPeriod(
        employeeId: String,
        from: String,
        to: String,
    ): Result<List<AttendanceRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            q.getAttendanceByEmployeeForPeriod(employeeId, from, to)
                .executeAsList()
                .map(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getOpenRecord(employeeId: String): Result<AttendanceRecord?> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getOpenAttendanceRecord(employeeId).executeAsOneOrNull()?.let(::toDomain)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun getTodayForStore(
        storeId: String,
        todayPrefix: String,
    ): Result<List<AttendanceRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            // Use date-range query to filter by store via JOIN
            val from = "${todayPrefix}T00:00:00"
            val to = "${todayPrefix}T23:59:59"
            q.getAttendanceByStore(storeId, from, to)
                .executeAsList()
                .map { row ->
                    AttendanceRecord(
                        id = row.id,
                        employeeId = row.employee_id,
                        clockIn = row.clock_in,
                        clockOut = row.clock_out,
                        totalHours = row.total_hours,
                        overtimeHours = row.overtime_hours,
                        notes = row.notes,
                        status = runCatching { AttendanceStatus.valueOf(row.status) }
                            .getOrDefault(AttendanceStatus.PRESENT),
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                    )
                }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(record: AttendanceRecord): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertAttendance(
                    id = record.id,
                    employee_id = record.employeeId,
                    store_id = null,  // C3.4: null = primary store; set when roaming
                    clock_in = record.clockIn,
                    clock_out = record.clockOut,
                    total_hours = record.totalHours,
                    overtime_hours = record.overtimeHours,
                    notes = record.notes,
                    status = record.status.name,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.ATTENDANCE_RECORD,
                    record.id,
                    SyncOperation.Operation.INSERT,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun clockOut(
        id: String,
        clockOut: String,
        totalHours: Double,
        overtimeHours: Double,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.clockOut(
                    clock_out = clockOut,
                    total_hours = totalHours,
                    overtime_hours = overtimeHours,
                    updated_at = updatedAt,
                    id = id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.ATTENDANCE_RECORD,
                    id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Clock-out failed", cause = t)) },
        )
    }

    override suspend fun updateNotes(id: String, notes: String, updatedAt: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                db.transaction {
                    q.updateAttendanceNotes(notes = notes, updated_at = updatedAt, id = id)
                    syncEnqueuer.enqueue(
                        SyncOperation.EntityType.ATTENDANCE_RECORD,
                        id,
                        SyncOperation.Operation.UPDATE,
                    )
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update notes failed", cause = t)) },
            )
        }

    override suspend fun getSummary(
        employeeId: String,
        from: String,
        to: String,
    ): Result<AttendanceSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val records = q.getAttendanceByEmployeeForPeriod(employeeId, from, to)
                .executeAsList()
                .map(::toDomain)

            val totalHours = records.sumOf { it.totalHours ?: 0.0 }
            val overtimeHours = records.sumOf { it.overtimeHours }

            AttendanceSummary(
                employeeId = employeeId,
                totalDays = records.size,
                presentDays = records.count { it.status == AttendanceStatus.PRESENT },
                absentDays = records.count { it.status == AttendanceStatus.ABSENT },
                lateDays = records.count { it.status == AttendanceStatus.LATE },
                leaveDays = records.count { it.status == AttendanceStatus.LEAVE },
                totalHours = totalHours,
                overtimeHours = overtimeHours,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Summary failed", cause = t)) },
        )
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Attendance_records) = AttendanceRecord(
        id = row.id,
        employeeId = row.employee_id,
        clockIn = row.clock_in,
        clockOut = row.clock_out,
        totalHours = row.total_hours,
        overtimeHours = row.overtime_hours,
        notes = row.notes,
        status = runCatching { AttendanceStatus.valueOf(row.status) }.getOrDefault(AttendanceStatus.PRESENT),
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}

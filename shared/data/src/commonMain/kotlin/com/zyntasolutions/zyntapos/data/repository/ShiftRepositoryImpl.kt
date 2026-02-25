package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Shift_schedules
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.ShiftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class ShiftRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : ShiftRepository {

    private val q get() = db.shift_schedulesQueries

    override fun getWeeklySchedule(
        storeId: String,
        weekStart: String,
        weekEnd: String,
    ): Flow<List<ShiftSchedule>> =
        q.getShiftsByStoreAndWeek(storeId, weekStart, weekEnd)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    // JOIN result — extract shift fields (first_name/last_name/position are extra)
                    ShiftSchedule(
                        id = row.id,
                        employeeId = row.employee_id,
                        storeId = row.store_id,
                        shiftDate = row.shift_date,
                        startTime = row.start_time,
                        endTime = row.end_time,
                        notes = row.notes,
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                    )
                }
            }

    override fun getByEmployee(employeeId: String): Flow<List<ShiftSchedule>> =
        // Pass very wide date bounds to retrieve all shifts for the employee
        q.getShiftsByEmployee(employeeId, "0000-01-01", "9999-12-31")
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getByEmployeeAndDate(
        employeeId: String,
        date: String,
    ): Result<ShiftSchedule?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getShiftByEmployeeAndDate(employeeId, date)
                .executeAsOneOrNull()
                ?.let(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByStoreAndDate(
        storeId: String,
        date: String,
    ): Result<List<ShiftSchedule>> = withContext(Dispatchers.IO) {
        runCatching {
            q.getShiftsByStoreAndDate(storeId, date)
                .executeAsList()
                .map { row ->
                    ShiftSchedule(
                        id = row.id,
                        employeeId = row.employee_id,
                        storeId = row.store_id,
                        shiftDate = row.shift_date,
                        startTime = row.start_time,
                        endTime = row.end_time,
                        notes = row.notes,
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                    )
                }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(shift: ShiftSchedule): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertShift(
                    id = shift.id,
                    employee_id = shift.employeeId,
                    store_id = shift.storeId,
                    shift_date = shift.shiftDate,
                    start_time = shift.startTime,
                    end_time = shift.endTime,
                    notes = shift.notes,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.SHIFT_SCHEDULE,
                    shift.id,
                    SyncOperation.Operation.INSERT,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(shift: ShiftSchedule): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateShift(
                    start_time = shift.startTime,
                    end_time = shift.endTime,
                    notes = shift.notes,
                    updated_at = now,
                    id = shift.id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.SHIFT_SCHEDULE,
                    shift.id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun upsert(shift: ShiftSchedule): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.upsertShift(
                    id = shift.id,
                    employee_id = shift.employeeId,
                    store_id = shift.storeId,
                    shift_date = shift.shiftDate,
                    start_time = shift.startTime,
                    end_time = shift.endTime,
                    notes = shift.notes,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.SHIFT_SCHEDULE,
                    shift.id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Upsert failed", cause = t)) },
        )
    }

    override suspend fun deleteById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.deleteShiftById(id)
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.SHIFT_SCHEDULE,
                    id,
                    SyncOperation.Operation.DELETE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    override suspend fun deleteByEmployeeAndDate(
        employeeId: String,
        date: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Look up the ID first so we can enqueue the correct entity ID for sync
            val existing = q.getShiftByEmployeeAndDate(employeeId, date).executeAsOneOrNull()
            db.transaction {
                q.deleteShiftByEmployeeAndDate(employeeId, date)
                existing?.let {
                    syncEnqueuer.enqueue(
                        SyncOperation.EntityType.SHIFT_SCHEDULE,
                        it.id,
                        SyncOperation.Operation.DELETE,
                    )
                }
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Shift_schedules) = ShiftSchedule(
        id = row.id,
        employeeId = row.employee_id,
        storeId = row.store_id,
        shiftDate = row.shift_date,
        startTime = row.start_time,
        endTime = row.end_time,
        notes = row.notes,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}

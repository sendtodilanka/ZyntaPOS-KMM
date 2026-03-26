package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Payroll_entries
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.PayrollEntry
import com.zyntasolutions.zyntapos.domain.model.PayrollEntryStatus
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.PayrollEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class PayrollEntryRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : PayrollEntryRepository {

    private val q get() = db.payrollQueries

    override suspend fun getById(id: String): Result<PayrollEntry> = withContext(Dispatchers.IO) {
        runCatching {
            q.getPayrollById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Payroll entry not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getByEmployee(employeeId: String): Flow<List<PayrollEntry>> =
        q.getPayrollByEmployee(employeeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getByPeriod(
        periodStart: String,
        periodEnd: String,
    ): Result<List<PayrollEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            q.getPayrollByPeriod(periodStart, periodEnd)
                .executeAsList()
                .map(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(entry: PayrollEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertPayroll(
                    id = entry.id,
                    employee_id = entry.employeeId,
                    period_start = entry.periodStart,
                    period_end = entry.periodEnd,
                    base_salary = entry.baseSalary,
                    overtime_hours = entry.overtimeHours,
                    overtime_rate = entry.overtimeRate,
                    overtime_pay = entry.overtimePay,
                    deductions = entry.deductions,
                    deduction_notes = entry.deductionNotes,
                    net_pay = entry.netPay,
                    status = entry.status.name,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.PAYROLL,
                    entry.id,
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
        status: PayrollEntryStatus,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updatePayrollStatus(
                    status = status.name,
                    updated_at = now,
                    id = id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.PAYROLL,
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

    private fun toDomain(row: Payroll_entries) = PayrollEntry(
        id = row.id,
        employeeId = row.employee_id,
        periodStart = row.period_start,
        periodEnd = row.period_end,
        baseSalary = row.base_salary,
        overtimeHours = row.overtime_hours,
        overtimeRate = row.overtime_rate,
        overtimePay = row.overtime_pay,
        deductions = row.deductions,
        deductionNotes = row.deduction_notes,
        netPay = row.net_pay,
        status = runCatching { PayrollEntryStatus.valueOf(row.status) }
            .getOrDefault(PayrollEntryStatus.DRAFT),
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}

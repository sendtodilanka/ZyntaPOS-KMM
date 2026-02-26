package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Payroll_records
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollStatus
import com.zyntasolutions.zyntapos.domain.model.PayrollSummary
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.PayrollRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class PayrollRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : PayrollRepository {

    private val q get() = db.payroll_recordsQueries

    override fun getByEmployee(employeeId: String): Flow<List<PayrollRecord>> =
        q.getPayrollByEmployee(employeeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getByPeriodForStore(storeId: String, period: String): Result<List<PayrollRecord>> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getPayrollByPeriodForStore(period, storeId)
                    .executeAsList()
                    .map { row ->
                        // JOIN result — extract payroll fields directly
                        PayrollRecord(
                            id = row.id,
                            employeeId = row.employee_id,
                            periodStart = row.period_start,
                            periodEnd = row.period_end,
                            baseSalary = row.base_salary,
                            overtimePay = row.overtime_pay,
                            commission = row.commission,
                            deductions = row.deductions,
                            netPay = row.net_pay,
                            status = runCatching { PayrollStatus.valueOf(row.status) }
                                .getOrDefault(PayrollStatus.PENDING),
                            paidAt = row.paid_at,
                            paymentRef = row.payment_ref,
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

    override suspend fun getByEmployeeAndPeriod(
        employeeId: String,
        periodStart: String,
    ): Result<PayrollRecord?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getPayrollByEmployeeAndPeriod(employeeId, periodStart)
                .executeAsOneOrNull()
                ?.let(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getById(id: String): Result<PayrollRecord> = withContext(Dispatchers.IO) {
        runCatching {
            q.getPayrollById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Payroll record not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getPending(storeId: String): Result<List<PayrollRecord>> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getPendingPayroll(storeId)
                    .executeAsList()
                    .map { row ->
                        // JOIN result — extract payroll fields
                        PayrollRecord(
                            id = row.id,
                            employeeId = row.employee_id,
                            periodStart = row.period_start,
                            periodEnd = row.period_end,
                            baseSalary = row.base_salary,
                            overtimePay = row.overtime_pay,
                            commission = row.commission,
                            deductions = row.deductions,
                            netPay = row.net_pay,
                            status = PayrollStatus.PENDING,
                            paidAt = row.paid_at,
                            paymentRef = row.payment_ref,
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

    override suspend fun insert(record: PayrollRecord): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertPayroll(
                    id = record.id,
                    employee_id = record.employeeId,
                    period_start = record.periodStart,
                    period_end = record.periodEnd,
                    base_salary = record.baseSalary,
                    overtime_pay = record.overtimePay,
                    commission = record.commission,
                    deductions = record.deductions,
                    net_pay = record.netPay,
                    status = record.status.name,
                    paid_at = record.paidAt,
                    payment_ref = record.paymentRef,
                    notes = record.notes,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.PAYROLL_RECORD,
                    record.id,
                    SyncOperation.Operation.INSERT,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun markPaid(
        id: String,
        paidAt: Long,
        paymentRef: String?,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.updatePayrollStatus(
                    status = PayrollStatus.PAID.name,
                    paid_at = paidAt,
                    payment_ref = paymentRef,
                    updated_at = updatedAt,
                    id = id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.PAYROLL_RECORD,
                    id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "markPaid failed", cause = t)) },
        )
    }

    override suspend fun updateCalculation(
        id: String,
        baseSalary: Double,
        overtimePay: Double,
        commission: Double,
        deductions: Double,
        netPay: Double,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.updatePayrollCalculation(
                    base_salary = baseSalary,
                    overtime_pay = overtimePay,
                    commission = commission,
                    deductions = deductions,
                    net_pay = netPay,
                    updated_at = updatedAt,
                    id = id,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.PAYROLL_RECORD,
                    id,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "updateCalculation failed", cause = t)) },
        )
    }

    override suspend fun getSummary(storeId: String, period: String): Result<PayrollSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val records = q.getPayrollByPeriodForStore(period, storeId).executeAsList()
                val totalGross = records.sumOf { it.base_salary + it.overtime_pay + it.commission }
                val totalDeductions = records.sumOf { it.deductions }
                val totalNet = records.sumOf { it.net_pay }
                PayrollSummary(
                    period = period,
                    totalEmployees = records.size,
                    totalGrossPay = totalGross,
                    totalDeductions = totalDeductions,
                    totalNetPay = totalNet,
                    pendingCount = records.count { it.status == PayrollStatus.PENDING.name },
                    paidCount = records.count { it.status == PayrollStatus.PAID.name },
                )
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "getSummary failed", cause = t)) },
            )
        }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Payroll_records) = PayrollRecord(
        id = row.id,
        employeeId = row.employee_id,
        periodStart = row.period_start,
        periodEnd = row.period_end,
        baseSalary = row.base_salary,
        overtimePay = row.overtime_pay,
        commission = row.commission,
        deductions = row.deductions,
        netPay = row.net_pay,
        status = runCatching { PayrollStatus.valueOf(row.status) }.getOrDefault(PayrollStatus.PENDING),
        paidAt = row.paid_at,
        paymentRef = row.payment_ref,
        notes = row.notes,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}

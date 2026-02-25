package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollSummary
import kotlinx.coroutines.flow.Flow

/**
 * Contract for payroll record management.
 */
interface PayrollRepository {

    /** Emits all payroll records for [employeeId], most recent first. Re-emits on change. */
    fun getByEmployee(employeeId: String): Flow<List<PayrollRecord>>

    /**
     * Returns payroll records for all employees in [storeId] for the given [period].
     *
     * @param storeId Store scope.
     * @param period Pay period string (e.g., "2026-02").
     */
    suspend fun getByPeriodForStore(storeId: String, period: String): Result<List<PayrollRecord>>

    /**
     * Returns the payroll record for [employeeId] in [periodStart].
     * Returns null inside [Result.Success] if none exists.
     */
    suspend fun getByEmployeeAndPeriod(employeeId: String, periodStart: String): Result<PayrollRecord?>

    /** Returns a single payroll record by [id]. */
    suspend fun getById(id: String): Result<PayrollRecord>

    /** Returns all PENDING payroll records for [storeId]. */
    suspend fun getPending(storeId: String): Result<List<PayrollRecord>>

    /** Inserts a new payroll record. Fails if a record already exists for the same employee+period. */
    suspend fun insert(record: PayrollRecord): Result<Unit>

    /**
     * Marks a payroll record as PAID.
     *
     * @param id Payroll record ID.
     * @param paidAt Epoch millis of the payment.
     * @param paymentRef External payment reference.
     * @param updatedAt Epoch millis of this update.
     */
    suspend fun markPaid(
        id: String,
        paidAt: Long,
        paymentRef: String? = null,
        updatedAt: Long,
    ): Result<Unit>

    /**
     * Updates the financial components of a pending payroll record (recalculation).
     */
    suspend fun updateCalculation(
        id: String,
        baseSalary: Double,
        overtimePay: Double,
        commission: Double,
        deductions: Double,
        netPay: Double,
        updatedAt: Long,
    ): Result<Unit>

    /**
     * Builds a [PayrollSummary] for all payroll records in [storeId] for [period].
     */
    suspend fun getSummary(storeId: String, period: String): Result<PayrollSummary>
}

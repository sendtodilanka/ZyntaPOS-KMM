package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PayrollEntry
import com.zyntasolutions.zyntapos.domain.model.PayrollEntryStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for [PayrollEntry] persistence.
 *
 * Provides CRUD operations for the payroll calculation engine's detailed entries,
 * which track overtime hours/rate breakdown and deduction notes.
 */
interface PayrollEntryRepository {

    /** Returns a single payroll entry by [id]. */
    suspend fun getById(id: String): Result<PayrollEntry>

    /** Emits all payroll entries for [employeeId], most recent period first. Re-emits on change. */
    fun getByEmployee(employeeId: String): Flow<List<PayrollEntry>>

    /** Returns payroll entries for the given period range. */
    suspend fun getByPeriod(periodStart: String, periodEnd: String): Result<List<PayrollEntry>>

    /** Inserts a new payroll entry. */
    suspend fun insert(entry: PayrollEntry): Result<Unit>

    /** Updates the [status] of the payroll entry identified by [id]. */
    suspend fun updateStatus(id: String, status: PayrollEntryStatus): Result<Unit>
}

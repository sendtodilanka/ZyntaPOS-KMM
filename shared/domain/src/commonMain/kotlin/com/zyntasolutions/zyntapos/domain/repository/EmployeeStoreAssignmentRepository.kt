package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for employee multi-store assignments (C3.4).
 *
 * Enables employee roaming across stores. An employee's primary store
 * is tracked via [Employee.storeId]; additional assignments are tracked here.
 */
interface EmployeeStoreAssignmentRepository {

    /** Observe all active assignments for an employee. */
    fun getAssignmentsForEmployee(employeeId: String): Flow<List<EmployeeStoreAssignment>>

    /** Observe all employees assigned to a specific store (including temporary). */
    fun getEmployeesAssignedToStore(storeId: String): Flow<List<EmployeeStoreAssignment>>

    /** Get a specific assignment. */
    suspend fun getById(id: String): EmployeeStoreAssignment?

    /** Get assignment by employee + store pair. */
    suspend fun getByEmployeeAndStore(employeeId: String, storeId: String): EmployeeStoreAssignment?

    /** Create or update an assignment. */
    suspend fun upsert(assignment: EmployeeStoreAssignment)

    /** Deactivate an assignment (soft-delete). */
    suspend fun deactivate(employeeId: String, storeId: String)

    /** Check if an employee is currently assigned to a store. */
    suspend fun isAssigned(employeeId: String, storeId: String): Boolean

    /** Insert/update from sync (no sync enqueue). */
    suspend fun upsertFromSync(assignment: EmployeeStoreAssignment)
}

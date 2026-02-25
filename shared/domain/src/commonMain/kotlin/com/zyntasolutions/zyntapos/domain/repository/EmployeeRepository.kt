package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Employee
import kotlinx.coroutines.flow.Flow

/**
 * Contract for employee profile management.
 */
interface EmployeeRepository {

    /** Emits all active employees for [storeId], ordered by first name. Re-emits on change. */
    fun getActive(storeId: String): Flow<List<Employee>>

    /** Emits all employees for [storeId] including inactive. Re-emits on change. */
    fun getAll(storeId: String): Flow<List<Employee>>

    /** Returns a single employee by [id]. */
    suspend fun getById(id: String): Result<Employee>

    /** Returns the employee linked to the given [userId]. */
    suspend fun getByUserId(userId: String): Result<Employee?>

    /**
     * Searches employees in [storeId] by name, email, or position.
     *
     * @param query Search term matched against first_name, last_name, email, and position.
     */
    suspend fun search(storeId: String, query: String): Result<List<Employee>>

    /** Inserts a new employee and enqueues a sync operation. */
    suspend fun insert(employee: Employee): Result<Unit>

    /** Updates an existing employee record. */
    suspend fun update(employee: Employee): Result<Unit>

    /** Sets the [isActive] flag for the given employee. Does not delete the record. */
    suspend fun setActive(id: String, isActive: Boolean): Result<Unit>

    /** Soft-deletes the employee (sets deleted_at, hides from all normal queries). */
    suspend fun delete(id: String): Result<Unit>
}

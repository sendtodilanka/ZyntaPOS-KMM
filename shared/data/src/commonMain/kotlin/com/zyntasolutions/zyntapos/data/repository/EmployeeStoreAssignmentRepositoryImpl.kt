package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Employee_store_assignments
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.EmployeeStoreAssignmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.Instant

/**
 * SQLDelight-backed implementation of [EmployeeStoreAssignmentRepository] (C3.4).
 */
class EmployeeStoreAssignmentRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : EmployeeStoreAssignmentRepository {

    private val q get() = db.employee_store_assignmentsQueries

    override fun getAssignmentsForEmployee(employeeId: String): Flow<List<EmployeeStoreAssignment>> =
        q.getAssignmentsForEmployee(employeeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun getEmployeesAssignedToStore(storeId: String): Flow<List<EmployeeStoreAssignment>> =
        q.getEmployeesAssignedToStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): EmployeeStoreAssignment? =
        withContext(Dispatchers.IO) {
            q.getAssignmentById(id).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun getByEmployeeAndStore(employeeId: String, storeId: String): EmployeeStoreAssignment? =
        withContext(Dispatchers.IO) {
            q.getAssignmentByEmployeeAndStore(employeeId, storeId).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun upsert(assignment: EmployeeStoreAssignment) =
        withContext(Dispatchers.IO) {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = q.getAssignmentByEmployeeAndStore(assignment.employeeId, assignment.storeId)
                .executeAsOneOrNull()
            db.transaction {
                if (existing != null) {
                    q.updateAssignment(
                        start_date = assignment.startDate,
                        end_date = assignment.endDate,
                        is_temporary = if (assignment.isTemporary) 1L else 0L,
                        is_active = if (assignment.isActive) 1L else 0L,
                        updated_at = now,
                        id = existing.id,
                    )
                    syncEnqueuer.enqueue(
                        SyncOperation.EntityType.EMPLOYEE_STORE_ASSIGNMENT,
                        existing.id,
                        SyncOperation.Operation.UPDATE,
                    )
                } else {
                    q.insertAssignment(
                        id = assignment.id,
                        employee_id = assignment.employeeId,
                        store_id = assignment.storeId,
                        start_date = assignment.startDate,
                        end_date = assignment.endDate,
                        is_temporary = if (assignment.isTemporary) 1L else 0L,
                        is_active = if (assignment.isActive) 1L else 0L,
                        created_at = now,
                        updated_at = now,
                        sync_status = "PENDING",
                    )
                    syncEnqueuer.enqueue(
                        SyncOperation.EntityType.EMPLOYEE_STORE_ASSIGNMENT,
                        assignment.id,
                        SyncOperation.Operation.INSERT,
                    )
                }
            }
        }

    override suspend fun deactivate(employeeId: String, storeId: String) =
        withContext(Dispatchers.IO) {
            val now = Clock.System.now().toEpochMilliseconds()
            q.deactivateAssignment(
                updated_at = now,
                employee_id = employeeId,
                store_id = storeId,
            )
        }

    override suspend fun isAssigned(employeeId: String, storeId: String): Boolean =
        withContext(Dispatchers.IO) {
            val count = q.isEmployeeAssigned(employeeId, storeId).executeAsOne()
            count > 0
        }

    override suspend fun upsertFromSync(assignment: EmployeeStoreAssignment) =
        withContext(Dispatchers.IO) {
            val existing = q.getAssignmentByEmployeeAndStore(assignment.employeeId, assignment.storeId)
                .executeAsOneOrNull()
            if (existing != null) {
                q.updateAssignment(
                    start_date = assignment.startDate,
                    end_date = assignment.endDate,
                    is_temporary = if (assignment.isTemporary) 1L else 0L,
                    is_active = if (assignment.isActive) 1L else 0L,
                    updated_at = assignment.updatedAt.toEpochMilliseconds(),
                    id = existing.id,
                )
            } else {
                q.insertAssignment(
                    id = assignment.id,
                    employee_id = assignment.employeeId,
                    store_id = assignment.storeId,
                    start_date = assignment.startDate,
                    end_date = assignment.endDate,
                    is_temporary = if (assignment.isTemporary) 1L else 0L,
                    is_active = if (assignment.isActive) 1L else 0L,
                    created_at = assignment.createdAt.toEpochMilliseconds(),
                    updated_at = assignment.updatedAt.toEpochMilliseconds(),
                    sync_status = "SYNCED",
                )
            }
        }

    private fun Employee_store_assignments.toDomain(): EmployeeStoreAssignment =
        EmployeeStoreAssignment(
            id = id,
            employeeId = employee_id,
            storeId = store_id,
            startDate = start_date,
            endDate = end_date,
            isTemporary = is_temporary == 1L,
            isActive = is_active == 1L,
            createdAt = Instant.fromEpochMilliseconds(created_at),
            updatedAt = Instant.fromEpochMilliseconds(updated_at),
        )
}

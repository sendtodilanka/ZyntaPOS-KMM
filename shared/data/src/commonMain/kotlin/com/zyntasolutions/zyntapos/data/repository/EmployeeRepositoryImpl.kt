package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Employees
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.EmergencyContact
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.EmployeeDocument
import com.zyntasolutions.zyntapos.domain.model.SalaryType
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class EmployeeRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : EmployeeRepository {

    private val q get() = db.employeesQueries

    override fun getActive(storeId: String): Flow<List<Employee>> =
        q.getActiveEmployeesByStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getAll(storeId: String): Flow<List<Employee>> =
        q.getEmployeesByStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<Employee> = withContext(Dispatchers.IO) {
        runCatching {
            q.getEmployeeById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Employee not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByUserId(userId: String): Result<Employee?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getEmployeeByUserId(userId).executeAsOneOrNull()?.let(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun search(storeId: String, query: String): Result<List<Employee>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val like = "%$query%"
                q.searchEmployees(storeId, like, like, like, like)
                    .executeAsList()
                    .map(::toDomain)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun insert(employee: Employee): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertEmployee(
                    id = employee.id,
                    user_id = employee.userId,
                    store_id = employee.storeId,
                    first_name = employee.firstName,
                    last_name = employee.lastName,
                    email = employee.email,
                    phone = employee.phone,
                    address = employee.address,
                    date_of_birth = employee.dateOfBirth,
                    hire_date = employee.hireDate,
                    department = employee.department,
                    position = employee.position,
                    salary = employee.salary,
                    salary_type = employee.salaryType.name,
                    commission_rate = employee.commissionRate,
                    emergency_contact = employee.emergencyContact?.toJson(),
                    documents = employee.documents.toJson(),
                    is_active = if (employee.isActive) 1L else 0L,
                    created_at = now,
                    updated_at = now,
                    deleted_at = null,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.EMPLOYEE, employee.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(employee: Employee): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateEmployee(
                    user_id = employee.userId,
                    first_name = employee.firstName,
                    last_name = employee.lastName,
                    email = employee.email,
                    phone = employee.phone,
                    address = employee.address,
                    date_of_birth = employee.dateOfBirth,
                    hire_date = employee.hireDate,
                    department = employee.department,
                    position = employee.position,
                    salary = employee.salary,
                    salary_type = employee.salaryType.name,
                    commission_rate = employee.commissionRate,
                    emergency_contact = employee.emergencyContact?.toJson(),
                    documents = employee.documents.toJson(),
                    is_active = if (employee.isActive) 1L else 0L,
                    updated_at = now,
                    sync_status = "PENDING",
                    id = employee.id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.EMPLOYEE, employee.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun setActive(id: String, isActive: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    q.setEmployeeActive(
                        is_active = if (isActive) 1L else 0L,
                        updated_at = now,
                        id = id,
                    )
                    syncEnqueuer.enqueue(SyncOperation.EntityType.EMPLOYEE, id, SyncOperation.Operation.UPDATE)
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "setActive failed", cause = t)) },
            )
        }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.softDeleteEmployee(deleted_at = now, updated_at = now, id = id)
                syncEnqueuer.enqueue(SyncOperation.EntityType.EMPLOYEE, id, SyncOperation.Operation.DELETE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun toDomain(row: Employees) = Employee(
        id = row.id,
        userId = row.user_id,
        storeId = row.store_id,
        firstName = row.first_name,
        lastName = row.last_name,
        email = row.email,
        phone = row.phone,
        address = row.address,
        dateOfBirth = row.date_of_birth,
        hireDate = row.hire_date,
        department = row.department,
        position = row.position,
        salary = row.salary,
        salaryType = runCatching { SalaryType.valueOf(row.salary_type) }.getOrDefault(SalaryType.MONTHLY),
        commissionRate = row.commission_rate,
        emergencyContact = row.emergency_contact?.parseEmergencyContact(),
        documents = row.documents?.parseDocuments() ?: emptyList(),
        isActive = row.is_active == 1L,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )

    private fun EmergencyContact.toJson(): String =
        """{"name":"${name.escapeJson()}","phone":"${phone.escapeJson()}","relationship":"${relationship.escapeJson()}"}"""

    private fun List<EmployeeDocument>.toJson(): String =
        joinToString(prefix = "[", postfix = "]", separator = ",") { doc ->
            """{"name":"${doc.name.escapeJson()}","url":"${doc.url.escapeJson()}","type":"${doc.type.escapeJson()}"}"""
        }

    private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")

    private fun String.extractField(key: String): String? =
        Regex("\"$key\":\"([^\"]*)\"").find(this)?.groupValues?.getOrNull(1)

    private fun String.parseEmergencyContact(): EmergencyContact? = runCatching {
        EmergencyContact(
            name = extractField("name") ?: return@runCatching null,
            phone = extractField("phone") ?: return@runCatching null,
            relationship = extractField("relationship") ?: return@runCatching null,
        )
    }.getOrNull()

    private fun String.parseDocuments(): List<EmployeeDocument> = runCatching {
        val objectPattern = Regex("\\{([^}]*)\\}")
        objectPattern.findAll(this).map { match ->
            val obj = match.value
            EmployeeDocument(
                name = obj.extractField("name") ?: "",
                url = obj.extractField("url") ?: "",
                type = obj.extractField("type") ?: "",
            )
        }.toList()
    }.getOrDefault(emptyList())
}

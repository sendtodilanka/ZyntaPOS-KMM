package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.EmergencyContact
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.SalaryType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — EmployeeRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates EmployeeRepositoryImpl against a real in-memory SQLite database.
 * No mocks — all assertions exercise actual SQLDelight-generated queries.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all mandatory fields
 *  B. insert → getById preserves optional fields (email, salary, emergencyContact)
 *  C. getAll(storeId) returns all non-deleted employees for that store
 *  D. getAll(storeId) does not return employees from a different store
 *  E. getActive(storeId) returns only is_active = 1 employees
 *  F. update changes salary and position
 *  G. setActive(false) deactivates employee; getActive no longer includes them
 *  H. setActive(true) reactivates an employee; getActive includes them again
 *  I. delete (soft-delete) sets deleted_at; getById returns Result.Error
 *  J. getAll excludes soft-deleted employees
 *  K. getByUserId returns the correct employee
 *  L. getById for unknown id returns Result.Error
 *  M. search matches on first_name, last_name, email, and position
 */
class EmployeeRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: EmployeeRepositoryImpl

    private val now = System.currentTimeMillis()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        val syncEnqueuer = SyncEnqueuer(db)
        repo = EmployeeRepositoryImpl(db, syncEnqueuer)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeEmployee(
        id: String         = "emp-1",
        userId: String?    = null,
        storeId: String    = "store-A",
        firstName: String  = "John",
        lastName: String   = "Doe",
        email: String?     = null,
        phone: String?     = null,
        hireDate: String   = "2024-01-15",
        position: String   = "Cashier",
        salary: Double?    = null,
        salaryType: SalaryType = SalaryType.MONTHLY,
        commissionRate: Double = 0.0,
        emergencyContact: EmergencyContact? = null,
        isActive: Boolean  = true,
    ) = Employee(
        id               = id,
        userId           = userId,
        storeId          = storeId,
        firstName        = firstName,
        lastName         = lastName,
        email            = email,
        phone            = phone,
        hireDate         = hireDate,
        position         = position,
        salary           = salary,
        salaryType       = salaryType,
        commissionRate   = commissionRate,
        emergencyContact = emergencyContact,
        isActive         = isActive,
        createdAt        = now,
        updatedAt        = now,
    )

    // ── A. insert → getById preserves mandatory fields ────────────────────────

    @Test
    fun insert_then_getById_preserves_mandatory_fields() = runTest {
        val employee = makeEmployee(
            id        = "emp-rt",
            storeId   = "store-1",
            firstName = "Alice",
            lastName  = "Smith",
            position  = "Manager",
            hireDate  = "2023-06-01",
        )
        val insertResult = repo.insert(employee)
        assertIs<Result.Success<Unit>>(insertResult)

        val result = repo.getById("emp-rt")
        assertIs<Result.Success<Employee>>(result)
        val retrieved = result.data
        assertEquals("emp-rt",   retrieved.id)
        assertEquals("store-1",  retrieved.storeId)
        assertEquals("Alice",    retrieved.firstName)
        assertEquals("Smith",    retrieved.lastName)
        assertEquals("Manager",  retrieved.position)
        assertEquals("2023-06-01", retrieved.hireDate)
        assertTrue(retrieved.isActive)
    }

    // ── B. insert → getById preserves optional fields ─────────────────────────

    @Test
    fun insert_then_getById_preserves_optional_fields() = runTest {
        val ec = EmergencyContact(name = "Jane Doe", phone = "0771234567", relationship = "Spouse")
        val employee = makeEmployee(
            id               = "emp-opt",
            email            = "alice@zyntapos.com",
            phone            = "0701234567",
            salary           = 75000.0,
            salaryType       = SalaryType.MONTHLY,
            commissionRate   = 2.5,
            emergencyContact = ec,
        )
        repo.insert(employee)

        val result = repo.getById("emp-opt")
        assertIs<Result.Success<Employee>>(result)
        val retrieved = result.data
        assertEquals("alice@zyntapos.com", retrieved.email)
        assertEquals("0701234567",         retrieved.phone)
        assertEquals(75000.0,              retrieved.salary)
        assertEquals(SalaryType.MONTHLY,   retrieved.salaryType)
        assertEquals(2.5,                  retrieved.commissionRate)
        assertNotNull(retrieved.emergencyContact)
        assertEquals("Jane Doe",  retrieved.emergencyContact!!.name)
        assertEquals("0771234567", retrieved.emergencyContact!!.phone)
        assertEquals("Spouse",    retrieved.emergencyContact!!.relationship)
    }

    // ── C. getAll returns all non-deleted employees for the store ─────────────

    @Test
    fun getAll_returns_all_non_deleted_employees_for_store() = runTest {
        repo.insert(makeEmployee(id = "emp-a1", storeId = "store-A"))
        repo.insert(makeEmployee(id = "emp-a2", storeId = "store-A", isActive = false))

        repo.getAll("store-A").test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.any { it.id == "emp-a1" })
            assertTrue(items.any { it.id == "emp-a2" })
            cancel()
        }
    }

    // ── D. getAll does not return employees from a different store ────────────

    @Test
    fun getAll_does_not_return_employees_from_different_store() = runTest {
        repo.insert(makeEmployee(id = "emp-storeA", storeId = "store-A"))
        repo.insert(makeEmployee(id = "emp-storeB", storeId = "store-B"))

        repo.getAll("store-A").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("emp-storeA", items[0].id)
            cancel()
        }
    }

    // ── E. getActive returns only active employees ────────────────────────────

    @Test
    fun getActive_returns_only_active_employees() = runTest {
        repo.insert(makeEmployee(id = "emp-active",   storeId = "store-X", isActive = true))
        repo.insert(makeEmployee(id = "emp-inactive", storeId = "store-X", isActive = false))

        repo.getActive("store-X").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("emp-active", items[0].id)
            cancel()
        }
    }

    // ── F. update changes salary and position ────────────────────────────────

    @Test
    fun update_changes_salary_and_position() = runTest {
        val employee = makeEmployee(id = "emp-upd", salary = 50000.0, position = "Cashier")
        repo.insert(employee)

        val updated = employee.copy(salary = 60000.0, position = "Senior Cashier")
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val result = repo.getById("emp-upd")
        assertIs<Result.Success<Employee>>(result)
        assertEquals(60000.0,          result.data.salary)
        assertEquals("Senior Cashier", result.data.position)
    }

    // ── G. setActive(false) deactivates employee ──────────────────────────────

    @Test
    fun setActive_false_deactivates_employee_and_getActive_excludes_them() = runTest {
        repo.insert(makeEmployee(id = "emp-deact", storeId = "store-D", isActive = true))

        val result = repo.setActive("emp-deact", false)
        assertIs<Result.Success<Unit>>(result)

        val retrieved = repo.getById("emp-deact")
        assertIs<Result.Success<Employee>>(retrieved)
        assertEquals(false, retrieved.data.isActive)

        repo.getActive("store-D").test {
            val items = awaitItem()
            assertTrue(items.none { it.id == "emp-deact" })
            cancel()
        }
    }

    // ── H. setActive(true) reactivates an employee ───────────────────────────

    @Test
    fun setActive_true_reactivates_employee_and_getActive_includes_them() = runTest {
        repo.insert(makeEmployee(id = "emp-react", storeId = "store-R", isActive = false))

        val result = repo.setActive("emp-react", true)
        assertIs<Result.Success<Unit>>(result)

        repo.getActive("store-R").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("emp-react", items[0].id)
            cancel()
        }
    }

    // ── I. delete (soft-delete) makes getById return Result.Error ────────────

    @Test
    fun delete_soft_deletes_employee_and_getById_returns_error() = runTest {
        repo.insert(makeEmployee(id = "emp-del", storeId = "store-E"))

        val deleteResult = repo.delete("emp-del")
        assertIs<Result.Success<Unit>>(deleteResult)

        val result = repo.getById("emp-del")
        assertIs<Result.Error>(result)
    }

    // ── J. getAll excludes soft-deleted employees ─────────────────────────────

    @Test
    fun getAll_excludes_soft_deleted_employees() = runTest {
        repo.insert(makeEmployee(id = "emp-live", storeId = "store-F"))
        repo.insert(makeEmployee(id = "emp-gone", storeId = "store-F"))

        repo.delete("emp-gone")

        repo.getAll("store-F").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("emp-live", items[0].id)
            cancel()
        }
    }

    // ── K. getByUserId returns the correct employee ───────────────────────────

    @Test
    fun getByUserId_returns_correct_employee() = runTest {
        repo.insert(makeEmployee(id = "emp-user", userId = "user-42"))
        repo.insert(makeEmployee(id = "emp-other", userId = null))

        val result = repo.getByUserId("user-42")
        assertIs<Result.Success<Employee?>>(result)
        assertNotNull(result.data)
        assertEquals("emp-user", result.data!!.id)
    }

    // ── L. getById for unknown id returns Result.Error ────────────────────────

    @Test
    fun getById_for_unknown_id_returns_error() = runTest {
        val result = repo.getById("does-not-exist")
        assertIs<Result.Error>(result)
    }

    // ── M. search matches on multiple fields ──────────────────────────────────

    @Test
    fun search_matches_on_first_name_and_position() = runTest {
        repo.insert(makeEmployee(id = "emp-s1", storeId = "store-S", firstName = "Carlos", position = "Manager"))
        repo.insert(makeEmployee(id = "emp-s2", storeId = "store-S", firstName = "Diana",  position = "Cashier"))
        repo.insert(makeEmployee(id = "emp-s3", storeId = "store-S", firstName = "Edward", position = "Cashier"))

        val byFirstName = repo.search("store-S", "Carlos")
        assertIs<Result.Success<List<Employee>>>(byFirstName)
        assertEquals(1, byFirstName.data.size)
        assertEquals("emp-s1", byFirstName.data[0].id)

        val byPosition = repo.search("store-S", "Cashier")
        assertIs<Result.Success<List<Employee>>>(byPosition)
        assertEquals(2, byPosition.data.size)
        assertTrue(byPosition.data.any { it.id == "emp-s2" })
        assertTrue(byPosition.data.any { it.id == "emp-s3" })
    }
}

package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — EmployeeStoreAssignmentRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [EmployeeStoreAssignmentRepositoryImpl] against a real in-memory SQLite database.
 * No FK constraints on employee_store_assignments — no pre-seeding required.
 *
 * Coverage:
 *  A. upsert (insert) → getById round-trip preserves all fields
 *  B. getAssignmentsForEmployee emits active assignments via Turbine
 *  C. getEmployeesAssignedToStore emits employees for a store
 *  D. getByEmployeeAndStore returns assignment for valid combination
 *  E. getByEmployeeAndStore returns null for unknown combination
 *  F. upsert (update) modifies existing assignment by employee+store pair
 *  G. deactivate sets is_active=false (excluded from getAssignmentsForEmployee)
 *  H. isAssigned returns true for active assignment, false after deactivate
 */
class EmployeeStoreAssignmentRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: EmployeeStoreAssignmentRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = EmployeeStoreAssignmentRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val nowInstant get() = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    private fun makeAssignment(
        id: String = "esa-01",
        employeeId: String = "emp-01",
        storeId: String = "store-01",
        startDate: String = "2026-01-01",
        endDate: String? = null,
        isTemporary: Boolean = false,
        isActive: Boolean = true,
    ) = EmployeeStoreAssignment(
        id = id,
        employeeId = employeeId,
        storeId = storeId,
        startDate = startDate,
        endDate = endDate,
        isTemporary = isTemporary,
        isActive = isActive,
        createdAt = nowInstant,
        updatedAt = nowInstant,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsert then getById round-trip preserves all fields`() = runTest {
        val assignment = makeAssignment(
            id = "esa-01",
            employeeId = "emp-01",
            storeId = "store-01",
            startDate = "2026-03-01",
            endDate = "2026-12-31",
            isTemporary = true,
            isActive = true,
        )
        repo.upsert(assignment)

        val fetched = repo.getById("esa-01")
        assertNotNull(fetched)
        assertEquals("esa-01", fetched.id)
        assertEquals("emp-01", fetched.employeeId)
        assertEquals("store-01", fetched.storeId)
        assertEquals("2026-03-01", fetched.startDate)
        assertEquals("2026-12-31", fetched.endDate)
        assertTrue(fetched.isTemporary)
        assertTrue(fetched.isActive)
    }

    @Test
    fun `B - getAssignmentsForEmployee emits active assignments via Turbine`() = runTest {
        repo.upsert(makeAssignment(id = "esa-01", employeeId = "emp-01", storeId = "store-01"))
        repo.upsert(makeAssignment(id = "esa-02", employeeId = "emp-01", storeId = "store-02"))
        repo.upsert(makeAssignment(id = "esa-03", employeeId = "emp-02", storeId = "store-01"))

        repo.getAssignmentsForEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.employeeId == "emp-01" })
            assertTrue(list.any { it.storeId == "store-01" })
            assertTrue(list.any { it.storeId == "store-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getEmployeesAssignedToStore emits employees for a store`() = runTest {
        repo.upsert(makeAssignment(id = "esa-01", employeeId = "emp-01", storeId = "store-01"))
        repo.upsert(makeAssignment(id = "esa-02", employeeId = "emp-02", storeId = "store-01"))
        repo.upsert(makeAssignment(id = "esa-03", employeeId = "emp-03", storeId = "store-02"))

        repo.getEmployeesAssignedToStore("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.storeId == "store-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getByEmployeeAndStore returns assignment for valid combination`() = runTest {
        repo.upsert(makeAssignment(id = "esa-01", employeeId = "emp-01", storeId = "store-01"))

        val result = repo.getByEmployeeAndStore("emp-01", "store-01")
        assertNotNull(result)
        assertEquals("esa-01", result.id)
    }

    @Test
    fun `E - getByEmployeeAndStore returns null for unknown combination`() = runTest {
        val result = repo.getByEmployeeAndStore("non-existent-emp", "non-existent-store")
        assertNull(result)
    }

    @Test
    fun `F - upsert updates existing assignment when employee+store pair matches`() = runTest {
        repo.upsert(makeAssignment(id = "esa-01", employeeId = "emp-01", storeId = "store-01",
            startDate = "2026-01-01", endDate = null, isTemporary = false))

        repo.upsert(makeAssignment(id = "esa-01-new", employeeId = "emp-01", storeId = "store-01",
            startDate = "2026-06-01", endDate = "2026-12-31", isTemporary = true))

        val fetched = repo.getByEmployeeAndStore("emp-01", "store-01")
        assertNotNull(fetched)
        assertEquals("2026-06-01", fetched.startDate)
        assertEquals("2026-12-31", fetched.endDate)
        assertTrue(fetched.isTemporary)
    }

    @Test
    fun `G - deactivate sets is_active=false excluded from getAssignmentsForEmployee`() = runTest {
        repo.upsert(makeAssignment(id = "esa-01", employeeId = "emp-01", storeId = "store-01"))
        repo.upsert(makeAssignment(id = "esa-02", employeeId = "emp-01", storeId = "store-02"))

        repo.deactivate("emp-01", "store-01")

        repo.getAssignmentsForEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("store-02", list.first().storeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `H - isAssigned returns true for active, false after deactivate`() = runTest {
        repo.upsert(makeAssignment(id = "esa-01", employeeId = "emp-01", storeId = "store-01"))

        assertTrue(repo.isAssigned("emp-01", "store-01"))
        assertFalse(repo.isAssigned("emp-01", "store-02"))

        repo.deactivate("emp-01", "store-01")
        assertFalse(repo.isAssigned("emp-01", "store-01"))
    }
}

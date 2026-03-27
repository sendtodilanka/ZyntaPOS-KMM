package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — ShiftRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [ShiftRepositoryImpl] against a real in-memory SQLite database.
 * Requires employees seeded to satisfy the employee_id FK constraint.
 *
 * Coverage:
 *  A. upsert (insert) → getById round-trip preserves all fields
 *  B. getByEmployee emits shifts for a specific employee via Turbine
 *  C. getByEmployeeAndDate returns the shift for a date
 *  D. getByEmployeeAndDate returns null for unknown date
 *  E. getByStoreAndDate returns all shifts for a store on a date
 *  F. upsert (update) modifies existing shift by employee+store+date
 *  G. deleteById removes the shift
 *  H. deleteByEmployeeAndDate removes the shift for that date
 */
class ShiftRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ShiftRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = ShiftRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed employees required by employee_id FK
        db.employeesQueries.insertEmployee(
            id = "emp-01", user_id = null, store_id = "store-01",
            first_name = "Alice", last_name = "Smith",
            email = null, phone = null, address = null,
            date_of_birth = null, hire_date = "2024-01-01",
            department = null, position = "Cashier",
            salary = 50000.0, salary_type = "MONTHLY",
            commission_rate = null, emergency_contact = null, documents = null,
            is_active = 1L, created_at = now, updated_at = now, deleted_at = null,
            sync_status = "PENDING",
        )
        db.employeesQueries.insertEmployee(
            id = "emp-02", user_id = null, store_id = "store-01",
            first_name = "Bob", last_name = "Jones",
            email = null, phone = null, address = null,
            date_of_birth = null, hire_date = "2024-01-01",
            department = null, position = "Supervisor",
            salary = 70000.0, salary_type = "MONTHLY",
            commission_rate = null, emergency_contact = null, documents = null,
            is_active = 1L, created_at = now, updated_at = now, deleted_at = null,
            sync_status = "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeShift(
        id: String = "shift-01",
        employeeId: String = "emp-01",
        storeId: String = "store-01",
        shiftDate: String = "2026-04-01",
        startTime: String = "08:00",
        endTime: String = "16:00",
        notes: String? = null,
    ) = ShiftSchedule(
        id = id,
        employeeId = employeeId,
        storeId = storeId,
        shiftDate = shiftDate,
        startTime = startTime,
        endTime = endTime,
        notes = notes,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsert then getById round-trip preserves all fields`() = runTest {
        val shift = makeShift(
            id = "shift-01",
            employeeId = "emp-01",
            storeId = "store-01",
            shiftDate = "2026-04-01",
            startTime = "09:00",
            endTime = "17:00",
            notes = "Morning shift",
        )
        val upsertResult = repo.upsert(shift)
        assertIs<Result.Success<Unit>>(upsertResult)

        val fetchResult = repo.getById("shift-01")
        assertIs<Result.Success<ShiftSchedule?>>(fetchResult)
        val fetched = fetchResult.data
        assertNotNull(fetched)
        assertEquals("shift-01", fetched.id)
        assertEquals("emp-01", fetched.employeeId)
        assertEquals("store-01", fetched.storeId)
        assertEquals("2026-04-01", fetched.shiftDate)
        assertEquals("09:00", fetched.startTime)
        assertEquals("17:00", fetched.endTime)
        assertEquals("Morning shift", fetched.notes)
    }

    @Test
    fun `B - getByEmployee emits shifts for a specific employee via Turbine`() = runTest {
        repo.upsert(makeShift(id = "shift-01", employeeId = "emp-01", shiftDate = "2026-04-01"))
        repo.upsert(makeShift(id = "shift-02", employeeId = "emp-01", shiftDate = "2026-04-02"))
        repo.upsert(makeShift(id = "shift-03", employeeId = "emp-02", shiftDate = "2026-04-01"))

        repo.getByEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.employeeId == "emp-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByEmployeeAndDate returns shift for a specific date`() = runTest {
        repo.upsert(makeShift(id = "shift-01", employeeId = "emp-01", shiftDate = "2026-04-01"))
        repo.upsert(makeShift(id = "shift-02", employeeId = "emp-01", shiftDate = "2026-04-02"))

        val result = repo.getByEmployeeAndDate("emp-01", "2026-04-01")
        assertIs<Result.Success<ShiftSchedule?>>(result)
        assertNotNull(result.data)
        assertEquals("shift-01", result.data!!.id)
    }

    @Test
    fun `D - getByEmployeeAndDate returns null for unknown date`() = runTest {
        val result = repo.getByEmployeeAndDate("emp-01", "2099-01-01")
        assertIs<Result.Success<ShiftSchedule?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `E - getByStoreAndDate returns all shifts for a store on a date`() = runTest {
        repo.upsert(makeShift(id = "shift-01", employeeId = "emp-01", storeId = "store-01", shiftDate = "2026-04-01"))
        repo.upsert(makeShift(id = "shift-02", employeeId = "emp-02", storeId = "store-01", shiftDate = "2026-04-01"))
        repo.upsert(makeShift(id = "shift-03", employeeId = "emp-01", storeId = "store-01", shiftDate = "2026-04-02"))

        val result = repo.getByStoreAndDate("store-01", "2026-04-01")
        assertIs<Result.Success<List<ShiftSchedule>>>(result)
        assertEquals(2, result.data.size)
        assertTrue(result.data.all { it.storeId == "store-01" && it.shiftDate == "2026-04-01" })
    }

    @Test
    fun `F - upsert updates existing shift by employee+store+date combination`() = runTest {
        repo.upsert(makeShift(id = "shift-01", employeeId = "emp-01", shiftDate = "2026-04-01",
            startTime = "08:00", endTime = "16:00"))

        val updated = makeShift(id = "shift-01-new", employeeId = "emp-01", shiftDate = "2026-04-01",
            startTime = "10:00", endTime = "18:00", notes = "Afternoon")
        repo.upsert(updated)

        val result = repo.getByEmployeeAndDate("emp-01", "2026-04-01")
        assertIs<Result.Success<ShiftSchedule?>>(result)
        assertNotNull(result.data)
        assertEquals("10:00", result.data!!.startTime)
        assertEquals("18:00", result.data!!.endTime)
        assertEquals("Afternoon", result.data!!.notes)
    }

    @Test
    fun `G - deleteById removes the shift`() = runTest {
        repo.upsert(makeShift(id = "shift-01", employeeId = "emp-01", shiftDate = "2026-04-01"))
        repo.upsert(makeShift(id = "shift-02", employeeId = "emp-01", shiftDate = "2026-04-02"))

        val deleteResult = repo.deleteById("shift-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        val result = repo.getById("shift-01")
        assertIs<Result.Success<ShiftSchedule?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `H - deleteByEmployeeAndDate removes the shift for that date`() = runTest {
        repo.upsert(makeShift(id = "shift-01", employeeId = "emp-01", shiftDate = "2026-04-01"))
        repo.upsert(makeShift(id = "shift-02", employeeId = "emp-01", shiftDate = "2026-04-02"))

        val deleteResult = repo.deleteByEmployeeAndDate("emp-01", "2026-04-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        val result = repo.getByEmployeeAndDate("emp-01", "2026-04-01")
        assertIs<Result.Success<ShiftSchedule?>>(result)
        assertNull(result.data)

        // Other date unaffected
        val other = repo.getByEmployeeAndDate("emp-01", "2026-04-02")
        assertIs<Result.Success<ShiftSchedule?>>(other)
        assertNotNull(other.data)
    }
}

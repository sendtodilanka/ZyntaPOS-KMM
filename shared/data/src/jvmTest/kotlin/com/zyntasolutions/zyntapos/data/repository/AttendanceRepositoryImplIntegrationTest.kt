package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.model.AttendanceStatus
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
 * ZyntaPOS — AttendanceRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [AttendanceRepositoryImpl] against a real in-memory SQLite database.
 * Requires employees seeded to satisfy the employee_id FK constraint.
 *
 * Coverage:
 *  A. insert → getByEmployee emits records via Turbine
 *  B. getOpenRecord returns record where clockOut is null
 *  C. clockOut updates the record with clock-out time and total hours
 *  D. getByEmployeeForPeriod filters by date range
 *  E. getSummary aggregates present/absent/late/leave correctly
 *  F. updateNotes changes notes on a record
 *  G. getOpenRecord returns null when all records are clocked out
 */
class AttendanceRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: AttendanceRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = AttendanceRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed employee required by employee_id FK
        db.employeesQueries.insertEmployee(
            id = "emp-01", user_id = null, store_id = "store-01",
            first_name = "Alice", last_name = "Smith",
            email = null, phone = null, address = null,
            date_of_birth = null, hire_date = "2024-01-01",
            department = null, position = "Cashier",
            salary = 50000.0, salary_type = "MONTHLY",
            commission_rate = 0.0, emergency_contact = null, documents = null,
            is_active = 1L, created_at = now, updated_at = now, deleted_at = null,
            sync_status = "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeRecord(
        id: String = "att-01",
        employeeId: String = "emp-01",
        storeId: String? = "store-01",
        clockIn: String = "2026-04-01T08:00:00",
        clockOut: String? = null,
        totalHours: Double? = null,
        overtimeHours: Double = 0.0,
        status: AttendanceStatus = AttendanceStatus.PRESENT,
        notes: String? = null,
    ) = AttendanceRecord(
        id = id,
        employeeId = employeeId,
        storeId = storeId,
        clockIn = clockIn,
        clockOut = clockOut,
        totalHours = totalHours,
        overtimeHours = overtimeHours,
        status = status,
        notes = notes,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getByEmployee emits records via Turbine`() = runTest {
        repo.insert(makeRecord(id = "att-01", clockIn = "2026-04-01T08:00:00"))
        repo.insert(makeRecord(id = "att-02", clockIn = "2026-04-02T08:00:00"))

        repo.getByEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.employeeId == "emp-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - getOpenRecord returns record where clockOut is null`() = runTest {
        repo.insert(makeRecord(id = "att-01", clockIn = "2026-04-01T08:00:00", clockOut = null))

        val result = repo.getOpenRecord("emp-01")
        assertIs<Result.Success<AttendanceRecord?>>(result)
        assertNotNull(result.data)
        assertEquals("att-01", result.data!!.id)
        assertNull(result.data!!.clockOut)
    }

    @Test
    fun `C - clockOut updates record with clock-out time and total hours`() = runTest {
        repo.insert(makeRecord(id = "att-01", clockIn = "2026-04-01T08:00:00", clockOut = null))

        val clockOutResult = repo.clockOut(
            id = "att-01",
            clockOut = "2026-04-01T16:00:00",
            totalHours = 8.0,
            overtimeHours = 0.0,
            updatedAt = now,
        )
        assertIs<Result.Success<Unit>>(clockOutResult)

        repo.getByEmployee("emp-01").test {
            val list = awaitItem()
            val updated = list.find { it.id == "att-01" }
            assertNotNull(updated)
            assertEquals("2026-04-01T16:00:00", updated!!.clockOut)
            assertEquals(8.0, updated.totalHours)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getByEmployeeForPeriod filters by date range`() = runTest {
        repo.insert(makeRecord(id = "att-march", clockIn = "2026-03-15T08:00:00"))
        repo.insert(makeRecord(id = "att-april1", clockIn = "2026-04-01T08:00:00"))
        repo.insert(makeRecord(id = "att-april15", clockIn = "2026-04-15T08:00:00"))
        repo.insert(makeRecord(id = "att-may", clockIn = "2026-05-01T08:00:00"))

        val result = repo.getByEmployeeForPeriod(
            employeeId = "emp-01",
            from = "2026-04-01T00:00:00",
            to = "2026-04-30T23:59:59",
        )
        assertIs<Result.Success<List<AttendanceRecord>>>(result)
        assertEquals(2, result.data.size)
        assertTrue(result.data.all { it.clockIn.startsWith("2026-04") })
    }

    @Test
    fun `E - getSummary aggregates present, absent, late, and leave correctly`() = runTest {
        repo.insert(makeRecord(id = "att-01", clockIn = "2026-04-01T08:00:00", status = AttendanceStatus.PRESENT, totalHours = 8.0))
        repo.insert(makeRecord(id = "att-02", clockIn = "2026-04-02T08:00:00", status = AttendanceStatus.ABSENT))
        repo.insert(makeRecord(id = "att-03", clockIn = "2026-04-03T08:00:00", status = AttendanceStatus.LATE, totalHours = 7.5))
        repo.insert(makeRecord(id = "att-04", clockIn = "2026-04-04T08:00:00", status = AttendanceStatus.LEAVE))

        val result = repo.getSummary("emp-01", "2026-04-01T00:00:00", "2026-04-30T23:59:59")
        assertIs<Result.Success<*>>(result)
        val summary = result.data
        assertNotNull(summary)
        assertEquals(4, (summary as com.zyntasolutions.zyntapos.domain.model.AttendanceSummary).totalDays)
        assertEquals(1, summary.presentDays)
        assertEquals(1, summary.absentDays)
        assertEquals(1, summary.lateDays)
        assertEquals(1, summary.leaveDays)
        assertEquals(15.5, summary.totalHours)
    }

    @Test
    fun `F - updateNotes changes notes on a record`() = runTest {
        repo.insert(makeRecord(id = "att-01", notes = null))

        val updateResult = repo.updateNotes("att-01", "Overtime approved", now)
        assertIs<Result.Success<Unit>>(updateResult)

        repo.getByEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals("Overtime approved", list.find { it.id == "att-01" }?.notes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - getOpenRecord returns null when all records are clocked out`() = runTest {
        repo.insert(makeRecord(id = "att-01", clockIn = "2026-04-01T08:00:00", clockOut = "2026-04-01T16:00:00", totalHours = 8.0))

        val result = repo.getOpenRecord("emp-01")
        assertIs<Result.Success<AttendanceRecord?>>(result)
        assertNull(result.data)
    }
}

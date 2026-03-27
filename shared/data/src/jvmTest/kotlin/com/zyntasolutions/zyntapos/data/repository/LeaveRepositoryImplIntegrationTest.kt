package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — LeaveRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [LeaveRepositoryImpl] against a real in-memory SQLite database.
 * Requires employees seeded to satisfy the employee_id FK constraint.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getByEmployee emits leave records for an employee via Turbine
 *  C. getPendingForStore emits pending leaves for a store
 *  D. getPendingForStore excludes approved/rejected leaves
 *  E. updateStatus changes PENDING → APPROVED
 *  F. getByEmployeeAndPeriod filters by date range
 *  G. getById unknown id returns error
 */
class LeaveRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: LeaveRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = LeaveRepositoryImpl(db, SyncEnqueuer(db))

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
        id: String = "leave-01",
        employeeId: String = "emp-01",
        leaveType: LeaveType = LeaveType.ANNUAL,
        startDate: String = "2026-05-01",
        endDate: String = "2026-05-05",
        reason: String? = "Family vacation",
        status: LeaveStatus = LeaveStatus.PENDING,
    ) = LeaveRecord(
        id = id,
        employeeId = employeeId,
        leaveType = leaveType,
        startDate = startDate,
        endDate = endDate,
        reason = reason,
        status = status,
        approvedBy = null,
        approvedAt = null,
        rejectionReason = null,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById round-trip preserves all fields`() = runTest {
        val record = makeRecord(
            id = "leave-01",
            leaveType = LeaveType.SICK,
            startDate = "2026-05-01",
            endDate = "2026-05-03",
            reason = "Medical leave",
        )
        val insertResult = repo.insert(record)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("leave-01")
        assertIs<Result.Success<LeaveRecord>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("leave-01", fetched.id)
        assertEquals("emp-01", fetched.employeeId)
        assertEquals(LeaveType.SICK, fetched.leaveType)
        assertEquals("2026-05-01", fetched.startDate)
        assertEquals("2026-05-03", fetched.endDate)
        assertEquals("Medical leave", fetched.reason)
        assertEquals(LeaveStatus.PENDING, fetched.status)
    }

    @Test
    fun `B - getByEmployee emits leave records via Turbine`() = runTest {
        repo.insert(makeRecord(id = "leave-01", startDate = "2026-05-01", endDate = "2026-05-03"))
        repo.insert(makeRecord(id = "leave-02", leaveType = LeaveType.PERSONAL, startDate = "2026-06-01", endDate = "2026-06-02"))

        repo.getByEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.employeeId == "emp-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getPendingForStore emits pending leave records for a store`() = runTest {
        repo.insert(makeRecord(id = "leave-01", status = LeaveStatus.PENDING))
        repo.insert(makeRecord(id = "leave-02", status = LeaveStatus.PENDING, startDate = "2026-06-01", endDate = "2026-06-03"))

        repo.getPendingForStore("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.status == LeaveStatus.PENDING })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getPendingForStore excludes approved and rejected leaves`() = runTest {
        repo.insert(makeRecord(id = "leave-pending", status = LeaveStatus.PENDING))
        repo.insert(makeRecord(id = "leave-approved", status = LeaveStatus.APPROVED, startDate = "2026-06-01", endDate = "2026-06-02"))
        repo.insert(makeRecord(id = "leave-rejected", status = LeaveStatus.REJECTED, startDate = "2026-07-01", endDate = "2026-07-02"))

        repo.getPendingForStore("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("leave-pending", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - updateStatus changes PENDING to APPROVED`() = runTest {
        repo.insert(makeRecord(id = "leave-01", status = LeaveStatus.PENDING))

        val updateResult = repo.updateStatus(
            id = "leave-01",
            status = LeaveStatus.APPROVED,
            decidedBy = "manager-01",
            decidedAt = now,
            rejectionReason = null,
            updatedAt = now,
        )
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("leave-01") as Result.Success).data
        assertEquals(LeaveStatus.APPROVED, fetched.status)
        assertEquals("manager-01", fetched.approvedBy)
    }

    @Test
    fun `F - getByEmployeeAndPeriod filters by date range`() = runTest {
        repo.insert(makeRecord(id = "leave-jan", startDate = "2026-01-15", endDate = "2026-01-17"))
        repo.insert(makeRecord(id = "leave-apr", startDate = "2026-04-01", endDate = "2026-04-05"))
        repo.insert(makeRecord(id = "leave-dec", startDate = "2026-12-20", endDate = "2026-12-25"))

        val result = repo.getByEmployeeAndPeriod(
            employeeId = "emp-01",
            from = "2026-03-01",
            to = "2026-06-30",
        )
        assertIs<Result.Success<List<LeaveRecord>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("leave-apr", result.data.first().id)
    }

    @Test
    fun `G - getById unknown id returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }
}

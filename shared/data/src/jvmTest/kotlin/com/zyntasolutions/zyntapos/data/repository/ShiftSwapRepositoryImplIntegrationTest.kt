package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapRequest
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapStatus
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
 * ZyntaPOS — ShiftSwapRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [ShiftSwapRepositoryImpl] against a real in-memory SQLite database.
 * Requires employees AND shift_schedules seeded to satisfy FK constraints:
 *   shift_swap_requests.requesting_employee_id → employees(id)
 *   shift_swap_requests.target_employee_id     → employees(id)
 *   shift_swap_requests.requesting_shift_id    → shift_schedules(id)
 *   shift_swap_requests.target_shift_id        → shift_schedules(id)
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getPendingForEmployee emits pending requests for requesting employee via Turbine
 *  C. getPendingForEmployee emits pending requests for target employee via Turbine
 *  D. getPendingForManager emits all PENDING requests via Turbine
 *  E. getByRequestingEmployee emits all requests regardless of status via Turbine
 *  F. updateStatus changes PENDING → TARGET_ACCEPTED
 *  G. updateStatus changes TARGET_ACCEPTED → MANAGER_APPROVED with notes
 *  H. getById returns null for unknown id
 */
class ShiftSwapRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ShiftSwapRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = ShiftSwapRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed employees required by FK constraints
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
        db.employeesQueries.insertEmployee(
            id = "emp-02", user_id = null, store_id = "store-01",
            first_name = "Bob", last_name = "Jones",
            email = null, phone = null, address = null,
            date_of_birth = null, hire_date = "2024-01-01",
            department = null, position = "Supervisor",
            salary = 70000.0, salary_type = "MONTHLY",
            commission_rate = 0.0, emergency_contact = null, documents = null,
            is_active = 1L, created_at = now, updated_at = now, deleted_at = null,
            sync_status = "PENDING",
        )

        // Seed shift_schedules required by FK constraints
        // shift-01: Alice on Apr 1
        // shift-02: Bob on Apr 1
        // shift-03: Alice on Apr 2
        // shift-04: Bob on Apr 2
        listOf(
            Triple("shift-01", "emp-01", "2026-04-01"),
            Triple("shift-02", "emp-02", "2026-04-01"),
            Triple("shift-03", "emp-01", "2026-04-02"),
            Triple("shift-04", "emp-02", "2026-04-02"),
        ).forEach { (shiftId, empId, date) ->
            db.shift_schedulesQueries.upsertShift(
                id = shiftId,
                employee_id = empId,
                store_id = "store-01",
                shift_date = date,
                start_time = "08:00",
                end_time = "16:00",
                notes = null,
                created_at = now,
                updated_at = now,
                sync_status = "PENDING",
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeRequest(
        id: String = "swap-01",
        requestingEmployeeId: String = "emp-01",
        targetEmployeeId: String = "emp-02",
        requestingShiftId: String = "shift-01",
        targetShiftId: String = "shift-02",
        status: ShiftSwapStatus = ShiftSwapStatus.PENDING,
        reason: String = "Personal appointment",
        managerNotes: String? = null,
    ) = ShiftSwapRequest(
        id = id,
        requestingEmployeeId = requestingEmployeeId,
        targetEmployeeId = targetEmployeeId,
        requestingShiftId = requestingShiftId,
        targetShiftId = targetShiftId,
        status = status,
        reason = reason,
        managerNotes = managerNotes,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById round-trip preserves all fields`() = runTest {
        val request = makeRequest(
            id = "swap-01",
            requestingEmployeeId = "emp-01",
            targetEmployeeId = "emp-02",
            requestingShiftId = "shift-01",
            targetShiftId = "shift-02",
            reason = "Doctor appointment",
        )
        val insertResult = repo.insert(request)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("swap-01")
        assertIs<Result.Success<ShiftSwapRequest?>>(fetchResult)
        val fetched = fetchResult.data
        assertNotNull(fetched)
        assertEquals("swap-01", fetched.id)
        assertEquals("emp-01", fetched.requestingEmployeeId)
        assertEquals("emp-02", fetched.targetEmployeeId)
        assertEquals("shift-01", fetched.requestingShiftId)
        assertEquals("shift-02", fetched.targetShiftId)
        assertEquals(ShiftSwapStatus.PENDING, fetched.status)
        assertEquals("Doctor appointment", fetched.reason)
        assertNull(fetched.managerNotes)
    }

    @Test
    fun `B - getPendingForEmployee emits pending requests for requesting employee`() = runTest {
        repo.insert(makeRequest(id = "swap-01", requestingEmployeeId = "emp-01", targetEmployeeId = "emp-02",
            requestingShiftId = "shift-01", targetShiftId = "shift-02"))
        repo.insert(makeRequest(id = "swap-02", requestingEmployeeId = "emp-01", targetEmployeeId = "emp-02",
            requestingShiftId = "shift-03", targetShiftId = "shift-04"))

        repo.getPendingForEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.status == ShiftSwapStatus.PENDING })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getPendingForEmployee emits pending requests where employee is target`() = runTest {
        // emp-02 is target in this swap
        repo.insert(makeRequest(id = "swap-01", requestingEmployeeId = "emp-01", targetEmployeeId = "emp-02",
            requestingShiftId = "shift-01", targetShiftId = "shift-02"))

        repo.getPendingForEmployee("emp-02").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("swap-01", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getPendingForManager emits TARGET_ACCEPTED requests awaiting manager approval`() = runTest {
        // getPendingForManager() returns only TARGET_ACCEPTED records (not PENDING).
        // A swap must be accepted by the target employee before the manager sees it.
        repo.insert(makeRequest(id = "swap-01", requestingEmployeeId = "emp-01", targetEmployeeId = "emp-02",
            requestingShiftId = "shift-01", targetShiftId = "shift-02",
            status = ShiftSwapStatus.TARGET_ACCEPTED))
        repo.insert(makeRequest(id = "swap-02", requestingEmployeeId = "emp-02", targetEmployeeId = "emp-01",
            requestingShiftId = "shift-02", targetShiftId = "shift-01",
            status = ShiftSwapStatus.TARGET_ACCEPTED))
        // This PENDING record should NOT appear in the manager queue
        repo.insert(makeRequest(id = "swap-03", requestingEmployeeId = "emp-01", targetEmployeeId = "emp-02",
            requestingShiftId = "shift-03", targetShiftId = "shift-04",
            status = ShiftSwapStatus.PENDING))

        repo.getPendingForManager().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.status == ShiftSwapStatus.TARGET_ACCEPTED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - getByRequestingEmployee emits all requests regardless of status`() = runTest {
        repo.insert(makeRequest(id = "swap-01", requestingEmployeeId = "emp-01", targetEmployeeId = "emp-02",
            requestingShiftId = "shift-01", targetShiftId = "shift-02", status = ShiftSwapStatus.PENDING))
        repo.insert(makeRequest(id = "swap-02", requestingEmployeeId = "emp-01", targetEmployeeId = "emp-02",
            requestingShiftId = "shift-03", targetShiftId = "shift-04", status = ShiftSwapStatus.CANCELLED))

        repo.getByRequestingEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.requestingEmployeeId == "emp-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `F - updateStatus changes PENDING to TARGET_ACCEPTED`() = runTest {
        repo.insert(makeRequest(id = "swap-01", status = ShiftSwapStatus.PENDING))

        val updateResult = repo.updateStatus(
            id = "swap-01",
            status = ShiftSwapStatus.TARGET_ACCEPTED,
            managerNotes = null,
            updatedAt = now,
        )
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("swap-01") as Result.Success).data
        assertNotNull(fetched)
        assertEquals(ShiftSwapStatus.TARGET_ACCEPTED, fetched.status)
    }

    @Test
    fun `G - updateStatus changes TARGET_ACCEPTED to MANAGER_APPROVED with notes`() = runTest {
        repo.insert(makeRequest(id = "swap-01", status = ShiftSwapStatus.TARGET_ACCEPTED))

        repo.updateStatus(
            id = "swap-01",
            status = ShiftSwapStatus.MANAGER_APPROVED,
            managerNotes = "Approved. Please update your schedule.",
            updatedAt = now,
        )

        val fetched = (repo.getById("swap-01") as Result.Success).data
        assertNotNull(fetched)
        assertEquals(ShiftSwapStatus.MANAGER_APPROVED, fetched.status)
        assertEquals("Approved. Please update your schedule.", fetched.managerNotes)
    }

    @Test
    fun `H - getById returns null for unknown id`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Success<ShiftSwapRequest?>>(result)
        assertNull(result.data)
    }
}

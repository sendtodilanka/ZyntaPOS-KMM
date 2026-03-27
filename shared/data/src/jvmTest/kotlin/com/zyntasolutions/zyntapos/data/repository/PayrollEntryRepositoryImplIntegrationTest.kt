package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.PayrollEntry
import com.zyntasolutions.zyntapos.domain.model.PayrollEntryStatus
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
 * ZyntaPOS — PayrollEntryRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [PayrollEntryRepositoryImpl] against a real in-memory SQLite database.
 * Requires employees seeded to satisfy the employee_id FK constraint.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getByEmployee emits entries via Turbine
 *  C. getByPeriod filters by period start/end
 *  D. updateStatus DRAFT → APPROVED
 *  E. updateStatus APPROVED → PAID
 *  F. getById unknown id returns error
 *  G. null deductionNotes round-trip
 */
class PayrollEntryRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: PayrollEntryRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = PayrollEntryRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed employees required by employee_id FK
        db.employeesQueries.insertEmployee(
            id = "emp-01", user_id = null, store_id = "store-01",
            first_name = "Alice", last_name = "Smith",
            email = null, phone = null, address = null,
            date_of_birth = null, hire_date = "2024-01-01",
            department = null, position = "Cashier",
            salary = 60000.0, salary_type = "MONTHLY",
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
            salary = 80000.0, salary_type = "MONTHLY",
            commission_rate = 0.0, emergency_contact = null, documents = null,
            is_active = 1L, created_at = now, updated_at = now, deleted_at = null,
            sync_status = "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeEntry(
        id: String = "pe-01",
        employeeId: String = "emp-01",
        periodStart: String = "2026-04-01",
        periodEnd: String = "2026-04-30",
        baseSalary: Double = 60000.0,
        overtimeHours: Double = 4.0,
        overtimeRate: Double = 500.0,
        overtimePay: Double = 2000.0,
        deductions: Double = 5000.0,
        deductionNotes: String? = "Tax and insurance",
        netPay: Double = 57000.0,
        status: PayrollEntryStatus = PayrollEntryStatus.DRAFT,
    ) = PayrollEntry(
        id = id,
        employeeId = employeeId,
        periodStart = periodStart,
        periodEnd = periodEnd,
        baseSalary = baseSalary,
        overtimeHours = overtimeHours,
        overtimeRate = overtimeRate,
        overtimePay = overtimePay,
        deductions = deductions,
        deductionNotes = deductionNotes,
        netPay = netPay,
        status = status,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById round-trip preserves all fields`() = runTest {
        val entry = makeEntry(
            id = "pe-01",
            employeeId = "emp-01",
            periodStart = "2026-04-01",
            periodEnd = "2026-04-30",
            baseSalary = 60000.0,
            overtimeHours = 8.0,
            overtimeRate = 450.0,
            overtimePay = 3600.0,
            deductions = 6000.0,
            deductionNotes = "EPF + ETF",
            netPay = 57600.0,
        )
        val insertResult = repo.insert(entry)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("pe-01")
        assertIs<Result.Success<PayrollEntry>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("pe-01", fetched.id)
        assertEquals("emp-01", fetched.employeeId)
        assertEquals("2026-04-01", fetched.periodStart)
        assertEquals("2026-04-30", fetched.periodEnd)
        assertEquals(60000.0, fetched.baseSalary)
        assertEquals(8.0, fetched.overtimeHours)
        assertEquals(450.0, fetched.overtimeRate)
        assertEquals(3600.0, fetched.overtimePay)
        assertEquals(6000.0, fetched.deductions)
        assertEquals("EPF + ETF", fetched.deductionNotes)
        assertEquals(57600.0, fetched.netPay)
        assertEquals(PayrollEntryStatus.DRAFT, fetched.status)
    }

    @Test
    fun `B - getByEmployee emits payroll entries via Turbine`() = runTest {
        repo.insert(makeEntry(id = "pe-01", employeeId = "emp-01", periodStart = "2026-03-01", periodEnd = "2026-03-31"))
        repo.insert(makeEntry(id = "pe-02", employeeId = "emp-01", periodStart = "2026-04-01", periodEnd = "2026-04-30"))
        repo.insert(makeEntry(id = "pe-03", employeeId = "emp-02", periodStart = "2026-04-01", periodEnd = "2026-04-30"))

        repo.getByEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.employeeId == "emp-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByPeriod filters by period start and end`() = runTest {
        repo.insert(makeEntry(id = "pe-jan", employeeId = "emp-01", periodStart = "2026-01-01", periodEnd = "2026-01-31"))
        repo.insert(makeEntry(id = "pe-apr-1", employeeId = "emp-01", periodStart = "2026-04-01", periodEnd = "2026-04-30"))
        repo.insert(makeEntry(id = "pe-apr-2", employeeId = "emp-02", periodStart = "2026-04-01", periodEnd = "2026-04-30"))

        val result = repo.getByPeriod("2026-04-01", "2026-04-30")
        assertIs<Result.Success<List<PayrollEntry>>>(result)
        assertEquals(2, result.data.size)
        assertTrue(result.data.all { it.periodStart == "2026-04-01" && it.periodEnd == "2026-04-30" })
    }

    @Test
    fun `D - updateStatus changes DRAFT to APPROVED`() = runTest {
        repo.insert(makeEntry(id = "pe-01", status = PayrollEntryStatus.DRAFT))

        val updateResult = repo.updateStatus("pe-01", PayrollEntryStatus.APPROVED)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("pe-01") as Result.Success).data
        assertEquals(PayrollEntryStatus.APPROVED, fetched.status)
    }

    @Test
    fun `E - updateStatus changes APPROVED to PAID`() = runTest {
        repo.insert(makeEntry(id = "pe-01", status = PayrollEntryStatus.APPROVED))

        repo.updateStatus("pe-01", PayrollEntryStatus.PAID)

        val fetched = (repo.getById("pe-01") as Result.Success).data
        assertEquals(PayrollEntryStatus.PAID, fetched.status)
    }

    @Test
    fun `F - getById unknown id returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `G - null deductionNotes round-trips correctly`() = runTest {
        repo.insert(makeEntry(id = "pe-01", deductionNotes = null))

        val fetched = (repo.getById("pe-01") as Result.Success).data
        assertNull(fetched.deductionNotes)
    }
}

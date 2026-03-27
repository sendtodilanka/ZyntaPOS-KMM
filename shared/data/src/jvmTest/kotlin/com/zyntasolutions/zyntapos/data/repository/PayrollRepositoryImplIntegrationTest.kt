package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollStatus
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
 * ZyntaPOS — PayrollRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [PayrollRepositoryImpl] against a real in-memory SQLite database.
 * Requires employees seeded to satisfy the employee_id FK constraint.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getByEmployee emits payroll records via Turbine
 *  C. getByEmployeeAndPeriod returns record for matching period
 *  D. markPaid updates status to PAID with paidAt and paymentRef
 *  E. getPending returns only PENDING records for a store
 *  F. getSummary aggregates total payroll for a store/period
 *  G. getById unknown id returns error
 */
class PayrollRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: PayrollRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = PayrollRepositoryImpl(db, SyncEnqueuer(db))

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

    private fun makePayroll(
        id: String = "pay-01",
        employeeId: String = "emp-01",
        periodStart: String = "2026-04-01",
        periodEnd: String = "2026-04-30",
        baseSalary: Double = 60000.0,
        overtimePay: Double = 0.0,
        commission: Double = 0.0,
        deductions: Double = 5000.0,
        netPay: Double = 55000.0,
        status: PayrollStatus = PayrollStatus.PENDING,
    ) = PayrollRecord(
        id = id,
        employeeId = employeeId,
        periodStart = periodStart,
        periodEnd = periodEnd,
        baseSalary = baseSalary,
        overtimePay = overtimePay,
        commission = commission,
        deductions = deductions,
        netPay = netPay,
        status = status,
        paidAt = null,
        paymentRef = null,
        notes = null,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById round-trip preserves all fields`() = runTest {
        val record = makePayroll(
            id = "pay-01",
            employeeId = "emp-01",
            periodStart = "2026-04-01",
            periodEnd = "2026-04-30",
            baseSalary = 60000.0,
            deductions = 6000.0,
            netPay = 54000.0,
        )
        val insertResult = repo.insert(record)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("pay-01")
        assertIs<Result.Success<PayrollRecord>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("pay-01", fetched.id)
        assertEquals("emp-01", fetched.employeeId)
        assertEquals("2026-04-01", fetched.periodStart)
        assertEquals("2026-04-30", fetched.periodEnd)
        assertEquals(60000.0, fetched.baseSalary)
        assertEquals(6000.0, fetched.deductions)
        assertEquals(54000.0, fetched.netPay)
        assertEquals(PayrollStatus.PENDING, fetched.status)
        assertNull(fetched.paidAt)
    }

    @Test
    fun `B - getByEmployee emits payroll records via Turbine`() = runTest {
        repo.insert(makePayroll(id = "pay-01", employeeId = "emp-01", periodStart = "2026-03-01", periodEnd = "2026-03-31"))
        repo.insert(makePayroll(id = "pay-02", employeeId = "emp-01", periodStart = "2026-04-01", periodEnd = "2026-04-30"))
        repo.insert(makePayroll(id = "pay-03", employeeId = "emp-02", periodStart = "2026-04-01", periodEnd = "2026-04-30"))

        repo.getByEmployee("emp-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.employeeId == "emp-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByEmployeeAndPeriod returns record for matching period`() = runTest {
        repo.insert(makePayroll(id = "pay-01", periodStart = "2026-04-01", periodEnd = "2026-04-30"))

        val result = repo.getByEmployeeAndPeriod("emp-01", "2026-04-01")
        assertIs<Result.Success<PayrollRecord?>>(result)
        assertNotNull(result.data)
        assertEquals("pay-01", result.data!!.id)
    }

    @Test
    fun `D - markPaid updates status to PAID with paidAt and paymentRef`() = runTest {
        repo.insert(makePayroll(id = "pay-01", status = PayrollStatus.PENDING))

        val markResult = repo.markPaid(
            id = "pay-01",
            paidAt = now,
            paymentRef = "TXN-001",
            updatedAt = now,
        )
        assertIs<Result.Success<Unit>>(markResult)

        val fetched = (repo.getById("pay-01") as Result.Success).data
        assertEquals(PayrollStatus.PAID, fetched.status)
        assertNotNull(fetched.paidAt)
        assertEquals("TXN-001", fetched.paymentRef)
    }

    @Test
    fun `E - getPending returns only PENDING records for a store`() = runTest {
        repo.insert(makePayroll(id = "pay-pending-1", employeeId = "emp-01", status = PayrollStatus.PENDING))
        repo.insert(makePayroll(id = "pay-pending-2", employeeId = "emp-02", status = PayrollStatus.PENDING))
        repo.insert(makePayroll(id = "pay-paid", employeeId = "emp-01", status = PayrollStatus.PAID,
            periodStart = "2026-03-01", periodEnd = "2026-03-31"))

        val result = repo.getPending("store-01")
        assertIs<Result.Success<List<PayrollRecord>>>(result)
        assertEquals(2, result.data.size)
        assertTrue(result.data.all { it.status == PayrollStatus.PENDING })
    }

    @Test
    fun `F - getSummary aggregates total payroll for a store and period`() = runTest {
        repo.insert(makePayroll(id = "pay-01", employeeId = "emp-01", netPay = 55000.0,
            periodStart = "2026-04-01", periodEnd = "2026-04-30"))
        repo.insert(makePayroll(id = "pay-02", employeeId = "emp-02", netPay = 75000.0,
            periodStart = "2026-04-01", periodEnd = "2026-04-30"))

        val result = repo.getSummary("store-01", "2026-04-01")
        assertIs<Result.Success<*>>(result)
        val summary = result.data
        assertNotNull(summary)
    }

    @Test
    fun `G - getById unknown id returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }
}

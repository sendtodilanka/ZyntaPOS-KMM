package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollStatus
import com.zyntasolutions.zyntapos.domain.model.SalaryType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAttendanceRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakePayrollRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildAttendanceSummary
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildEmployee
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [GeneratePayrollUseCase].
 *
 * Covers:
 * - Guard: employee must have salary configured (REQUIRED)
 * - Guard: duplicate payroll record for same employee+period is rejected (DUPLICATE)
 * - HOURLY salary type: base = regular hours * rate; overtime = OT hours * rate * 1.5
 * - DAILY salary type: base = present days * rate; overtime calculated from daily rate / 8
 * - MONTHLY/WEEKLY salary type: base = fixed salary; overtime uses employee.hourlyRate
 * - Deductions are subtracted from net pay
 * - PayrollRecord is persisted via repository with correct metadata
 */
class GeneratePayrollUseCaseTest {

    private lateinit var fakePayrollRepo: FakePayrollRepository
    private lateinit var fakeAttendanceRepo: FakeAttendanceRepository
    private lateinit var generatePayrollUseCase: GeneratePayrollUseCase

    @BeforeTest
    fun setUp() {
        fakePayrollRepo = FakePayrollRepository()
        fakeAttendanceRepo = FakeAttendanceRepository()
        generatePayrollUseCase = GeneratePayrollUseCase(fakePayrollRepo, fakeAttendanceRepo)
    }

    // ─── Guard: missing salary ─────────────────────────────────────────────────

    @Test
    fun `returns REQUIRED error when employee has no salary configured`() = runTest {
        val employee = buildEmployee(salary = null)

        val result = generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
        assertEquals("salary", ex.field)
    }

    @Test
    fun `does not persist payroll record when employee has no salary`() = runTest {
        val employee = buildEmployee(salary = null)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        assertEquals(0, fakePayrollRepo.payrollRecords.size)
    }

    // ─── Guard: duplicate payroll period ──────────────────────────────────────

    @Test
    fun `returns DUPLICATE error when payroll record already exists for employee+period`() = runTest {
        val employee = buildEmployee(id = "emp-01", salary = 1000.0)
        fakePayrollRepo.existingRecord = PayrollRecord(
            id = "existing-payroll",
            employeeId = "emp-01",
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            baseSalary = 1000.0,
            netPay = 1000.0,
            createdAt = 900_000L,
            updatedAt = 900_000L,
        )

        val result = generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-02",
            createdAt = 1_000_000L,
        )

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("DUPLICATE", ex.rule)
        assertEquals("periodStart", ex.field)
    }

    @Test
    fun `does not persist when duplicate payroll record exists`() = runTest {
        val employee = buildEmployee(id = "emp-01", salary = 1000.0)
        fakePayrollRepo.existingRecord = PayrollRecord(
            id = "existing-payroll",
            employeeId = "emp-01",
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            baseSalary = 1000.0,
            netPay = 1000.0,
            createdAt = 900_000L,
            updatedAt = 900_000L,
        )

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-02",
            createdAt = 1_000_000L,
        )

        // No new records should be inserted
        assertEquals(0, fakePayrollRepo.payrollRecords.size)
    }

    // ─── HOURLY salary type ───────────────────────────────────────────────────

    @Test
    fun `HOURLY - calculates base and overtime correctly`() = runTest {
        // 144 total hours, 4 overtime → regular = 140h
        // rate = 10.0/hr → base = 140 * 10 = 1400, OT = 4 * 10 * 1.5 = 60, net = 1460
        fakeAttendanceRepo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            totalHours = 144.0,
            overtimeHours = 4.0,
        )
        val employee = buildEmployee(id = "emp-01", salary = 10.0, salaryType = SalaryType.HOURLY)

        val result = generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        assertIs<Result.Success<Unit>>(result)
        val payroll = fakePayrollRepo.payrollRecords.first()
        assertEquals(1400.0, payroll.baseSalary, 0.001)
        assertEquals(60.0, payroll.overtimePay, 0.001)
        assertEquals(1460.0, payroll.netPay, 0.001)
    }

    @Test
    fun `HOURLY - zero overtime when no overtime hours`() = runTest {
        fakeAttendanceRepo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            totalHours = 80.0,
            overtimeHours = 0.0,
        )
        val employee = buildEmployee(id = "emp-01", salary = 15.0, salaryType = SalaryType.HOURLY)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        val payroll = fakePayrollRepo.payrollRecords.first()
        // 80 regular hours * 15.0 = 1200.0
        assertEquals(1200.0, payroll.baseSalary, 0.001)
        assertEquals(0.0, payroll.overtimePay, 0.001)
    }

    // ─── DAILY salary type ────────────────────────────────────────────────────

    @Test
    fun `DAILY - calculates base from presentDays and overtime from daily rate`() = runTest {
        // 18 present days, salary = 50.0/day → base = 18 * 50 = 900
        // 4 OT hours, daily rate / 8 = 50/8 = 6.25/hr, OT = 4 * 6.25 * 1.5 = 37.5
        fakeAttendanceRepo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            presentDays = 18,
            totalHours = 144.0,
            overtimeHours = 4.0,
        )
        val employee = buildEmployee(id = "emp-01", salary = 50.0, salaryType = SalaryType.DAILY)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        val payroll = fakePayrollRepo.payrollRecords.first()
        assertEquals(900.0, payroll.baseSalary, 0.001)
        assertEquals(37.5, payroll.overtimePay, 0.001)
    }

    // ─── MONTHLY salary type ──────────────────────────────────────────────────

    @Test
    fun `MONTHLY - base salary is the fixed salary amount`() = runTest {
        fakeAttendanceRepo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            totalHours = 160.0,
            overtimeHours = 0.0,
        )
        val employee = buildEmployee(id = "emp-01", salary = 2000.0, salaryType = SalaryType.MONTHLY)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        val payroll = fakePayrollRepo.payrollRecords.first()
        assertEquals(2000.0, payroll.baseSalary, 0.001)
        assertEquals(0.0, payroll.overtimePay, 0.001)
    }

    @Test
    fun `MONTHLY - overtime uses hourlyRate derived from salary`() = runTest {
        // Monthly salary = 1600; hourlyRate = 1600/160 = 10/hr
        // 8 OT hours → OT = 8 * 10 * 1.5 = 120; net = 1600 + 120 = 1720
        fakeAttendanceRepo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            totalHours = 168.0,
            overtimeHours = 8.0,
        )
        val employee = buildEmployee(id = "emp-01", salary = 1600.0, salaryType = SalaryType.MONTHLY)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        val payroll = fakePayrollRepo.payrollRecords.first()
        assertEquals(1600.0, payroll.baseSalary, 0.001)
        assertEquals(120.0, payroll.overtimePay, 0.001)
        assertEquals(1720.0, payroll.netPay, 0.001)
    }

    // ─── WEEKLY salary type ───────────────────────────────────────────────────

    @Test
    fun `WEEKLY - base salary is the fixed salary amount`() = runTest {
        // For WEEKLY the base = fixed salary (same branch as MONTHLY in the use case)
        fakeAttendanceRepo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            totalHours = 40.0,
            overtimeHours = 0.0,
        )
        val employee = buildEmployee(id = "emp-01", salary = 400.0, salaryType = SalaryType.WEEKLY)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-07",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        val payroll = fakePayrollRepo.payrollRecords.first()
        assertEquals(400.0, payroll.baseSalary, 0.001)
    }

    // ─── Deductions ───────────────────────────────────────────────────────────

    @Test
    fun `deductions are subtracted from net pay`() = runTest {
        fakeAttendanceRepo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            totalHours = 160.0,
            overtimeHours = 0.0,
        )
        val employee = buildEmployee(id = "emp-01", salary = 2000.0, salaryType = SalaryType.MONTHLY)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
            deductions = 200.0,
        )

        val payroll = fakePayrollRepo.payrollRecords.first()
        assertEquals(200.0, payroll.deductions, 0.001)
        assertEquals(1800.0, payroll.netPay, 0.001)
    }

    @Test
    fun `zero deductions leaves net pay equal to gross`() = runTest {
        fakeAttendanceRepo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            totalHours = 160.0,
            overtimeHours = 0.0,
        )
        val employee = buildEmployee(id = "emp-01", salary = 1500.0, salaryType = SalaryType.MONTHLY)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
            deductions = 0.0,
        )

        val payroll = fakePayrollRepo.payrollRecords.first()
        assertEquals(payroll.grossPay, payroll.netPay, 0.001)
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    @Test
    fun `payroll record is persisted with correct metadata`() = runTest {
        val employee = buildEmployee(id = "emp-01", salary = 1000.0, salaryType = SalaryType.MONTHLY)

        generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-99",
            createdAt = 5_000_000L,
        )

        assertEquals(1, fakePayrollRepo.payrollRecords.size)
        val payroll = fakePayrollRepo.payrollRecords.first()
        assertEquals("payroll-99", payroll.id)
        assertEquals("emp-01", payroll.employeeId)
        assertEquals("2026-02-01", payroll.periodStart)
        assertEquals("2026-02-28", payroll.periodEnd)
        assertEquals(5_000_000L, payroll.createdAt)
        assertEquals(5_000_000L, payroll.updatedAt)
        assertEquals(PayrollStatus.PENDING, payroll.status)
    }

    @Test
    fun `payroll record is not persisted when repository insert fails`() = runTest {
        fakePayrollRepo.shouldFailInsert = true
        val employee = buildEmployee(salary = 1000.0)

        val result = generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        assertIs<Result.Error>(result)
        assertEquals(0, fakePayrollRepo.payrollRecords.size)
    }

    @Test
    fun `propagates repository error from getByEmployeeAndPeriod`() = runTest {
        // Signal the fake to return a DB error on the duplicate-check query
        fakePayrollRepo.shouldFailGetByEmployeeAndPeriod = true
        val employee = buildEmployee(salary = 1000.0)

        val result = generatePayrollUseCase(
            employee = employee,
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            recordId = "payroll-01",
            createdAt = 1_000_000L,
        )

        assertIs<Result.Error>(result)
    }
}

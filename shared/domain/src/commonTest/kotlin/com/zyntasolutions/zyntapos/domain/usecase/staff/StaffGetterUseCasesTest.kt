package com.zyntasolutions.zyntapos.domain.usecase.staff

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AttendanceStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveRequestType
import com.zyntasolutions.zyntapos.domain.model.LeaveRequestStatus
import com.zyntasolutions.zyntapos.domain.model.PayrollEntryStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAttendanceRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeEmployeeRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLeaveRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakePayrollRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildAttendanceRecord
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildAttendanceSummary
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildEmployee
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildLeaveRecord
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildLeaveRequest
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildPayrollRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the thin-wrapper staff getter use cases:
 * [GetEmployeesUseCase], [GetEmployeeByIdUseCase], [GetAttendanceHistoryUseCase],
 * [GetTodayAttendanceUseCase], [GetAttendanceSummaryUseCase], [GetLeaveHistoryUseCase],
 * [GetPendingLeaveRequestsUseCase], [GetPayrollHistoryUseCase],
 * [GetCrossStoreAttendanceUseCase], [ProcessPayrollPaymentUseCase],
 * [RequestLeaveUseCase], [CalculatePayrollUseCase].
 */
class StaffGetterUseCasesTest {

    // ─────────────────────────────────────────────────────────────────────────
    // GetEmployeesUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getEmployees_emptyStore_emitsEmptyList`() = runTest {
        GetEmployeesUseCase(FakeEmployeeRepository())("store-01").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getEmployees_filtersActiveByStore`() = runTest {
        val repo = FakeEmployeeRepository()
        repo.employees.add(buildEmployee(id = "e1", storeId = "store-01"))
        repo.employees.add(buildEmployee(id = "e2", storeId = "store-01").copy(isActive = false))
        repo.employees.add(buildEmployee(id = "e3", storeId = "store-02"))

        GetEmployeesUseCase(repo)("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("e1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getEmployees_multipleActive_returnsAll`() = runTest {
        val repo = FakeEmployeeRepository()
        repo.employees.add(buildEmployee(id = "e1", storeId = "store-01"))
        repo.employees.add(buildEmployee(id = "e2", storeId = "store-01"))

        GetEmployeesUseCase(repo)("store-01").test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetEmployeeByIdUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getEmployeeById_found_returnsSuccess`() = runTest {
        val repo = FakeEmployeeRepository()
        repo.employees.add(buildEmployee(id = "e1"))
        val result = GetEmployeeByIdUseCase(repo)("e1")
        assertIs<Result.Success<*>>(result)
        assertEquals("e1", (result as Result.Success).data.id)
    }

    @Test
    fun `getEmployeeById_notFound_returnsError`() = runTest {
        val result = GetEmployeeByIdUseCase(FakeEmployeeRepository())("unknown")
        assertIs<Result.Error>(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetAttendanceHistoryUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getAttendanceHistory_returnsRecordsForEmployee`() = runTest {
        val repo = FakeAttendanceRepository()
        repo.records.add(buildAttendanceRecord(id = "a1", employeeId = "emp-01"))
        repo.records.add(buildAttendanceRecord(id = "a2", employeeId = "emp-02"))

        GetAttendanceHistoryUseCase(repo)("emp-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("a1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAttendanceHistory_emptyForUnknownEmployee`() = runTest {
        val repo = FakeAttendanceRepository()
        repo.records.add(buildAttendanceRecord(id = "a1", employeeId = "emp-01"))

        GetAttendanceHistoryUseCase(repo)("unknown").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetTodayAttendanceUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getTodayAttendance_returnsRecordsMatchingPrefix`() = runTest {
        val repo = FakeAttendanceRepository()
        repo.records.add(buildAttendanceRecord(id = "a1", clockIn = "2026-03-27T08:00:00"))
        repo.records.add(buildAttendanceRecord(id = "a2", clockIn = "2026-03-26T08:00:00"))

        val result = GetTodayAttendanceUseCase(repo)("store-01", "2026-03-27")
        assertIs<Result.Success<*>>(result)
        val list = (result as Result.Success).data
        assertEquals(1, list.size)
        assertEquals("a1", list.first().id)
    }

    @Test
    fun `getTodayAttendance_noMatchingPrefix_returnsEmptyList`() = runTest {
        val repo = FakeAttendanceRepository()
        repo.records.add(buildAttendanceRecord(id = "a1", clockIn = "2026-03-26T08:00:00"))

        val result = GetTodayAttendanceUseCase(repo)("store-01", "2026-03-27")
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetAttendanceSummaryUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getAttendanceSummary_returnsSummaryFromRepository`() = runTest {
        val repo = FakeAttendanceRepository()
        repo.summaryOverride = buildAttendanceSummary(
            employeeId = "emp-01",
            totalDays = 20,
            presentDays = 18,
        )

        val result = GetAttendanceSummaryUseCase(repo)("emp-01", "2026-03-01", "2026-03-31")
        assertIs<Result.Success<*>>(result)
        val summary = (result as Result.Success).data
        assertEquals(20, summary.totalDays)
        assertEquals(18, summary.presentDays)
    }

    @Test
    fun `getAttendanceSummary_defaultSummaryForNewEmployee`() = runTest {
        val result = GetAttendanceSummaryUseCase(FakeAttendanceRepository())("emp-new", "2026-03-01", "2026-03-31")
        assertIs<Result.Success<*>>(result)
        assertEquals("emp-new", (result as Result.Success).data.employeeId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetLeaveHistoryUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getLeaveHistory_returnsRecordsForEmployee`() = runTest {
        val repo = FakeLeaveRepository()
        repo.leaveRecords.add(buildLeaveRecord(id = "l1", employeeId = "emp-01"))
        repo.leaveRecords.add(buildLeaveRecord(id = "l2", employeeId = "emp-02"))

        GetLeaveHistoryUseCase(repo)("emp-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("l1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getLeaveHistory_emptyForUnknownEmployee`() = runTest {
        val repo = FakeLeaveRepository()
        repo.leaveRecords.add(buildLeaveRecord(id = "l1", employeeId = "emp-01"))

        GetLeaveHistoryUseCase(repo)("unknown").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetPendingLeaveRequestsUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getPendingLeaveRequests_returnsPendingRecords`() = runTest {
        val repo = FakeLeaveRepository()
        repo.leaveRecords.add(buildLeaveRecord(id = "l1", status = com.zyntasolutions.zyntapos.domain.model.LeaveStatus.PENDING))
        repo.leaveRecords.add(buildLeaveRecord(id = "l2", status = com.zyntasolutions.zyntapos.domain.model.LeaveStatus.APPROVED))

        GetPendingLeaveRequestsUseCase(repo)("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("l1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPendingLeaveRequests_emptyWhenNoPending`() = runTest {
        val repo = FakeLeaveRepository()
        repo.leaveRecords.add(buildLeaveRecord(id = "l1", status = com.zyntasolutions.zyntapos.domain.model.LeaveStatus.APPROVED))

        GetPendingLeaveRequestsUseCase(repo)("store-01").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetPayrollHistoryUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getPayrollHistory_returnsRecordsForEmployee`() = runTest {
        val repo = FakePayrollRepository()
        repo.payrollRecords.add(buildPayrollRecord(id = "p1", employeeId = "emp-01"))
        repo.payrollRecords.add(buildPayrollRecord(id = "p2", employeeId = "emp-02"))

        GetPayrollHistoryUseCase(repo)("emp-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("p1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPayrollHistory_emptyForUnknownEmployee`() = runTest {
        val repo = FakePayrollRepository()
        repo.payrollRecords.add(buildPayrollRecord(id = "p1", employeeId = "emp-01"))

        GetPayrollHistoryUseCase(repo)("unknown").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetCrossStoreAttendanceUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getCrossStoreAttendance_emptyEmployeeList_returnsEmptyResult`() = runTest {
        val result = GetCrossStoreAttendanceUseCase(FakeAttendanceRepository())(
            employees = emptyList(),
            from = "2026-01-01",
            to = "2026-12-31",
        )
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun `getCrossStoreAttendance_singleEmployee_aggregatesCorrectly`() = runTest {
        val repo = FakeAttendanceRepository()
        repo.records.add(buildAttendanceRecord(id = "a1", employeeId = "emp-01", clockIn = "2026-03-01T08:00:00", totalHours = 8.0).copy(storeId = "store-01"))
        repo.records.add(buildAttendanceRecord(id = "a2", employeeId = "emp-01", clockIn = "2026-03-02T08:00:00", totalHours = 9.0).copy(storeId = "store-01"))

        val employee = buildEmployee(id = "emp-01", firstName = "Jane", lastName = "Doe")
        val result = GetCrossStoreAttendanceUseCase(repo)(listOf(employee), "2026-03-01", "2026-03-31")

        assertIs<Result.Success<*>>(result)
        val rows = (result as Result.Success).data
        assertEquals(1, rows.size)
        assertEquals("emp-01", rows[0].employeeId)
        assertEquals("store-01", rows[0].storeId)
        assertEquals(2, rows[0].totalDays)
        assertEquals(17.0, rows[0].totalHoursWorked)
    }

    @Test
    fun `getCrossStoreAttendance_multipleStores_createsRowPerStore`() = runTest {
        val repo = FakeAttendanceRepository()
        repo.records.add(buildAttendanceRecord(id = "a1", employeeId = "emp-01", clockIn = "2026-03-01T08:00:00", totalHours = 8.0).copy(storeId = "store-01"))
        repo.records.add(buildAttendanceRecord(id = "a2", employeeId = "emp-01", clockIn = "2026-03-10T08:00:00", totalHours = 8.0).copy(storeId = "store-02"))

        val employee = buildEmployee(id = "emp-01")
        val result = GetCrossStoreAttendanceUseCase(repo)(listOf(employee), "2026-03-01", "2026-03-31")

        assertIs<Result.Success<*>>(result)
        val rows = (result as Result.Success).data
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.storeId == "store-01" })
        assertTrue(rows.any { it.storeId == "store-02" })
    }

    @Test
    fun `getCrossStoreAttendance_lateArrivals_countedCorrectly`() = runTest {
        val repo = FakeAttendanceRepository()
        repo.records.add(
            buildAttendanceRecord(id = "a1", employeeId = "emp-01", clockIn = "2026-03-01T09:30:00")
                .copy(storeId = "store-01", status = AttendanceStatus.LATE),
        )
        repo.records.add(
            buildAttendanceRecord(id = "a2", employeeId = "emp-01", clockIn = "2026-03-02T08:00:00")
                .copy(storeId = "store-01", status = AttendanceStatus.PRESENT),
        )

        val employee = buildEmployee(id = "emp-01")
        val result = GetCrossStoreAttendanceUseCase(repo)(listOf(employee), "2026-03-01", "2026-03-31")

        assertIs<Result.Success<*>>(result)
        val rows = (result as Result.Success).data
        assertEquals(1, rows[0].lateArrivals)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ProcessPayrollPaymentUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `processPayrollPayment_marksRecordAsPaid`() = runTest {
        val repo = FakePayrollRepository()
        repo.payrollRecords.add(buildPayrollRecord(id = "pay-01"))

        val result = ProcessPayrollPaymentUseCase(repo)(
            id = "pay-01",
            paidAt = 2_000_000L,
            paymentRef = "BANK-REF-001",
            updatedAt = 2_000_000L,
        )

        assertIs<Result.Success<*>>(result)
        val record = repo.payrollRecords.first()
        assertEquals(com.zyntasolutions.zyntapos.domain.model.PayrollStatus.PAID, record.status)
        assertEquals("BANK-REF-001", record.paymentRef)
        assertEquals(2_000_000L, record.paidAt)
    }

    @Test
    fun `processPayrollPayment_notFound_returnsError`() = runTest {
        val result = ProcessPayrollPaymentUseCase(FakePayrollRepository())(
            id = "unknown",
            paidAt = 2_000_000L,
            updatedAt = 2_000_000L,
        )
        assertIs<Result.Error>(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RequestLeaveUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `requestLeave_validRequest_persistsWithPendingStatus`() = runTest {
        val repo = FakeLeaveRepository()
        val request = buildLeaveRequest(
            employeeId = "emp-01",
            startDate = "2026-04-01",
            endDate = "2026-04-05",
            reason = "Annual vacation",
        )

        val result = RequestLeaveUseCase(repo)(request)
        assertIs<Result.Success<*>>(result)
        assertEquals(1, repo.leaveRequests.size)
        assertEquals(LeaveRequestStatus.PENDING, repo.leaveRequests.first().status)
    }

    @Test
    fun `requestLeave_sameDates_allowed`() = runTest {
        val repo = FakeLeaveRepository()
        val request = buildLeaveRequest(startDate = "2026-04-01", endDate = "2026-04-01")

        val result = RequestLeaveUseCase(repo)(request)
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `requestLeave_blankEmployeeId_returnsValidationError`() = runTest {
        val result = RequestLeaveUseCase(FakeLeaveRepository())(
            buildLeaveRequest(employeeId = ""),
        )
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
        assertEquals("employeeId", ((result).exception as ValidationException).field)
    }

    @Test
    fun `requestLeave_blankReason_returnsValidationError`() = runTest {
        val result = RequestLeaveUseCase(FakeLeaveRepository())(
            buildLeaveRequest(reason = ""),
        )
        assertIs<Result.Error>(result)
        assertEquals("reason", ((result as Result.Error).exception as ValidationException).field)
    }

    @Test
    fun `requestLeave_startDateAfterEndDate_returnsDateOrderError`() = runTest {
        val result = RequestLeaveUseCase(FakeLeaveRepository())(
            buildLeaveRequest(startDate = "2026-04-10", endDate = "2026-04-05"),
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("startDate", ex.field)
        assertEquals("DATE_ORDER", ex.rule)
    }

    @Test
    fun `requestLeave_validationFailure_doesNotPersist`() = runTest {
        val repo = FakeLeaveRepository()
        RequestLeaveUseCase(repo)(buildLeaveRequest(reason = ""))
        assertTrue(repo.leaveRequests.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CalculatePayrollUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `calculatePayroll_validInputs_returnsCorrectNetPay`() = runTest {
        val result = CalculatePayrollUseCase()(
            employeeId = "emp-01",
            periodStart = "2026-03-01",
            periodEnd = "2026-03-31",
            baseSalary = 1000.0,
            overtimeHours = 5.0,
            overtimeRate = 20.0,
            deductions = 100.0,
        )
        assertIs<Result.Success<*>>(result)
        val entry = (result as Result.Success).data
        assertEquals(100.0, entry.overtimePay, 0.001)    // 5 * 20
        assertEquals(1000.0, entry.netPay, 0.001)         // 1000 + 100 - 100
        assertEquals(PayrollEntryStatus.DRAFT, entry.status)
    }

    @Test
    fun `calculatePayroll_negativeSalary_returnsValidationError`() = runTest {
        val result = CalculatePayrollUseCase()(
            employeeId = "emp-01",
            periodStart = "2026-03-01",
            periodEnd = "2026-03-31",
            baseSalary = -1.0,
            overtimeHours = 0.0,
            overtimeRate = 0.0,
            deductions = 0.0,
        )
        assertIs<Result.Error>(result)
        assertEquals("baseSalary", ((result as Result.Error).exception as ValidationException).field)
    }

    @Test
    fun `calculatePayroll_negativeOvertimeHours_returnsValidationError`() = runTest {
        val result = CalculatePayrollUseCase()(
            employeeId = "emp-01",
            periodStart = "2026-03-01",
            periodEnd = "2026-03-31",
            baseSalary = 1000.0,
            overtimeHours = -1.0,
            overtimeRate = 20.0,
            deductions = 0.0,
        )
        assertIs<Result.Error>(result)
        assertEquals("overtimeHours", ((result as Result.Error).exception as ValidationException).field)
    }

    @Test
    fun `calculatePayroll_negativeOvertimeRate_returnsValidationError`() = runTest {
        val result = CalculatePayrollUseCase()(
            employeeId = "emp-01",
            periodStart = "2026-03-01",
            periodEnd = "2026-03-31",
            baseSalary = 1000.0,
            overtimeHours = 0.0,
            overtimeRate = -5.0,
            deductions = 0.0,
        )
        assertIs<Result.Error>(result)
        assertEquals("overtimeRate", ((result as Result.Error).exception as ValidationException).field)
    }

    @Test
    fun `calculatePayroll_negativeDeductions_returnsValidationError`() = runTest {
        val result = CalculatePayrollUseCase()(
            employeeId = "emp-01",
            periodStart = "2026-03-01",
            periodEnd = "2026-03-31",
            baseSalary = 1000.0,
            overtimeHours = 0.0,
            overtimeRate = 0.0,
            deductions = -50.0,
        )
        assertIs<Result.Error>(result)
        assertEquals("deductions", ((result as Result.Error).exception as ValidationException).field)
    }

    @Test
    fun `calculatePayroll_zeroOvertimeAndDeductions_netEqualsBase`() = runTest {
        val result = CalculatePayrollUseCase()(
            employeeId = "emp-01",
            periodStart = "2026-03-01",
            periodEnd = "2026-03-31",
            baseSalary = 2500.0,
            overtimeHours = 0.0,
            overtimeRate = 0.0,
            deductions = 0.0,
        )
        assertIs<Result.Success<*>>(result)
        assertEquals(2500.0, (result as Result.Success).data.netPay, 0.001)
    }

    @Test
    fun `calculatePayroll_deductionsExceedGross_netPayIsNegative`() = runTest {
        val result = CalculatePayrollUseCase()(
            employeeId = "emp-01",
            periodStart = "2026-03-01",
            periodEnd = "2026-03-31",
            baseSalary = 100.0,
            overtimeHours = 0.0,
            overtimeRate = 0.0,
            deductions = 200.0,
        )
        assertIs<Result.Success<*>>(result)
        // No business rule forbids negative net pay — the use case allows it
        assertEquals(-100.0, (result as Result.Success).data.netPay, 0.001)
    }
}

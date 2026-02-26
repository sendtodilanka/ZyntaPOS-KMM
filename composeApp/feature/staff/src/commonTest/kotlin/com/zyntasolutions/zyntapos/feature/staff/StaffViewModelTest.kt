package com.zyntasolutions.zyntapos.feature.staff

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.model.AttendanceStatus
import com.zyntasolutions.zyntapos.domain.model.AttendanceSummary
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveType
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollStatus
import com.zyntasolutions.zyntapos.domain.model.PayrollSummary
import com.zyntasolutions.zyntapos.domain.model.SalaryType
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository
import com.zyntasolutions.zyntapos.domain.repository.PayrollRepository
import com.zyntasolutions.zyntapos.domain.repository.ShiftRepository
import com.zyntasolutions.zyntapos.domain.usecase.staff.ApproveLeaveUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.ClockInUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.ClockOutUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.DeleteEmployeeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.DeleteShiftScheduleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GeneratePayrollUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetAttendanceHistoryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetAttendanceSummaryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetEmployeeByIdUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetEmployeesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetLeaveHistoryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetPayrollHistoryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetPendingLeaveRequestsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetShiftScheduleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetTodayAttendanceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.ProcessPayrollPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.RejectLeaveUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.SaveEmployeeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.SaveShiftScheduleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.SubmitLeaveRequestUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.Role
import kotlinx.datetime.Instant

// ─────────────────────────────────────────────────────────────────────────────
// StaffViewModelTest
// Tests StaffViewModel MVI state transitions using hand-rolled fakes.
// Only covers the most important intents from each section.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val storeId = "store-001"
    private val currentUserId = "user-001"

    private val fakeAuthRepository = object : AuthRepository {
        private val _session = MutableStateFlow<User?>(
            User(
                id = "user-001", name = "Test User", email = "test@zynta.com",
                role = Role.CASHIER, storeId = "store-001", isActive = true,
                pinHash = null, createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            )
        )
        override fun getSession(): Flow<User?> = _session
        override suspend fun login(email: String, password: String): Result<User> =
            Result.Success(_session.value!!)
        override suspend fun logout() { _session.value = null }
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> =
            Result.Success(Unit)
    }
    private val now = System.currentTimeMillis()
    private val today = "2026-02-25"

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private val testEmployee = Employee(
        id = "emp-001",
        storeId = storeId,
        firstName = "Jane",
        lastName = "Doe",
        position = "Cashier",
        hireDate = "2024-01-10",
        salary = 3000.0,
        salaryType = SalaryType.MONTHLY,
        createdAt = now,
        updatedAt = now,
    )

    private val testAttendanceRecord = AttendanceRecord(
        id = "att-001",
        employeeId = "emp-001",
        clockIn = "${today}T09:00:00",
        clockOut = null,
        status = AttendanceStatus.PRESENT,
        createdAt = now,
        updatedAt = now,
    )

    private val testLeaveRecord = LeaveRecord(
        id = "leave-001",
        employeeId = "emp-001",
        leaveType = LeaveType.ANNUAL,
        startDate = "2026-03-01",
        endDate = "2026-03-05",
        reason = "Vacation",
        status = LeaveStatus.PENDING,
        createdAt = now,
        updatedAt = now,
    )

    private val testShift = ShiftSchedule(
        id = "shift-001",
        employeeId = "emp-001",
        storeId = storeId,
        shiftDate = today,
        startTime = "09:00",
        endTime = "17:00",
        createdAt = now,
        updatedAt = now,
    )

    private val testPayrollRecord = PayrollRecord(
        id = "payroll-001",
        employeeId = "emp-001",
        periodStart = "2026-02-01",
        periodEnd = "2026-02-28",
        baseSalary = 3000.0,
        netPay = 2700.0,
        deductions = 300.0,
        status = PayrollStatus.PENDING,
        createdAt = now,
        updatedAt = now,
    )

    // ── Fake backing state ────────────────────────────────────────────────────

    private val employeesFlow = MutableStateFlow<List<Employee>>(emptyList())
    private val pendingLeaveFlow = MutableStateFlow<List<LeaveRecord>>(emptyList())
    private val attendanceHistoryFlow = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    private val shiftsFlow = MutableStateFlow<List<ShiftSchedule>>(emptyList())
    private val payrollFlow = MutableStateFlow<List<PayrollRecord>>(emptyList())

    private var shouldFailSaveEmployee = false
    private var shouldFailDeleteEmployee = false
    private var shouldFailClockIn = false
    private var openRecord: AttendanceRecord? = null

    // ── Fake EmployeeRepository ───────────────────────────────────────────────

    private val fakeEmployeeRepository = object : EmployeeRepository {
        override fun getActive(storeId: String): Flow<List<Employee>> =
            employeesFlow.map { list -> list.filter { it.isActive } }

        override fun getAll(storeId: String): Flow<List<Employee>> = employeesFlow

        override suspend fun getById(id: String): Result<Employee> {
            val e = employeesFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Employee '$id' not found"))
            return Result.Success(e)
        }

        override suspend fun getByUserId(userId: String): Result<Employee?> =
            Result.Success(employeesFlow.value.firstOrNull { it.userId == userId })

        override suspend fun search(storeId: String, query: String): Result<List<Employee>> =
            Result.Success(
                employeesFlow.value.filter { e ->
                    e.firstName.contains(query, true) || e.lastName.contains(query, true)
                }
            )

        override suspend fun insert(employee: Employee): Result<Unit> {
            if (shouldFailSaveEmployee) return Result.Error(DatabaseException("Insert failed"))
            employeesFlow.value = employeesFlow.value + employee
            return Result.Success(Unit)
        }

        override suspend fun update(employee: Employee): Result<Unit> {
            if (shouldFailSaveEmployee) return Result.Error(DatabaseException("Update failed"))
            val idx = employeesFlow.value.indexOfFirst { it.id == employee.id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = employeesFlow.value.toMutableList().also { it[idx] = employee }
            employeesFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun setActive(id: String, isActive: Boolean): Result<Unit> {
            val idx = employeesFlow.value.indexOfFirst { it.id == id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = employeesFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(isActive = isActive)
            employeesFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun delete(id: String): Result<Unit> {
            if (shouldFailDeleteEmployee) return Result.Error(DatabaseException("Delete failed"))
            employeesFlow.value = employeesFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }
    }

    // ── Fake AttendanceRepository ─────────────────────────────────────────────

    private val fakeAttendanceRepository = object : AttendanceRepository {
        override fun getByEmployee(employeeId: String): Flow<List<AttendanceRecord>> =
            attendanceHistoryFlow.map { list -> list.filter { it.employeeId == employeeId } }

        override suspend fun getByEmployeeForPeriod(employeeId: String, from: String, to: String): Result<List<AttendanceRecord>> =
            Result.Success(attendanceHistoryFlow.value.filter { it.employeeId == employeeId })

        override suspend fun getOpenRecord(employeeId: String): Result<AttendanceRecord?> =
            if (shouldFailClockIn) Result.Error(DatabaseException("DB error"))
            else Result.Success(openRecord?.takeIf { it.employeeId == employeeId })

        override suspend fun getTodayForStore(storeId: String, todayPrefix: String): Result<List<AttendanceRecord>> =
            Result.Success(attendanceHistoryFlow.value)

        override suspend fun insert(record: AttendanceRecord): Result<Unit> {
            attendanceHistoryFlow.value = attendanceHistoryFlow.value + record
            openRecord = record
            return Result.Success(Unit)
        }

        override suspend fun clockOut(id: String, clockOut: String, totalHours: Double, overtimeHours: Double, updatedAt: Long): Result<Unit> {
            val idx = attendanceHistoryFlow.value.indexOfFirst { it.id == id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = attendanceHistoryFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(clockOut = clockOut, totalHours = totalHours, overtimeHours = overtimeHours)
            attendanceHistoryFlow.value = updated
            openRecord = null
            return Result.Success(Unit)
        }

        override suspend fun updateNotes(id: String, notes: String, updatedAt: Long): Result<Unit> =
            Result.Success(Unit)

        override suspend fun getSummary(employeeId: String, from: String, to: String): Result<AttendanceSummary> =
            Result.Success(
                AttendanceSummary(
                    employeeId = employeeId,
                    totalDays = 20,
                    presentDays = 18,
                    absentDays = 2,
                    lateDays = 1,
                    leaveDays = 0,
                    totalHours = 144.0,
                    overtimeHours = 4.0,
                )
            )
    }

    // ── Fake LeaveRepository ──────────────────────────────────────────────────

    private val leaveRecordsFlow = MutableStateFlow<List<LeaveRecord>>(emptyList())
    private val fakeLeaveRepository = object : LeaveRepository {
        override fun getByEmployee(employeeId: String): Flow<List<LeaveRecord>> =
            leaveRecordsFlow.map { list -> list.filter { it.employeeId == employeeId } }

        override fun getPendingForStore(storeId: String): Flow<List<LeaveRecord>> =
            leaveRecordsFlow.map { list -> list.filter { it.status == LeaveStatus.PENDING } }

        override suspend fun getById(id: String): Result<LeaveRecord> {
            val r = leaveRecordsFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Leave not found"))
            return Result.Success(r)
        }

        override suspend fun getByEmployeeAndPeriod(employeeId: String, from: String, to: String): Result<List<LeaveRecord>> =
            Result.Success(leaveRecordsFlow.value.filter { it.employeeId == employeeId })

        override suspend fun insert(record: LeaveRecord): Result<Unit> {
            leaveRecordsFlow.value = leaveRecordsFlow.value + record
            return Result.Success(Unit)
        }

        override suspend fun updateStatus(
            id: String,
            status: LeaveStatus,
            decidedBy: String,
            decidedAt: Long,
            rejectionReason: String?,
            updatedAt: Long,
        ): Result<Unit> {
            val idx = leaveRecordsFlow.value.indexOfFirst { it.id == id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = leaveRecordsFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(status = status, approvedBy = decidedBy, rejectionReason = rejectionReason)
            leaveRecordsFlow.value = updated
            return Result.Success(Unit)
        }
    }

    // ── Fake ShiftRepository ──────────────────────────────────────────────────

    private val fakeShiftRepository = object : ShiftRepository {
        override fun getWeeklySchedule(storeId: String, weekStart: String, weekEnd: String): Flow<List<ShiftSchedule>> =
            shiftsFlow

        override fun getByEmployee(employeeId: String): Flow<List<ShiftSchedule>> =
            shiftsFlow.map { list -> list.filter { it.employeeId == employeeId } }

        override suspend fun getByEmployeeAndDate(employeeId: String, date: String): Result<ShiftSchedule?> =
            Result.Success(shiftsFlow.value.firstOrNull { it.employeeId == employeeId && it.shiftDate == date })

        override suspend fun getByStoreAndDate(storeId: String, date: String): Result<List<ShiftSchedule>> =
            Result.Success(shiftsFlow.value.filter { it.storeId == storeId && it.shiftDate == date })

        override suspend fun insert(shift: ShiftSchedule): Result<Unit> {
            shiftsFlow.value = shiftsFlow.value + shift
            return Result.Success(Unit)
        }

        override suspend fun update(shift: ShiftSchedule): Result<Unit> {
            val idx = shiftsFlow.value.indexOfFirst { it.id == shift.id }
            if (idx == -1) return Result.Error(DatabaseException("Shift not found"))
            val updated = shiftsFlow.value.toMutableList().also { it[idx] = shift }
            shiftsFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun upsert(shift: ShiftSchedule): Result<Unit> {
            val idx = shiftsFlow.value.indexOfFirst { it.id == shift.id }
            val list = shiftsFlow.value.toMutableList()
            if (idx == -1) list.add(shift) else list[idx] = shift
            shiftsFlow.value = list
            return Result.Success(Unit)
        }

        override suspend fun deleteById(id: String): Result<Unit> {
            shiftsFlow.value = shiftsFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }

        override suspend fun deleteByEmployeeAndDate(employeeId: String, date: String): Result<Unit> {
            shiftsFlow.value = shiftsFlow.value.filter { !(it.employeeId == employeeId && it.shiftDate == date) }
            return Result.Success(Unit)
        }
    }

    // ── Fake PayrollRepository ────────────────────────────────────────────────

    private val fakePayrollRepository = object : PayrollRepository {
        override fun getByEmployee(employeeId: String): Flow<List<PayrollRecord>> =
            payrollFlow.map { list -> list.filter { it.employeeId == employeeId } }

        override suspend fun getByPeriodForStore(storeId: String, period: String): Result<List<PayrollRecord>> =
            Result.Success(payrollFlow.value)

        override suspend fun getByEmployeeAndPeriod(employeeId: String, periodStart: String): Result<PayrollRecord?> =
            Result.Success(payrollFlow.value.firstOrNull { it.employeeId == employeeId && it.periodStart == periodStart })

        override suspend fun getById(id: String): Result<PayrollRecord> {
            val r = payrollFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Payroll not found"))
            return Result.Success(r)
        }

        override suspend fun getPending(storeId: String): Result<List<PayrollRecord>> =
            Result.Success(payrollFlow.value.filter { it.status == PayrollStatus.PENDING })

        override suspend fun insert(record: PayrollRecord): Result<Unit> {
            payrollFlow.value = payrollFlow.value + record
            return Result.Success(Unit)
        }

        override suspend fun markPaid(id: String, paidAt: Long, paymentRef: String?, updatedAt: Long): Result<Unit> {
            val idx = payrollFlow.value.indexOfFirst { it.id == id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = payrollFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(status = PayrollStatus.PAID, paidAt = paidAt, paymentRef = paymentRef)
            payrollFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun updateCalculation(id: String, baseSalary: Double, overtimePay: Double, commission: Double, deductions: Double, netPay: Double, updatedAt: Long): Result<Unit> =
            Result.Success(Unit)

        override suspend fun getSummary(storeId: String, period: String): Result<PayrollSummary> =
            Result.Success(
                PayrollSummary(
                    period = period,
                    totalEmployees = 1,
                    totalGrossPay = 3000.0,
                    totalDeductions = 300.0,
                    totalNetPay = 2700.0,
                    pendingCount = 1,
                    paidCount = 0,
                )
            )
    }

    // ── Use cases wired to fakes ──────────────────────────────────────────────

    private val getEmployeesUseCase = GetEmployeesUseCase(fakeEmployeeRepository)
    private val getEmployeeByIdUseCase = GetEmployeeByIdUseCase(fakeEmployeeRepository)
    private val saveEmployeeUseCase = SaveEmployeeUseCase(fakeEmployeeRepository)
    private val deleteEmployeeUseCase = DeleteEmployeeUseCase(fakeEmployeeRepository)
    private val clockInUseCase = ClockInUseCase(fakeAttendanceRepository)
    private val clockOutUseCase = ClockOutUseCase(fakeAttendanceRepository)
    private val getTodayAttendanceUseCase = GetTodayAttendanceUseCase(fakeAttendanceRepository)
    private val getAttendanceHistoryUseCase = GetAttendanceHistoryUseCase(fakeAttendanceRepository)
    private val getPendingLeaveUseCase = GetPendingLeaveRequestsUseCase(fakeLeaveRepository)
    private val submitLeaveUseCase = SubmitLeaveRequestUseCase(fakeLeaveRepository)
    private val approveLeaveUseCase = ApproveLeaveUseCase(fakeLeaveRepository)
    private val rejectLeaveUseCase = RejectLeaveUseCase(fakeLeaveRepository)
    private val getShiftScheduleUseCase = GetShiftScheduleUseCase(fakeShiftRepository)
    private val saveShiftUseCase = SaveShiftScheduleUseCase(fakeShiftRepository)
    private val deleteShiftUseCase = DeleteShiftScheduleUseCase(fakeShiftRepository)
    private val generatePayrollUseCase = GeneratePayrollUseCase(fakePayrollRepository, fakeAttendanceRepository)
    private val processPaymentUseCase = ProcessPayrollPaymentUseCase(fakePayrollRepository)
    private val getPayrollHistoryUseCase = GetPayrollHistoryUseCase(fakePayrollRepository)
    private val getAttendanceSummaryUseCase = GetAttendanceSummaryUseCase(fakeAttendanceRepository)
    private val getLeaveHistoryUseCase = GetLeaveHistoryUseCase(fakeLeaveRepository)

    private lateinit var viewModel: StaffViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        employeesFlow.value = emptyList()
        pendingLeaveFlow.value = emptyList()
        leaveRecordsFlow.value = emptyList()
        attendanceHistoryFlow.value = emptyList()
        shiftsFlow.value = emptyList()
        payrollFlow.value = emptyList()
        shouldFailSaveEmployee = false
        shouldFailDeleteEmployee = false
        shouldFailClockIn = false
        openRecord = null

        viewModel = StaffViewModel(
            authRepository = fakeAuthRepository,
            payrollRepository = fakePayrollRepository,
            shiftRepository = fakeShiftRepository,
            getEmployeesUseCase = getEmployeesUseCase,
            getEmployeeByIdUseCase = getEmployeeByIdUseCase,
            saveEmployeeUseCase = saveEmployeeUseCase,
            deleteEmployeeUseCase = deleteEmployeeUseCase,
            clockInUseCase = clockInUseCase,
            clockOutUseCase = clockOutUseCase,
            getTodayAttendanceUseCase = getTodayAttendanceUseCase,
            getAttendanceHistoryUseCase = getAttendanceHistoryUseCase,
            getPendingLeaveUseCase = getPendingLeaveUseCase,
            submitLeaveUseCase = submitLeaveUseCase,
            approveLeaveUseCase = approveLeaveUseCase,
            rejectLeaveUseCase = rejectLeaveUseCase,
            getShiftScheduleUseCase = getShiftScheduleUseCase,
            saveShiftUseCase = saveShiftUseCase,
            deleteShiftUseCase = deleteShiftUseCase,
            generatePayrollUseCase = generatePayrollUseCase,
            processPaymentUseCase = processPaymentUseCase,
            getPayrollHistoryUseCase = getPayrollHistoryUseCase,
            getAttendanceSummaryUseCase = getAttendanceSummaryUseCase,
            getLeaveHistoryUseCase = getLeaveHistoryUseCase,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state starts on EMPLOYEES tab with empty lists and no error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(StaffTab.EMPLOYEES, state.activeTab)
        assertTrue(state.employees.isEmpty())
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    @Test
    fun `SwitchTab updates activeTab in state`() = runTest {
        viewModel.dispatch(StaffIntent.SwitchTab(StaffTab.ATTENDANCE))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(StaffTab.ATTENDANCE, viewModel.state.value.activeTab)

        viewModel.dispatch(StaffIntent.SwitchTab(StaffTab.PAYROLL))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(StaffTab.PAYROLL, viewModel.state.value.activeTab)
    }

    // ── Employee list ─────────────────────────────────────────────────────────

    @Test
    fun `employees from repository are reflected in state reactively`() = runTest {
        employeesFlow.value = listOf(testEmployee)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.employees.size)
        assertEquals("Jane", viewModel.state.value.employees.first().firstName)
    }

    @Test
    fun `SearchEmployees updates searchQuery in state`() = runTest {
        viewModel.dispatch(StaffIntent.SearchEmployees("Jane"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Jane", viewModel.state.value.searchQuery)
    }

    // ── Employee CRUD ─────────────────────────────────────────────────────────

    @Test
    fun `SaveEmployee with valid form creates new employee and navigates to list`() = runTest {
        viewModel.dispatch(StaffIntent.UpdateEmployeeField("firstName", "Alice"))
        viewModel.dispatch(StaffIntent.UpdateEmployeeField("lastName", "Smith"))
        viewModel.dispatch(StaffIntent.UpdateEmployeeField("position", "Manager"))
        viewModel.dispatch(StaffIntent.UpdateEmployeeField("hireDate", "2026-01-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(StaffIntent.SaveEmployee)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is StaffEffect.NavigateToEmployeeList)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, employeesFlow.value.size)
        assertEquals("Alice", employeesFlow.value.first().firstName)
    }

    @Test
    fun `SaveEmployee with blank firstName sets validation error`() = runTest {
        viewModel.dispatch(StaffIntent.UpdateEmployeeField("lastName", "Smith"))
        viewModel.dispatch(StaffIntent.UpdateEmployeeField("position", "Cashier"))
        viewModel.dispatch(StaffIntent.SaveEmployee)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.employeeForm.validationErrors["firstName"])
        assertTrue(employeesFlow.value.isEmpty())
    }

    @Test
    fun `DeleteEmployee removes employee from list and navigates to list`() = runTest {
        employeesFlow.value = listOf(testEmployee)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(StaffIntent.DeleteEmployee(testEmployee.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is StaffEffect.NavigateToEmployeeList)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(employeesFlow.value.isEmpty())
    }

    @Test
    fun `SelectEmployee with null id opens new-employee form`() = runTest {
        viewModel.dispatch(StaffIntent.SelectEmployee(null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.selectedEmployee)
        assertFalse(viewModel.state.value.employeeForm.isEditing)
    }

    // ── Attendance ────────────────────────────────────────────────────────────

    @Test
    fun `ClockIn on first clock-in inserts attendance record`() = runTest {
        viewModel.dispatch(StaffIntent.ClockIn("emp-001", storeId, "${today}T09:00:00"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, attendanceHistoryFlow.value.size)
        assertNull(attendanceHistoryFlow.value.first().clockOut)
    }

    @Test
    fun `ClockIn when already clocked in sets error in state`() = runTest {
        // Simulate an already open record
        openRecord = testAttendanceRecord
        viewModel.dispatch(StaffIntent.ClockIn("emp-001", storeId, "${today}T09:30:00"))
        testDispatcher.scheduler.advanceUntilIdle()

        // The use case returns a ValidationException because openRecord is already set
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("already clocked in"))
    }

    @Test
    fun `ClockOut closes the open attendance record`() = runTest {
        // Clock in first
        attendanceHistoryFlow.value = listOf(testAttendanceRecord)
        openRecord = testAttendanceRecord

        viewModel.dispatch(
            StaffIntent.ClockOut(
                attendanceId = testAttendanceRecord.id,
                employeeId = testAttendanceRecord.employeeId,
                clockOutTime = "${today}T17:00:00",
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val record = attendanceHistoryFlow.value.first()
        assertNotNull(record.clockOut)
        assertEquals("${today}T17:00:00", record.clockOut)
        assertNull(openRecord)
    }

    // ── Leave Management ──────────────────────────────────────────────────────

    @Test
    fun `pending leave requests from repository are reflected in state reactively`() = runTest {
        leaveRecordsFlow.value = listOf(testLeaveRecord)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.pendingLeaveRequests.size)
        assertEquals("leave-001", viewModel.state.value.pendingLeaveRequests.first().id)
    }

    @Test
    fun `ShowLeaveForm sets showLeaveForm to true and HideLeaveForm clears it`() = runTest {
        viewModel.dispatch(StaffIntent.ShowLeaveForm)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.showLeaveForm)

        viewModel.dispatch(StaffIntent.HideLeaveForm)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.showLeaveForm)
    }

    @Test
    fun `ApproveLeave updates leave status to APPROVED and sets successMessage`() = runTest {
        leaveRecordsFlow.value = listOf(testLeaveRecord)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(
            StaffIntent.ApproveLeave(
                requestId = testLeaveRecord.id,
                approverId = currentUserId,
                approvedAt = now,
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)
        assertEquals(LeaveStatus.APPROVED, leaveRecordsFlow.value.first().status)
    }

    @Test
    fun `RejectLeave updates leave status to REJECTED and sets successMessage`() = runTest {
        leaveRecordsFlow.value = listOf(testLeaveRecord)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(
            StaffIntent.RejectLeave(
                requestId = testLeaveRecord.id,
                reason = "Insufficient staffing",
                rejectedAt = now,
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)
        assertEquals(LeaveStatus.REJECTED, leaveRecordsFlow.value.first().status)
        assertEquals("Insufficient staffing", leaveRecordsFlow.value.first().rejectionReason)
    }

    // ── Payroll ───────────────────────────────────────────────────────────────

    @Test
    fun `LoadPayroll loads records and summary for a period`() = runTest {
        payrollFlow.value = listOf(testPayrollRecord)
        viewModel.dispatch(StaffIntent.LoadPayroll(storeId, "2026-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.payrollRecords.size)
        assertNotNull(viewModel.state.value.payrollSummary)
        assertEquals("2026-02", viewModel.state.value.payrollSummary?.period)
    }

    @Test
    fun `ProcessPayment on pending record marks it PAID and sets successMessage`() = runTest {
        payrollFlow.value = listOf(testPayrollRecord)
        viewModel.dispatch(StaffIntent.LoadPayroll(storeId, "2026-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(
            StaffIntent.ProcessPayment(
                payrollId = testPayrollRecord.id,
                paidAt = now,
                paymentRef = "TXN-12345",
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)
        assertEquals(PayrollStatus.PAID, payrollFlow.value.first().status)
        assertEquals("TXN-12345", payrollFlow.value.first().paymentRef)
    }

    // ── UI Feedback ───────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears error in state`() = runTest {
        // Trigger an error by clocking in with an already-open record
        openRecord = testAttendanceRecord
        viewModel.dispatch(StaffIntent.ClockIn("emp-001", storeId, "${today}T09:30:00"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.dispatch(StaffIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `DismissSuccess clears successMessage in state`() = runTest {
        employeesFlow.value = listOf(testEmployee)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(StaffIntent.DeleteEmployee(testEmployee.id))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.successMessage)

        viewModel.dispatch(StaffIntent.DismissSuccess)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.successMessage)
    }
}

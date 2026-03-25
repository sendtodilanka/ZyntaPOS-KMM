package com.zyntasolutions.zyntapos.feature.staff

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.model.AttendanceStatus
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
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
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostPayrollJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.ProcessPayrollPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.RejectLeaveUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.SaveEmployeeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.SaveShiftScheduleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.SubmitLeaveRequestUseCase
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Root ViewModel for the Staff & HR feature (Sprints 8–12).
 *
 * ### Reactive bindings (init)
 * - Employees are observed reactively via [GetEmployeesUseCase].
 * - Pending leave requests are observed reactively via [GetPendingLeaveRequestsUseCase].
 * - Attendance history is observed reactively, scoped to [_historyEmployeeId].
 * - Weekly shifts are observed reactively, scoped to the current week window.
 *
 * @param authRepository Provides the active auth session for resolving storeId and currentUserId.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModel(
    private val authRepository: AuthRepository,
    private val payrollRepository: PayrollRepository,
    private val shiftRepository: ShiftRepository,
    private val getEmployeesUseCase: GetEmployeesUseCase,
    private val getEmployeeByIdUseCase: GetEmployeeByIdUseCase,
    private val saveEmployeeUseCase: SaveEmployeeUseCase,
    private val deleteEmployeeUseCase: DeleteEmployeeUseCase,
    private val clockInUseCase: ClockInUseCase,
    private val clockOutUseCase: ClockOutUseCase,
    private val getTodayAttendanceUseCase: GetTodayAttendanceUseCase,
    private val getAttendanceHistoryUseCase: GetAttendanceHistoryUseCase,
    private val getPendingLeaveUseCase: GetPendingLeaveRequestsUseCase,
    private val submitLeaveUseCase: SubmitLeaveRequestUseCase,
    private val approveLeaveUseCase: ApproveLeaveUseCase,
    private val rejectLeaveUseCase: RejectLeaveUseCase,
    private val getShiftScheduleUseCase: GetShiftScheduleUseCase,
    private val saveShiftUseCase: SaveShiftScheduleUseCase,
    private val deleteShiftUseCase: DeleteShiftScheduleUseCase,
    private val generatePayrollUseCase: GeneratePayrollUseCase,
    private val processPaymentUseCase: ProcessPayrollPaymentUseCase,
    private val postPayrollJournalEntryUseCase: PostPayrollJournalEntryUseCase,
    private val getPayrollHistoryUseCase: GetPayrollHistoryUseCase,
    private val getAttendanceSummaryUseCase: GetAttendanceSummaryUseCase,
    private val getLeaveHistoryUseCase: GetLeaveHistoryUseCase,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<StaffState, StaffIntent, StaffEffect>(StaffState()) {

    private var storeId: String = ""
    private var currentUserId: String = "unknown"

    // ── Reactive filter states ─────────────────────────────────────────────

    private val _weekStart = MutableStateFlow("")
    private val _weekEnd = MutableStateFlow("")
    private val _historyEmployeeId = MutableStateFlow<String?>(null)

    init {
        analytics.logScreenView("Staff", "StaffViewModel")
        viewModelScope.launch {
            val session = authRepository.getSession().first()
            storeId = session?.storeId ?: ""
            currentUserId = session?.id ?: "unknown"
            observeEmployees()
            observePendingLeave()
            observeAttendanceHistory()
            observeShifts()
        }
    }

    private fun observeEmployees() {
        getEmployeesUseCase(storeId)
            .onEach { list -> updateState { copy(employees = list) } }
            .launchIn(viewModelScope)
    }

    private fun observePendingLeave() {
        getPendingLeaveUseCase(storeId)
            .onEach { list -> updateState { copy(pendingLeaveRequests = list) } }
            .launchIn(viewModelScope)
    }

    private fun observeAttendanceHistory() {
        _historyEmployeeId
            .filterNotNull()
            .flatMapLatest { empId -> getAttendanceHistoryUseCase(empId) }
            .onEach { list -> updateState { copy(attendanceHistory = list) } }
            .launchIn(viewModelScope)
    }

    private fun observeShifts() {
        combine(_weekStart, _weekEnd) { start, end -> start to end }
            .flatMapLatest { (start, end) ->
                if (start.isBlank() || end.isBlank()) {
                    shiftRepository.getWeeklySchedule(storeId, "1970-01-01", "1970-01-01")
                } else {
                    getShiftScheduleUseCase(storeId, start, end)
                }
            }
            .onEach { shifts -> updateState { copy(weeklyShifts = shifts) } }
            .launchIn(viewModelScope)
    }

    // ── Intent dispatch ───────────────────────────────────────────────────

    override suspend fun handleIntent(intent: StaffIntent) {
        when (intent) {
            is StaffIntent.SwitchTab -> updateState { copy(activeTab = intent.tab) }

            // Employee list
            is StaffIntent.LoadEmployees -> Unit // driven by reactive flow in init
            is StaffIntent.SearchEmployees -> updateState { copy(searchQuery = intent.query) }
            is StaffIntent.ToggleShowInactive -> updateState { copy(showInactive = intent.show) }

            // Employee detail
            is StaffIntent.SelectEmployee -> loadEmployee(intent.employeeId)
            is StaffIntent.BackToEmployeeList -> {
                updateState { copy(selectedEmployee = null, employeeForm = EmployeeFormState()) }
                sendEffect(StaffEffect.NavigateToEmployeeList)
            }
            is StaffIntent.UpdateEmployeeField -> updateEmployeeField(intent.field, intent.value)
            is StaffIntent.UpdateEmployeeSalaryType -> updateState {
                copy(employeeForm = employeeForm.copy(salaryType = intent.salaryType))
            }
            is StaffIntent.ToggleEmployeeActive -> updateState {
                copy(employeeForm = employeeForm.copy(isActive = !employeeForm.isActive))
            }
            is StaffIntent.SaveEmployee -> saveEmployee()
            is StaffIntent.DeleteEmployee -> deleteEmployee(intent.employeeId)

            // Attendance
            is StaffIntent.LoadTodayAttendance -> loadTodayAttendance(intent.storeId, intent.today)
            is StaffIntent.LoadAttendanceHistory -> {
                // Reactive: update the scoped employee ID to trigger the Flow
                _historyEmployeeId.value = intent.employeeId
            }
            is StaffIntent.ClockIn -> clockIn(intent.employeeId, intent.storeId, intent.clockInTime)
            is StaffIntent.ClockOut -> clockOut(intent.employeeId, intent.clockOutTime)

            // Leave
            is StaffIntent.LoadPendingLeave -> Unit // driven by reactive observePendingLeave()
            is StaffIntent.ShowLeaveForm -> updateState { copy(showLeaveForm = true) }
            is StaffIntent.HideLeaveForm -> updateState {
                copy(showLeaveForm = false, leaveForm = LeaveFormState())
            }
            is StaffIntent.UpdateLeaveFormField -> updateLeaveField(intent.field, intent.value)
            is StaffIntent.UpdateLeaveType -> updateState {
                copy(leaveForm = leaveForm.copy(leaveType = intent.leaveType))
            }
            is StaffIntent.SubmitLeaveRequest -> submitLeaveRequest()
            is StaffIntent.ApproveLeave -> approveLeave(
                intent.requestId, intent.approverId, intent.approvedAt
            )
            is StaffIntent.RejectLeave -> rejectLeave(intent.requestId, intent.reason, intent.rejectedAt)

            // Shifts
            is StaffIntent.LoadWeeklyShifts -> {
                _weekStart.value = intent.weekStart
                _weekEnd.value = intent.weekEnd
                updateState { copy(weekStart = intent.weekStart, weekEnd = intent.weekEnd) }
            }
            is StaffIntent.ShowShiftForm -> updateState { copy(showShiftForm = true) }
            is StaffIntent.HideShiftForm -> updateState {
                copy(showShiftForm = false, shiftForm = ShiftFormState())
            }
            is StaffIntent.UpdateShiftField -> updateShiftField(intent.field, intent.value)
            is StaffIntent.SaveShift -> saveShift()
            is StaffIntent.DeleteShift -> deleteShift(intent.shiftId)

            // Payroll
            is StaffIntent.LoadPayroll -> loadPayroll(intent.storeId, intent.period)
            is StaffIntent.SelectPayroll -> selectPayroll(intent.payrollId)
            is StaffIntent.GeneratePayroll -> generatePayroll(
                intent.employeeId, intent.periodStart, intent.periodEnd
            )
            is StaffIntent.ProcessPayment -> processPayment(
                intent.payrollId, intent.paidAt, intent.paymentRef
            )

            // History / Summary
            is StaffIntent.LoadPayrollHistory -> loadPayrollHistory(intent.employeeId)
            is StaffIntent.LoadAttendanceSummary -> loadAttendanceSummary(
                intent.employeeId, intent.from, intent.to
            )
            is StaffIntent.LoadLeaveHistory -> loadLeaveHistory(intent.employeeId)

            // C3.4: Employee Roaming
            is StaffIntent.NavigateToEmployeeStores ->
                sendEffect(StaffEffect.NavigateToEmployeeStores(intent.employeeId))

            // UI
            is StaffIntent.DismissError -> updateState { copy(error = null) }
            is StaffIntent.DismissSuccess -> updateState { copy(successMessage = null) }
        }
    }

    // ── Employee handlers ─────────────────────────────────────────────────

    private suspend fun loadEmployee(employeeId: String?) {
        if (employeeId == null) {
            updateState { copy(selectedEmployee = null, employeeForm = EmployeeFormState()) }
            sendEffect(StaffEffect.NavigateToEmployeeDetail(null))
            return
        }
        updateState { copy(isLoading = true) }
        when (val result = getEmployeeByIdUseCase(employeeId)) {
            is Result.Success -> {
                val emp = result.data
                updateState {
                    copy(
                        isLoading = false,
                        selectedEmployee = emp,
                        employeeForm = emp.toFormState(),
                    )
                }
                sendEffect(StaffEffect.NavigateToEmployeeDetail(employeeId))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
            }
            is Result.Loading -> Unit
        }
    }

    private fun updateEmployeeField(field: String, value: String) {
        updateState {
            copy(
                employeeForm = when (field) {
                    "firstName" -> employeeForm.copy(firstName = value)
                    "lastName" -> employeeForm.copy(lastName = value)
                    "email" -> employeeForm.copy(email = value)
                    "phone" -> employeeForm.copy(phone = value)
                    "position" -> employeeForm.copy(position = value)
                    "department" -> employeeForm.copy(department = value)
                    "hireDate" -> employeeForm.copy(hireDate = value)
                    "salary" -> employeeForm.copy(salary = value)
                    "commissionRate" -> employeeForm.copy(commissionRate = value)
                    else -> employeeForm
                },
            )
        }
    }

    private suspend fun saveEmployee() {
        val form = currentState.employeeForm
        val isNew = form.id == null
        val now = Clock.System.now().toEpochMilliseconds()

        val employee = Employee(
            id = form.id ?: IdGenerator.newId(),
            storeId = storeId,
            firstName = form.firstName.trim(),
            lastName = form.lastName.trim(),
            email = form.email.trim().ifBlank { null },
            phone = form.phone.trim().ifBlank { null },
            position = form.position.trim(),
            department = form.department.trim().ifBlank { null },
            hireDate = form.hireDate.trim().ifBlank { "2000-01-01" },
            salary = form.salary.toDoubleOrNull(),
            salaryType = form.salaryType,
            commissionRate = form.commissionRate.toDoubleOrNull() ?: 0.0,
            isActive = form.isActive,
            createdAt = if (isNew) now else currentState.selectedEmployee?.createdAt ?: now,
            updatedAt = now,
        )

        updateState { copy(isLoading = true) }
        when (val result = saveEmployeeUseCase(employee, isNew)) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        selectedEmployee = null,
                        employeeForm = EmployeeFormState(),
                        successMessage = if (isNew) "Employee created." else "Employee updated.",
                    )
                }
                sendEffect(StaffEffect.NavigateToEmployeeList)
            }
            is Result.Error -> {
                val ex = result.exception
                if (ex is ValidationException && ex.field.isNotBlank()) {
                    updateState {
                        copy(
                            isLoading = false,
                            employeeForm = employeeForm.copy(
                                validationErrors = mapOf(ex.field to (ex.message ?: "Invalid value")),
                            ),
                        )
                    }
                } else {
                    updateState { copy(isLoading = false, error = ex.message) }
                }
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun deleteEmployee(employeeId: String) {
        updateState { copy(isLoading = true) }
        when (val result = deleteEmployeeUseCase(employeeId)) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        selectedEmployee = null,
                        employeeForm = EmployeeFormState(),
                        successMessage = "Employee deactivated.",
                    )
                }
                sendEffect(StaffEffect.NavigateToEmployeeList)
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    // ── Attendance handlers ───────────────────────────────────────────────

    private suspend fun loadTodayAttendance(storeId: String, today: String) {
        updateState { copy(isLoading = true, todayDate = today) }
        when (val result = getTodayAttendanceUseCase(storeId, today)) {
            is Result.Success -> updateState { copy(isLoading = false, todayAttendance = result.data) }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun clockIn(employeeId: String, storeId: String, clockInTime: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        val record = AttendanceRecord(
            id = IdGenerator.newId(),
            employeeId = employeeId,
            clockIn = clockInTime,
            status = AttendanceStatus.PRESENT,
            createdAt = now,
            updatedAt = now,
        )
        updateState { copy(isLoading = true) }
        when (val result = clockInUseCase(record)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, successMessage = "Clocked in.") }
                loadTodayAttendance(storeId, currentState.todayDate)
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun clockOut(employeeId: String, clockOutTime: String) {
        updateState { copy(isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = clockOutUseCase(employeeId, clockOutTime, now)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, successMessage = "Clocked out.") }
                loadTodayAttendance(storeId, currentState.todayDate)
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    // ── Leave handlers ────────────────────────────────────────────────────

    private fun updateLeaveField(field: String, value: String) {
        updateState {
            copy(
                leaveForm = when (field) {
                    "employeeId" -> leaveForm.copy(employeeId = value)
                    "startDate" -> leaveForm.copy(startDate = value)
                    "endDate" -> leaveForm.copy(endDate = value)
                    "reason" -> leaveForm.copy(reason = value)
                    else -> leaveForm
                },
            )
        }
    }

    private suspend fun submitLeaveRequest() {
        val form = currentState.leaveForm
        val now = Clock.System.now().toEpochMilliseconds()
        val record = LeaveRecord(
            id = IdGenerator.newId(),
            employeeId = form.employeeId,
            leaveType = form.leaveType,
            startDate = form.startDate,
            endDate = form.endDate,
            reason = form.reason.ifBlank { null },
            createdAt = now,
            updatedAt = now,
        )
        updateState { copy(isLoading = true) }
        when (val result = submitLeaveUseCase(record)) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        showLeaveForm = false,
                        leaveForm = LeaveFormState(),
                        successMessage = "Leave request submitted.",
                    )
                }
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun approveLeave(requestId: String, approverId: String, approvedAt: Long) {
        updateState { copy(isLoading = true) }
        when (val result = approveLeaveUseCase(requestId, approverId, approvedAt, approvedAt)) {
            is Result.Success -> updateState {
                copy(isLoading = false, successMessage = "Leave approved.")
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun rejectLeave(requestId: String, reason: String, rejectedAt: Long) {
        updateState { copy(isLoading = true) }
        when (val result = rejectLeaveUseCase(requestId, currentUserId, rejectedAt, reason, rejectedAt)) {
            is Result.Success -> updateState {
                copy(isLoading = false, successMessage = "Leave rejected.")
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    // ── Shift handlers ────────────────────────────────────────────────────

    private fun updateShiftField(field: String, value: String) {
        updateState {
            copy(
                shiftForm = when (field) {
                    "employeeId" -> shiftForm.copy(employeeId = value)
                    "shiftDate" -> shiftForm.copy(shiftDate = value)
                    "startTime" -> shiftForm.copy(startTime = value)
                    "endTime" -> shiftForm.copy(endTime = value)
                    "notes" -> shiftForm.copy(notes = value)
                    else -> shiftForm
                },
            )
        }
    }

    private suspend fun saveShift() {
        val form = currentState.shiftForm
        val now = Clock.System.now().toEpochMilliseconds()
        val shift = ShiftSchedule(
            id = form.id ?: IdGenerator.newId(),
            employeeId = form.employeeId,
            storeId = storeId,
            shiftDate = form.shiftDate,
            startTime = form.startTime,
            endTime = form.endTime,
            notes = form.notes.ifBlank { null },
            createdAt = now,
            updatedAt = now,
        )
        updateState { copy(isLoading = true) }
        when (val result = saveShiftUseCase(shift)) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        showShiftForm = false,
                        shiftForm = ShiftFormState(),
                        successMessage = "Shift saved.",
                    )
                }
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun deleteShift(shiftId: String) {
        updateState { copy(isLoading = true) }
        when (val result = deleteShiftUseCase(shiftId)) {
            is Result.Success -> updateState { copy(isLoading = false, successMessage = "Shift deleted.") }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    // ── Payroll handlers ──────────────────────────────────────────────────

    private suspend fun loadPayroll(storeId: String, period: String) {
        updateState { copy(isLoading = true, payrollPeriod = period) }
        when (val result = payrollRepository.getByPeriodForStore(storeId, period)) {
            is Result.Success -> {
                val records = result.data
                val summaryResult = payrollRepository.getSummary(storeId, period)
                updateState {
                    copy(
                        isLoading = false,
                        payrollRecords = records,
                        payrollSummary = if (summaryResult is Result.Success) summaryResult.data else null,
                    )
                }
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private fun selectPayroll(payrollId: String?) {
        val record = if (payrollId == null) null
        else currentState.payrollRecords.find { it.id == payrollId }
        updateState { copy(selectedPayroll = record) }
    }

    private suspend fun generatePayroll(employeeId: String, periodStart: String, periodEnd: String) {
        val employee = currentState.employees.find { it.id == employeeId } ?: return
        updateState { copy(isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = generatePayrollUseCase(
            employee = employee,
            periodStart = periodStart,
            periodEnd = periodEnd,
            recordId = IdGenerator.newId(),
            createdAt = now,
        )) {
            is Result.Success -> {
                updateState { copy(isLoading = false, successMessage = "Payroll generated.") }
                loadPayroll(storeId, currentState.payrollPeriod)
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun processPayment(payrollId: String, paidAt: Long, paymentRef: String?) {
        updateState { copy(isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = processPaymentUseCase(payrollId, paidAt, paymentRef, now)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, successMessage = "Payment recorded.") }
                loadPayroll(storeId, currentState.payrollPeriod)

                // ── Post payroll journal entry (best-effort — accounting failure must not block HR) ──
                @Suppress("TooGenericExceptionCaught")
                try {
                    val payrollResult = payrollRepository.getById(payrollId)
                    if (payrollResult is Result.Success) {
                        val record = payrollResult.data
                        val entryDate = Instant.fromEpochMilliseconds(paidAt)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date.toString()
                        val journalResult = postPayrollJournalEntryUseCase.execute(
                            storeId = storeId,
                            payrollId = payrollId,
                            grossPay = record.grossPay,
                            netPay = record.netPay,
                            deductions = record.deductions,
                            createdBy = currentUserId,
                            entryDate = entryDate,
                            now = now,
                        )
                        if (journalResult is Result.Error) {
                            ZyntaLogger.w(
                                "StaffViewModel",
                                "Payroll journal entry failed: ${journalResult.exception.message}",
                            )
                        }
                    } else if (payrollResult is Result.Error) {
                        ZyntaLogger.w(
                            "StaffViewModel",
                            "Could not fetch payroll record for journal entry: ${payrollResult.exception.message}",
                        )
                    }
                } catch (e: Exception) {
                    ZyntaLogger.w("StaffViewModel", "Payroll journal entry threw unexpectedly: ${e.message}")
                }
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    // ── History / Summary handlers ────────────────────────────────────────

    /**
     * Subscribes reactively to all payroll records for [employeeId].
     * Emits new list whenever the underlying payroll record changes.
     */
    private fun loadPayrollHistory(employeeId: String) {
        getPayrollHistoryUseCase(employeeId)
            .onEach { records -> updateState { copy(payrollHistory = records) } }
            .launchIn(viewModelScope)
    }

    /**
     * Fetches an [AttendanceSummary] for [employeeId] over the given date range
     * and stores the result in [StaffState.attendanceSummary].
     */
    private suspend fun loadAttendanceSummary(employeeId: String, from: String, to: String) {
        updateState { copy(isLoading = true) }
        when (val result = getAttendanceSummaryUseCase(employeeId, from, to)) {
            is Result.Success -> updateState { copy(isLoading = false, attendanceSummary = result.data) }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    /**
     * Subscribes reactively to all leave records for [employeeId].
     * Emits a new list whenever a leave record changes.
     */
    private fun loadLeaveHistory(employeeId: String) {
        getLeaveHistoryUseCase(employeeId)
            .onEach { records -> updateState { copy(leaveHistory = records) } }
            .launchIn(viewModelScope)
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────

private fun Employee.toFormState() = EmployeeFormState(
    id = id,
    firstName = firstName,
    lastName = lastName,
    email = email ?: "",
    phone = phone ?: "",
    position = position,
    department = department ?: "",
    hireDate = hireDate,
    salary = salary?.toString() ?: "",
    salaryType = salaryType,
    commissionRate = commissionRate.toString(),
    isActive = isActive,
    isEditing = true,
)

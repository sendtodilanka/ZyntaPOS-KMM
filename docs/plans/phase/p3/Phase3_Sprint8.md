# ZyntaPOS — Phase 3 Sprint 8: Staff Feature Part 1 — Employee Profile CRUD

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT8-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 8 of 24 | Week 8
> **Module(s):** `:composeApp:feature:staff`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001 | ADR-002

---

## Goal

Implement the first part of `:composeApp:feature:staff` (M17): Employee Profile CRUD screens (list, detail with tabs, form), the MVI ViewModel scaffold, and the Koin DI module. This sprint establishes the feature architecture that all subsequent staff sprints (9–12) will build upon.

---

## Module Structure

```
composeApp/feature/staff/
└── src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/staff/
    ├── di/
    │   └── StaffModule.kt
    ├── mvi/
    │   ├── StaffState.kt
    │   ├── StaffIntent.kt
    │   └── StaffEffect.kt
    ├── viewmodel/
    │   └── StaffViewModel.kt
    └── screen/
        ├── EmployeeListScreen.kt
        ├── EmployeeDetailScreen.kt
        └── EmployeeFormScreen.kt
```

---

## MVI Contracts

### `StaffState.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.mvi

import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.model.AttendanceSummary
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule

data class StaffState(
    val employees: List<Employee> = emptyList(),
    val filteredEmployees: List<Employee> = emptyList(),
    val selectedEmployee: Employee? = null,
    val searchQuery: String = "",
    val departmentFilter: String? = null,
    val todayAttendance: List<AttendanceRecord> = emptyList(),
    val attendanceHistory: List<AttendanceRecord> = emptyList(),
    val attendanceSummary: AttendanceSummary? = null,
    val pendingLeaves: List<LeaveRecord> = emptyList(),
    val leaveHistory: List<LeaveRecord> = emptyList(),
    val shiftSchedule: List<ShiftSchedule> = emptyList(),
    val payrollRecords: List<PayrollRecord> = emptyList(),
    val selectedPayroll: PayrollRecord? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
) {
    val departments: List<String>
        get() = employees.mapNotNull { it.department }.distinct().sorted()

    val activeEmployees: List<Employee>
        get() = employees.filter { it.isActive }
}
```

### `StaffIntent.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.mvi

import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule

sealed interface StaffIntent {
    // Employee management
    data object LoadEmployees : StaffIntent
    data class SearchEmployees(val query: String) : StaffIntent
    data class FilterByDepartment(val department: String?) : StaffIntent
    data class SelectEmployee(val employeeId: String) : StaffIntent
    data object NewEmployee : StaffIntent
    data class SaveEmployee(val employee: Employee) : StaffIntent
    data class DeleteEmployee(val employeeId: String) : StaffIntent

    // Attendance (loaded for detail view)
    data class ClockIn(val employeeId: String) : StaffIntent
    data class ClockOut(val employeeId: String) : StaffIntent
    data class LoadAttendanceHistory(val employeeId: String) : StaffIntent
    data class LoadAttendanceSummary(val employeeId: String, val period: String) : StaffIntent
    data object LoadTodayAttendance : StaffIntent

    // Leave
    data class SubmitLeaveRequest(val leave: LeaveRecord) : StaffIntent
    data class ApproveLeave(val leaveId: String) : StaffIntent
    data class RejectLeave(val leaveId: String, val reason: String) : StaffIntent
    data class LoadLeaveHistory(val employeeId: String) : StaffIntent
    data object LoadPendingLeaves : StaffIntent

    // Shift
    data class LoadShiftSchedule(val storeId: String, val weekStart: String, val weekEnd: String) : StaffIntent
    data class SaveShift(val shift: ShiftSchedule) : StaffIntent
    data class DeleteShift(val shiftId: String) : StaffIntent

    // Payroll
    data class GeneratePayroll(
        val employeeId: String,
        val periodStart: String,
        val periodEnd: String
    ) : StaffIntent
    data class ProcessPayrollPayment(val payrollId: String, val paymentRef: String) : StaffIntent
    data class LoadPayrollHistory(val employeeId: String) : StaffIntent

    // UI
    data object DismissError : StaffIntent
    data object DismissSuccess : StaffIntent
}
```

### `StaffEffect.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.mvi

sealed interface StaffEffect {
    data class ShowError(val message: String) : StaffEffect
    data class ShowSuccess(val message: String) : StaffEffect
    data class NavigateToEmployee(val employeeId: String) : StaffEffect
    data class NavigateToAttendance(val employeeId: String) : StaffEffect
    data class NavigateToPayroll(val employeeId: String) : StaffEffect
    data object NavigateBack : StaffEffect
    data class OpenMediaPicker(val entityType: String, val entityId: String) : StaffEffect
    data class ExportPayslip(val payrollId: String) : StaffEffect
    data class ConfirmDeleteEmployee(val employeeId: String, val employeeName: String) : StaffEffect
}
```

---

## ViewModel

### `StaffViewModel.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.viewmodel

import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.domain.usecase.staff.*
import com.zyntasolutions.zyntapos.feature.staff.mvi.StaffState
import com.zyntasolutions.zyntapos.feature.staff.mvi.StaffIntent
import com.zyntasolutions.zyntapos.feature.staff.mvi.StaffEffect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class StaffViewModel(
    private val getEmployees: GetEmployeesUseCase,
    private val saveEmployee: SaveEmployeeUseCase,
    private val deleteEmployee: DeleteEmployeeUseCase,
    private val getEmployeeById: GetEmployeeByIdUseCase,
    private val clockIn: ClockInUseCase,
    private val clockOut: ClockOutUseCase,
    private val getAttendanceHistory: GetAttendanceHistoryUseCase,
    private val getTodayAttendance: GetTodayAttendanceUseCase,
    private val getAttendanceSummary: GetAttendanceSummaryUseCase,
    private val submitLeaveRequest: SubmitLeaveRequestUseCase,
    private val approveLeave: ApproveLeaveUseCase,
    private val rejectLeave: RejectLeaveUseCase,
    private val getLeaveHistory: GetLeaveHistoryUseCase,
    private val getPendingLeaveRequests: GetPendingLeaveRequestsUseCase,
    private val getShiftSchedule: GetShiftScheduleUseCase,
    private val saveShift: SaveShiftScheduleUseCase,
    private val deleteShift: DeleteShiftScheduleUseCase,
    private val generatePayroll: GeneratePayrollUseCase,
    private val processPayrollPayment: ProcessPayrollPaymentUseCase,
    private val getPayrollHistory: GetPayrollHistoryUseCase,
    private val storeId: String                                      // injected from SessionManager
) : BaseViewModel<StaffState, StaffIntent, StaffEffect>(StaffState()) {

    init {
        handleIntent(StaffIntent.LoadEmployees)
    }

    override suspend fun handleIntent(intent: StaffIntent) {
        when (intent) {
            is StaffIntent.LoadEmployees        -> loadEmployees()
            is StaffIntent.SearchEmployees      -> searchEmployees(intent.query)
            is StaffIntent.FilterByDepartment   -> filterByDepartment(intent.department)
            is StaffIntent.SelectEmployee       -> selectEmployee(intent.employeeId)
            is StaffIntent.NewEmployee          -> sendEffect(StaffEffect.NavigateToEmployee(""))
            is StaffIntent.SaveEmployee         -> saveEmployeeRecord(intent.employee)
            is StaffIntent.DeleteEmployee       -> confirmDelete(intent.employeeId)
            is StaffIntent.ClockIn              -> clockInEmployee(intent.employeeId)
            is StaffIntent.ClockOut             -> clockOutEmployee(intent.employeeId)
            is StaffIntent.LoadAttendanceHistory -> loadAttendanceHistory(intent.employeeId)
            is StaffIntent.LoadAttendanceSummary -> loadAttendanceSummary(intent.employeeId, intent.period)
            is StaffIntent.LoadTodayAttendance   -> loadTodayAttendance()
            is StaffIntent.SubmitLeaveRequest    -> submitLeave(intent.leave)
            is StaffIntent.ApproveLeave          -> approveLeaveRequest(intent.leaveId)
            is StaffIntent.RejectLeave           -> rejectLeaveRequest(intent.leaveId, intent.reason)
            is StaffIntent.LoadLeaveHistory      -> loadLeaveHistory(intent.employeeId)
            is StaffIntent.LoadPendingLeaves     -> loadPendingLeaves()
            is StaffIntent.LoadShiftSchedule     -> loadShiftSchedule(intent.storeId, intent.weekStart, intent.weekEnd)
            is StaffIntent.SaveShift             -> saveShiftRecord(intent.shift)
            is StaffIntent.DeleteShift           -> deleteShiftRecord(intent.shiftId)
            is StaffIntent.GeneratePayroll       -> generatePayrollForEmployee(intent.employeeId, intent.periodStart, intent.periodEnd)
            is StaffIntent.ProcessPayrollPayment -> processPayment(intent.payrollId, intent.paymentRef)
            is StaffIntent.LoadPayrollHistory    -> loadPayrollHistory(intent.employeeId)
            is StaffIntent.DismissError          -> updateState { it.copy(error = null) }
            is StaffIntent.DismissSuccess        -> updateState { it.copy(successMessage = null) }
        }
    }

    private fun loadEmployees() {
        getEmployees(storeId)
            .onEach { employees ->
                updateState { state ->
                    state.copy(
                        employees = employees,
                        filteredEmployees = applyFilters(employees, state.searchQuery, state.departmentFilter)
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun searchEmployees(query: String) {
        updateState { state ->
            state.copy(
                searchQuery = query,
                filteredEmployees = applyFilters(state.employees, query, state.departmentFilter)
            )
        }
    }

    private fun filterByDepartment(department: String?) {
        updateState { state ->
            state.copy(
                departmentFilter = department,
                filteredEmployees = applyFilters(state.employees, state.searchQuery, department)
            )
        }
    }

    private fun applyFilters(
        employees: List<com.zyntasolutions.zyntapos.domain.model.Employee>,
        query: String,
        department: String?
    ): List<com.zyntasolutions.zyntapos.domain.model.Employee> {
        return employees
            .filter { department == null || it.department == department }
            .filter { query.isBlank() || it.fullName.contains(query, ignoreCase = true) || it.position.contains(query, ignoreCase = true) }
    }

    private suspend fun selectEmployee(employeeId: String) {
        val employee = getEmployeeById(employeeId)
        updateState { it.copy(selectedEmployee = employee) }
        if (employee != null) sendEffect(StaffEffect.NavigateToEmployee(employeeId))
    }

    private suspend fun saveEmployeeRecord(employee: com.zyntasolutions.zyntapos.domain.model.Employee) {
        updateState { it.copy(isSaving = true) }
        saveEmployee(employee).fold(
            onSuccess = {
                updateState { state -> state.copy(isSaving = false) }
                sendEffect(StaffEffect.ShowSuccess("Employee saved successfully"))
                sendEffect(StaffEffect.NavigateBack)
            },
            onFailure = { ex ->
                updateState { state -> state.copy(isSaving = false, error = ex.message) }
            }
        )
    }

    private fun confirmDelete(employeeId: String) {
        val employee = state.value.employees.find { it.id == employeeId } ?: return
        sendEffect(StaffEffect.ConfirmDeleteEmployee(employeeId, employee.fullName))
    }

    private suspend fun clockInEmployee(employeeId: String) {
        clockIn(employeeId).fold(
            onSuccess = { sendEffect(StaffEffect.ShowSuccess("Clocked in successfully")) },
            onFailure = { ex -> sendEffect(StaffEffect.ShowError(ex.message ?: "Clock-in failed")) }
        )
    }

    private suspend fun clockOutEmployee(employeeId: String) {
        clockOut(employeeId).fold(
            onSuccess = { sendEffect(StaffEffect.ShowSuccess("Clocked out successfully")) },
            onFailure = { ex -> sendEffect(StaffEffect.ShowError(ex.message ?: "Clock-out failed")) }
        )
    }

    private fun loadAttendanceHistory(employeeId: String) {
        getAttendanceHistory(employeeId)
            .onEach { records -> updateState { it.copy(attendanceHistory = records) } }
            .launchIn(viewModelScope)
    }

    private suspend fun loadAttendanceSummary(employeeId: String, period: String) {
        val summary = getAttendanceSummary(employeeId, period)
        updateState { it.copy(attendanceSummary = summary) }
    }

    private fun loadTodayAttendance() {
        // Collect from getTodayAttendance flow
    }

    private suspend fun submitLeave(leave: com.zyntasolutions.zyntapos.domain.model.LeaveRecord) {
        submitLeaveRequest(leave).fold(
            onSuccess = { sendEffect(StaffEffect.ShowSuccess("Leave request submitted")) },
            onFailure = { ex -> sendEffect(StaffEffect.ShowError(ex.message ?: "Submission failed")) }
        )
    }

    private suspend fun approveLeaveRequest(leaveId: String) {
        // Requires APPROVE_LEAVE permission — enforced by RbacNavFilter upstream
        approveLeave(leaveId, "").fold(
            onSuccess = { sendEffect(StaffEffect.ShowSuccess("Leave approved")) },
            onFailure = { ex -> sendEffect(StaffEffect.ShowError(ex.message ?: "Approval failed")) }
        )
    }

    private suspend fun rejectLeaveRequest(leaveId: String, reason: String) {
        rejectLeave(leaveId, "", reason).fold(
            onSuccess = { sendEffect(StaffEffect.ShowSuccess("Leave rejected")) },
            onFailure = { ex -> sendEffect(StaffEffect.ShowError(ex.message ?: "Rejection failed")) }
        )
    }

    private fun loadLeaveHistory(employeeId: String) {
        getLeaveHistory(employeeId)
            .onEach { records -> updateState { it.copy(leaveHistory = records) } }
            .launchIn(viewModelScope)
    }

    private fun loadPendingLeaves() {
        getPendingLeaveRequests(storeId)
            .onEach { records -> updateState { it.copy(pendingLeaves = records) } }
            .launchIn(viewModelScope)
    }

    private fun loadShiftSchedule(storeId: String, weekStart: String, weekEnd: String) {
        getShiftSchedule(storeId, weekStart, weekEnd)
            .onEach { shifts -> updateState { it.copy(shiftSchedule = shifts) } }
            .launchIn(viewModelScope)
    }

    private suspend fun saveShiftRecord(shift: com.zyntasolutions.zyntapos.domain.model.ShiftSchedule) {
        saveShift(shift).fold(
            onSuccess = { sendEffect(StaffEffect.ShowSuccess("Shift saved")) },
            onFailure = { ex -> sendEffect(StaffEffect.ShowError(ex.message ?: "Save failed")) }
        )
    }

    private suspend fun deleteShiftRecord(shiftId: String) {
        deleteShift(shiftId).fold(
            onSuccess = { sendEffect(StaffEffect.ShowSuccess("Shift deleted")) },
            onFailure = { ex -> sendEffect(StaffEffect.ShowError(ex.message ?: "Delete failed")) }
        )
    }

    private suspend fun generatePayrollForEmployee(
        employeeId: String,
        periodStart: String,
        periodEnd: String
    ) {
        updateState { it.copy(isLoading = true) }
        generatePayroll(employeeId, periodStart, periodEnd).fold(
            onSuccess = { payroll ->
                updateState { state ->
                    state.copy(
                        isLoading = false,
                        selectedPayroll = payroll,
                        payrollRecords = state.payrollRecords + payroll
                    )
                }
                sendEffect(StaffEffect.ShowSuccess("Payroll generated"))
            },
            onFailure = { ex ->
                updateState { it.copy(isLoading = false, error = ex.message) }
            }
        )
    }

    private suspend fun processPayment(payrollId: String, paymentRef: String) {
        processPayrollPayment(payrollId, paymentRef).fold(
            onSuccess = { sendEffect(StaffEffect.ShowSuccess("Payment processed")) },
            onFailure = { ex -> sendEffect(StaffEffect.ShowError(ex.message ?: "Payment failed")) }
        )
    }

    private fun loadPayrollHistory(employeeId: String) {
        getPayrollHistory(employeeId)
            .onEach { records -> updateState { it.copy(payrollRecords = records) } }
            .launchIn(viewModelScope)
    }
}
```

---

## Screen Files

### `EmployeeListScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.component.ZyntaSearchBar
import com.zyntasolutions.zyntapos.designsystem.component.ZyntaFab
import com.zyntasolutions.zyntapos.feature.staff.mvi.StaffIntent
import com.zyntasolutions.zyntapos.feature.staff.viewmodel.StaffViewModel

@Composable
fun EmployeeListScreen(
    viewModel: StaffViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handleIntent(StaffIntent.LoadEmployees)
        viewModel.effects.collect { effect ->
            when (effect) {
                is com.zyntasolutions.zyntapos.feature.staff.mvi.StaffEffect.NavigateToEmployee ->
                    if (effect.employeeId.isBlank()) onNavigateToNew()
                    else onNavigateToDetail(effect.employeeId)
                else -> {}
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            ZyntaFab(
                text = "Add Employee",
                onClick = { viewModel.handleIntent(StaffIntent.NewEmployee) }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            ZyntaSearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.handleIntent(StaffIntent.SearchEmployees(it)) },
                placeholder = "Search employees…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Department filter chips
            if (state.departments.isNotEmpty()) {
                DepartmentFilterRow(
                    departments = state.departments,
                    selected = state.departmentFilter,
                    onSelect = { viewModel.handleIntent(StaffIntent.FilterByDepartment(it)) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Employee list
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.filteredEmployees, key = { it.id }) { employee ->
                        EmployeeCard(
                            employee = employee,
                            onClick = { viewModel.handleIntent(StaffIntent.SelectEmployee(employee.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartmentFilterRow(
    departments: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Horizontal scrollable row of FilterChip composables
    // "All" chip when selected == null; individual department chips
}

@Composable
private fun EmployeeCard(
    employee: com.zyntasolutions.zyntapos.domain.model.Employee,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ZyntaCard with: avatar/initials, fullName, position, department badge, isActive indicator
}
```

### `EmployeeDetailScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Tab-based detail screen for an employee.
 *
 * Tab 0: Profile — personal info, contact, salary details, documents list
 * Tab 1: Attendance — AttendanceSummaryCard + AttendanceHistoryList (from Sprint 9)
 * Tab 2: Leave — LeaveHistoryList + Submit Leave button (from Sprint 10)
 * Tab 3: Payroll — PayrollHistoryList + Generate Payroll (from Sprint 12)
 *
 * Navigation: Edit FAB → EmployeeFormScreen
 */
@Composable
fun EmployeeDetailScreen(
    employeeId: String,
    viewModel: StaffViewModel,
    onNavigateToEdit: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profile", "Attendance", "Leave", "Payroll")

    LaunchedEffect(employeeId) {
        viewModel.handleIntent(StaffIntent.SelectEmployee(employeeId))
        viewModel.handleIntent(StaffIntent.LoadAttendanceHistory(employeeId))
        viewModel.handleIntent(StaffIntent.LoadLeaveHistory(employeeId))
        viewModel.handleIntent(StaffIntent.LoadPayrollHistory(employeeId))
    }

    val employee = state.selectedEmployee ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(employee.fullName) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back icon */ } },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(employeeId) }) { /* edit icon */ }
                }
            )
        }
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> EmployeeProfileTab(employee)
                1 -> EmployeeAttendanceTab(state, viewModel)
                2 -> EmployeeLeaveTab(state, viewModel)
                3 -> EmployeePayrollTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun EmployeeProfileTab(employee: com.zyntasolutions.zyntapos.domain.model.Employee) {
    // Personal info: name, email, phone, address, DOB, hire date
    // Employment info: position, department, salary, salary type, commission rate
    // Emergency contact
    // Documents list (name + type badges)
}
```

### `EmployeeFormScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Create / Edit employee form.
 *
 * Sections:
 * 1. Personal Info: firstName, lastName, email, phone, address, dateOfBirth
 * 2. Employment:    storeId (read-only from session), position, department, hireDate
 * 3. Compensation:  salary, salaryType (dropdown), commissionRate (%)
 * 4. Optional:      userId (link to system user), emergencyContact
 * 5. Documents:     list of EmployeeDocument (name, url, type) — add/remove rows
 *
 * Validation:
 * - firstName, lastName, position, hireDate are required
 * - email must match email regex if provided
 * - commissionRate must be 0.0–100.0
 * - salary must be positive if provided
 *
 * On save: calls StaffIntent.SaveEmployee
 * On "Add Image": calls StaffEffect.OpenMediaPicker(entityType="Employee", entityId=employee.id)
 */
@Composable
fun EmployeeFormScreen(
    employeeId: String?,                    // null = new employee
    viewModel: StaffViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Form state derived from selectedEmployee (if editing) or fresh Employee()
    // ZyntaTextField for text fields, ZyntaDropdownMenu for salaryType
    // Save button triggers StaffIntent.SaveEmployee
}
```

---

## DI Module

### `StaffModule.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.di

import com.zyntasolutions.zyntapos.domain.usecase.staff.*
import com.zyntasolutions.zyntapos.feature.staff.viewmodel.StaffViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val staffModule = module {
    // Use case bindings (SAM lambda wrappers around repo calls)
    single<GetEmployeesUseCase>           { GetEmployeesUseCase { storeId -> get<com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository>().getByStore(storeId) } }
    single<GetEmployeeByIdUseCase>        { GetEmployeeByIdUseCase { id -> get<com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository>().getById(id) } }
    single<SaveEmployeeUseCase>           { SaveEmployeeUseCase { emp -> get<com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository>().save(emp) } }
    single<DeleteEmployeeUseCase>         { DeleteEmployeeUseCase { id -> get<com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository>().delete(id) } }

    single<ClockInUseCase>                { get() }   // ClockInUseCaseImpl bound in dataModule
    single<ClockOutUseCase>               { get() }
    single<GetAttendanceHistoryUseCase>   { GetAttendanceHistoryUseCase { empId -> get<com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository>().getByEmployee(empId) } }
    single<GetTodayAttendanceUseCase>     { get() }
    single<GetAttendanceSummaryUseCase>   { get() }

    single<SubmitLeaveRequestUseCase>     { SubmitLeaveRequestUseCase { leave -> get<com.zyntasolutions.zyntapos.domain.repository.LeaveRepository>().save(leave) } }
    single<ApproveLeaveUseCase>           { get() }
    single<RejectLeaveUseCase>            { get() }
    single<GetLeaveHistoryUseCase>        { GetLeaveHistoryUseCase { empId -> get<com.zyntasolutions.zyntapos.domain.repository.LeaveRepository>().getByEmployee(empId) } }
    single<GetPendingLeaveRequestsUseCase>{ GetPendingLeaveRequestsUseCase { storeId -> get<com.zyntasolutions.zyntapos.domain.repository.LeaveRepository>().getPendingForStore(storeId) } }

    single<GetShiftScheduleUseCase>       { GetShiftScheduleUseCase { s, ws, we -> get<com.zyntasolutions.zyntapos.domain.repository.ShiftRepository>().getByStoreAndWeek(s, ws, we) } }
    single<SaveShiftScheduleUseCase>      { SaveShiftScheduleUseCase { shift -> get<com.zyntasolutions.zyntapos.domain.repository.ShiftRepository>().save(shift) } }
    single<DeleteShiftScheduleUseCase>    { DeleteShiftScheduleUseCase { id -> get<com.zyntasolutions.zyntapos.domain.repository.ShiftRepository>().delete(id) } }

    single<GeneratePayrollUseCase>        { get() }   // GeneratePayrollUseCaseImpl
    single<ProcessPayrollPaymentUseCase>  { get() }
    single<GetPayrollHistoryUseCase>      { GetPayrollHistoryUseCase { empId -> get<com.zyntasolutions.zyntapos.domain.repository.PayrollRepository>().getByEmployee(empId) } }

    viewModel {
        StaffViewModel(
            getEmployees              = get(),
            saveEmployee              = get(),
            deleteEmployee            = get(),
            getEmployeeById           = get(),
            clockIn                   = get(),
            clockOut                  = get(),
            getAttendanceHistory      = get(),
            getTodayAttendance        = get(),
            getAttendanceSummary      = get(),
            submitLeaveRequest        = get(),
            approveLeave              = get(),
            rejectLeave               = get(),
            getLeaveHistory           = get(),
            getPendingLeaveRequests   = get(),
            getShiftSchedule          = get(),
            saveShift                 = get(),
            deleteShift               = get(),
            generatePayroll           = get(),
            processPayrollPayment     = get(),
            getPayrollHistory         = get(),
            storeId                   = get<com.zyntasolutions.zyntapos.security.SessionManager>().currentSession()?.storeId ?: ""
        )
    }
}
```

---

## Navigation Wiring

In `composeApp/navigation/src/commonMain/.../navigation/MainNavGraph.kt`, add the staff sub-graph:

```kotlin
navigation(startDestination = ZyntaRoute.StaffList, route = ZyntaRoute.StaffGraph) {
    composable<ZyntaRoute.StaffList> {
        val vm = koinViewModel<StaffViewModel>()
        EmployeeListScreen(
            viewModel = vm,
            onNavigateToDetail = { id -> navController.navigate(ZyntaRoute.StaffDetail(id)) },
            onNavigateToNew = { navController.navigate(ZyntaRoute.StaffDetail(null)) }
        )
    }
    composable<ZyntaRoute.StaffDetail> { backStackEntry ->
        val route: ZyntaRoute.StaffDetail = backStackEntry.toRoute()
        val vm = koinViewModel<StaffViewModel>()
        if (route.employeeId == null) {
            EmployeeFormScreen(employeeId = null, viewModel = vm, onNavigateBack = { navController.popBackStack() })
        } else {
            EmployeeDetailScreen(
                employeeId = route.employeeId,
                viewModel = vm,
                onNavigateToEdit = { id -> navController.navigate(ZyntaRoute.StaffDetail(id)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

## Tasks

- [ ] **8.1** Create `StaffState.kt`, `StaffIntent.kt`, `StaffEffect.kt` in `mvi/` package
- [ ] **8.2** Implement `StaffViewModel.kt` extending `BaseViewModel<StaffState, StaffIntent, StaffEffect>` (ADR-001)
- [ ] **8.3** Implement `EmployeeListScreen.kt` with search, department filter chips, and lazy employee cards
- [ ] **8.4** Implement `EmployeeDetailScreen.kt` with 4-tab layout (Profile / Attendance / Leave / Payroll)
- [ ] **8.5** Implement `EmployeeFormScreen.kt` with all form sections and client-side validation
- [ ] **8.6** Create `StaffModule.kt` Koin module and register in `ZyntaApplication`
- [ ] **8.7** Wire staff navigation sub-graph in `MainNavGraph.kt`
- [ ] **8.8** Add "Staff" nav item to `NavigationItems.kt` gated by `Permission.VIEW_STAFF`
- [ ] **8.9** Verify: `./gradlew :composeApp:feature:staff:assemble`
- [ ] **8.10** Write `StaffViewModelTest.kt` — test `LoadEmployees`, `SearchEmployees`, `FilterByDepartment` using Turbine
- [ ] **8.11** Run: `./gradlew :composeApp:feature:staff:test`

---

## Verification

```bash
./gradlew :composeApp:feature:staff:assemble
./gradlew :composeApp:feature:staff:test
./gradlew :composeApp:feature:staff:detekt
```

---

## Definition of Done

- [ ] `StaffViewModel` extends `BaseViewModel` (ADR-001)
- [ ] All 3 screens implemented with correct MVI wiring
- [ ] `StaffModule` Koin bindings correct and registered
- [ ] Navigation sub-graph wired and RBAC-gated
- [ ] `StaffViewModelTest` passes (employee list, search, filter)
- [ ] Commit: `feat(staff): add employee CRUD screens with MVI scaffold and Koin wiring`

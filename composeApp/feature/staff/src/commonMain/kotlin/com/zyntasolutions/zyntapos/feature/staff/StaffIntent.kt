package com.zyntasolutions.zyntapos.feature.staff

import com.zyntasolutions.zyntapos.domain.model.LeaveType
import com.zyntasolutions.zyntapos.domain.model.SalaryType

/**
 * All user interactions and system events that can mutate [StaffState].
 *
 * Dispatched by Composable screens → [StaffViewModel.handleIntent].
 *
 * ### Categories
 * - **Tab:** [SwitchTab]
 * - **Employee list:** [LoadEmployees], [SearchEmployees], [ToggleShowInactive]
 * - **Employee detail:** [SelectEmployee], [BackToEmployeeList], [UpdateEmployeeField],
 *   [UpdateEmployeeSalaryType], [ToggleEmployeeActive], [SaveEmployee], [DeleteEmployee]
 * - **Attendance:** [LoadTodayAttendance], [LoadAttendanceHistory], [ClockIn], [ClockOut],
 *   [ExportAttendanceCsv]
 * - **Leave:** [LoadPendingLeave], [ShowLeaveForm], [HideLeaveForm], [UpdateLeaveFormField],
 *   [UpdateLeaveType], [SubmitLeaveRequest], [ApproveLeave], [RejectLeave]
 * - **Shifts:** [LoadWeeklyShifts], [ShowShiftForm], [HideShiftForm], [UpdateShiftField],
 *   [SaveShift], [DeleteShift]
 * - **Payroll:** [LoadPayroll], [SelectPayroll], [GeneratePayroll], [ProcessPayment]
 * - **History:** [LoadPayrollHistory], [LoadAttendanceSummary], [LoadLeaveHistory]
 * - **UI:** [DismissError], [DismissSuccess]
 */
sealed interface StaffIntent {

    // ── Tab Navigation ─────────────────────────────────────────────────────
    data class SwitchTab(val tab: StaffTab) : StaffIntent

    // ── Employee List ──────────────────────────────────────────────────────
    data object LoadEmployees : StaffIntent
    data class SearchEmployees(val query: String) : StaffIntent
    data class ToggleShowInactive(val show: Boolean) : StaffIntent

    // ── Employee Detail / Form ─────────────────────────────────────────────
    /** Load employee into detail form; null opens the new-employee form. */
    data class SelectEmployee(val employeeId: String?) : StaffIntent
    data object BackToEmployeeList : StaffIntent
    data class UpdateEmployeeField(val field: String, val value: String) : StaffIntent
    data class UpdateEmployeeSalaryType(val salaryType: SalaryType) : StaffIntent
    data object ToggleEmployeeActive : StaffIntent
    data object SaveEmployee : StaffIntent
    data class DeleteEmployee(val employeeId: String) : StaffIntent

    // ── Attendance ─────────────────────────────────────────────────────────
    data class LoadTodayAttendance(val storeId: String, val today: String) : StaffIntent
    data class LoadAttendanceHistory(
        val employeeId: String,
        val from: String,
        val to: String,
    ) : StaffIntent
    data class ClockIn(
        val employeeId: String,
        val storeId: String,
        val clockInTime: String,
    ) : StaffIntent
    data class ClockOut(
        val attendanceId: String,
        val employeeId: String,
        val clockOutTime: String,
    ) : StaffIntent
    data object ExportAttendanceCsv : StaffIntent

    // ── Leave Management ───────────────────────────────────────────────────
    data class LoadPendingLeave(val storeId: String) : StaffIntent
    data object ShowLeaveForm : StaffIntent
    data object HideLeaveForm : StaffIntent
    data class UpdateLeaveFormField(val field: String, val value: String) : StaffIntent
    data class UpdateLeaveType(val leaveType: LeaveType) : StaffIntent
    data object SubmitLeaveRequest : StaffIntent
    data class ApproveLeave(
        val requestId: String,
        val approverId: String,
        val approvedAt: Long,
    ) : StaffIntent
    data class RejectLeave(
        val requestId: String,
        val reason: String,
        val rejectedAt: Long,
    ) : StaffIntent

    // ── Shift Scheduling ───────────────────────────────────────────────────
    data class LoadWeeklyShifts(
        val storeId: String,
        val weekStart: String,
        val weekEnd: String,
    ) : StaffIntent
    data object ShowShiftForm : StaffIntent
    data object HideShiftForm : StaffIntent
    data class UpdateShiftField(val field: String, val value: String) : StaffIntent
    data object SaveShift : StaffIntent
    data class DeleteShift(val shiftId: String) : StaffIntent

    // ── Payroll ────────────────────────────────────────────────────────────
    data class LoadPayroll(val storeId: String, val period: String) : StaffIntent
    data class SelectPayroll(val payrollId: String?) : StaffIntent
    data class GeneratePayroll(
        val employeeId: String,
        val periodStart: String,
        val periodEnd: String,
    ) : StaffIntent
    data class ProcessPayment(
        val payrollId: String,
        val paidAt: Long,
        val paymentRef: String?,
    ) : StaffIntent

    /** Generate payroll for ALL active employees for the given period (G11 — "Generate All"). */
    data class GenerateAllPayroll(
        val periodStart: String,
        val periodEnd: String,
    ) : StaffIntent

    // ── History / Summary ──────────────────────────────────────────────────
    /** Load the full payroll history for a specific employee (reactive stream). */
    data class LoadPayrollHistory(val employeeId: String) : StaffIntent

    /**
     * Load an attendance summary for an employee over a date range.
     * @param from ISO date: YYYY-MM-DD (inclusive).
     * @param to ISO date: YYYY-MM-DD (inclusive).
     */
    data class LoadAttendanceSummary(
        val employeeId: String,
        val from: String,
        val to: String,
    ) : StaffIntent

    /** Load the full leave history for a specific employee (reactive stream). */
    data class LoadLeaveHistory(val employeeId: String) : StaffIntent

    /** Compute leave balance (days used/remaining) for the current calendar year. */
    data class LoadLeaveBalance(val employeeId: String) : StaffIntent

    // ── C3.4: Employee Roaming ─────────────────────────────────────────────
    /** Navigate to the employee's store assignments screen (C3.4). */
    data class NavigateToEmployeeStores(val employeeId: String) : StaffIntent

    // ── C3.4: Multi-Store Attendance ─────────────────────────────────────
    /** Load available stores for the clock-in store selector. */
    data object LoadAvailableStores : StaffIntent

    /** Select a store for clock-in (multi-store employee). */
    data class SelectClockInStore(val storeId: String?) : StaffIntent

    /** Load cross-store attendance report for all stores. */
    data class LoadCrossStoreAttendance(
        val from: String,
        val to: String,
    ) : StaffIntent

    // ── UI Feedback ────────────────────────────────────────────────────────
    data object DismissError : StaffIntent
    data object DismissSuccess : StaffIntent
}

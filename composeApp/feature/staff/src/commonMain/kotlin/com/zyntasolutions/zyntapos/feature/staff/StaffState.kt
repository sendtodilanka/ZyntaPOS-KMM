package com.zyntasolutions.zyntapos.feature.staff

import com.zyntasolutions.zyntapos.core.utils.DateTimeUtils
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.model.AttendanceSummary
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.LeaveType
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollSummary
import com.zyntasolutions.zyntapos.domain.model.SalaryType
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule

/** Active section displayed within the Staff feature. */
enum class StaffTab { EMPLOYEES, ATTENDANCE, LEAVE, SHIFTS, PAYROLL }

/**
 * Immutable UI state for all Staff & HR screens (Sprints 8–12).
 *
 * Single state drives five logical sections:
 * - **EMPLOYEES:** directory, create/edit form
 * - **ATTENDANCE:** today's clock-in/out board
 * - **LEAVE:** pending requests, submit form, approve/reject
 * - **SHIFTS:** weekly schedule grid, add/edit dialog
 * - **PAYROLL:** period summary, per-employee records, mark-paid
 */
data class StaffState(

    /** User-preferred date format pattern from GeneralSettings (G20). */
    val dateFormat: String = DateTimeUtils.DEFAULT_DATE_FORMAT,

    val activeTab: StaffTab = StaffTab.EMPLOYEES,

    // ── Employee Directory ────────────────────────────────────────────────
    val employees: List<Employee> = emptyList(),
    val searchQuery: String = "",
    val showInactive: Boolean = false,

    // ── Employee Detail / Form ────────────────────────────────────────────
    val selectedEmployee: Employee? = null,
    val employeeForm: EmployeeFormState = EmployeeFormState(),

    // ── Attendance ────────────────────────────────────────────────────────
    /** Today's clock-in/out records for all store employees. */
    val todayAttendance: List<AttendanceRecord> = emptyList(),
    /** Detailed history for a specific employee. */
    val attendanceHistory: List<AttendanceRecord> = emptyList(),
    /** YYYY-MM-DD string used to scope today's attendance queries. */
    val todayDate: String = "",

    // ── Leave Management ──────────────────────────────────────────────────
    val pendingLeaveRequests: List<LeaveRecord> = emptyList(),
    val showLeaveForm: Boolean = false,
    val leaveForm: LeaveFormState = LeaveFormState(),

    // ── Shift Scheduling ──────────────────────────────────────────────────
    val weeklyShifts: List<ShiftSchedule> = emptyList(),
    val weekStart: String = "",
    val weekEnd: String = "",
    val showShiftForm: Boolean = false,
    val shiftForm: ShiftFormState = ShiftFormState(),

    // ── Payroll ───────────────────────────────────────────────────────────
    val payrollPeriod: String = "",
    val payrollRecords: List<PayrollRecord> = emptyList(),
    val payrollSummary: PayrollSummary? = null,
    val selectedPayroll: PayrollRecord? = null,

    // ── Payroll History (employee-scoped reactive stream) ─────────────────
    val payrollHistory: List<PayrollRecord> = emptyList(),

    // ── Attendance Summary ────────────────────────────────────────────────
    val attendanceSummary: AttendanceSummary? = null,

    // ── Leave History (employee-scoped reactive stream) ───────────────────
    val leaveHistory: List<LeaveRecord> = emptyList(),

    // ── Leave Balance (per-type days used/remaining for selected employee) ──
    val leaveBalance: LeaveBalanceState? = null,

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/**
 * Mutable form fields mirroring [Employee] for create/edit.
 *
 * Kept separate from the domain model to support partial edits,
 * validation error display, and string-based text field binding.
 */
data class EmployeeFormState(
    val id: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val position: String = "",
    val department: String = "",
    val hireDate: String = "",
    val salary: String = "",
    val salaryType: SalaryType = SalaryType.MONTHLY,
    val commissionRate: String = "0",
    val isActive: Boolean = true,
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)

/** Form state for submitting a new leave request. */
data class LeaveFormState(
    val employeeId: String = "",
    val leaveType: LeaveType = LeaveType.ANNUAL,
    val startDate: String = "",
    val endDate: String = "",
    val reason: String = "",
    val validationErrors: Map<String, String> = emptyMap(),
)

/** Form state for creating or editing a shift schedule entry. */
data class ShiftFormState(
    val id: String? = null,
    val employeeId: String = "",
    val shiftDate: String = "",
    val startTime: String = "09:00",
    val endTime: String = "17:00",
    val notes: String = "",
)

/**
 * Leave balance summary for a specific employee (current calendar year).
 *
 * @property annualUsed   Days of ANNUAL leave approved this year.
 * @property sickUsed     Days of SICK leave approved this year.
 * @property personalUsed Days of PERSONAL leave approved this year.
 * @property unpaidUsed   Days of UNPAID leave approved this year.
 * @property annualAllowance  Annual leave entitlement (configurable per company, default 14).
 * @property sickAllowance    Sick leave entitlement (default 7).
 */
data class LeaveBalanceState(
    val annualUsed: Int = 0,
    val sickUsed: Int = 0,
    val personalUsed: Int = 0,
    val unpaidUsed: Int = 0,
    val annualAllowance: Int = 14,
    val sickAllowance: Int = 7,
) {
    val annualRemaining: Int get() = (annualAllowance - annualUsed).coerceAtLeast(0)
    val sickRemaining: Int get() = (sickAllowance - sickUsed).coerceAtLeast(0)
    val totalUsed: Int get() = annualUsed + sickUsed + personalUsed + unpaidUsed
}

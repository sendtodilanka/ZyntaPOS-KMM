package com.zyntasolutions.zyntapos.feature.staff

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
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:staff` (Sprints 8–12).
 *
 * ### Use-Case Registrations (factory — new instance per injection)
 *
 * **Sprint 8 — Employee CRUD**
 * - [GetEmployeesUseCase], [GetEmployeeByIdUseCase], [SaveEmployeeUseCase], [DeleteEmployeeUseCase]
 *
 * **Sprint 9 — Attendance**
 * - [ClockInUseCase], [ClockOutUseCase], [GetTodayAttendanceUseCase], [GetAttendanceHistoryUseCase]
 *
 * **Sprint 10 — Leave Management**
 * - [SubmitLeaveRequestUseCase], [ApproveLeaveUseCase], [RejectLeaveUseCase],
 *   [GetPendingLeaveRequestsUseCase]
 *
 * **Sprint 11 — Shift Scheduling**
 * - [GetShiftScheduleUseCase], [SaveShiftScheduleUseCase], [DeleteShiftScheduleUseCase]
 *
 * **Sprint 12 — Payroll**
 * - [GeneratePayrollUseCase], [ProcessPayrollPaymentUseCase]
 * - [GetPayrollHistoryUseCase], [GetAttendanceSummaryUseCase], [GetLeaveHistoryUseCase]
 */
val staffModule = module {

    // ── Sprint 8: Employee CRUD ───────────────────────────────────────────
    factoryOf(::GetEmployeesUseCase)
    factoryOf(::GetEmployeeByIdUseCase)
    factoryOf(::SaveEmployeeUseCase)
    factoryOf(::DeleteEmployeeUseCase)

    // ── Sprint 9: Attendance ──────────────────────────────────────────────
    factoryOf(::ClockInUseCase)
    // factoryOf cannot be used here because ClockOutUseCase has a non-DI
    // parameter (overtimeThresholdHours: Double = 8.0). Use an explicit factory
    // so the default value is preserved and Koin does not attempt to inject Double.
    factory { ClockOutUseCase(attendanceRepository = get()) }
    factoryOf(::GetTodayAttendanceUseCase)
    factoryOf(::GetAttendanceHistoryUseCase)

    // ── Sprint 10: Leave management ───────────────────────────────────────
    factoryOf(::SubmitLeaveRequestUseCase)
    factoryOf(::ApproveLeaveUseCase)
    factoryOf(::RejectLeaveUseCase)
    factoryOf(::GetPendingLeaveRequestsUseCase)

    // ── Sprint 11: Shift scheduling ───────────────────────────────────────
    factoryOf(::GetShiftScheduleUseCase)
    factoryOf(::SaveShiftScheduleUseCase)
    factoryOf(::DeleteShiftScheduleUseCase)

    // ── Sprint 12: Payroll ────────────────────────────────────────────────
    factoryOf(::GeneratePayrollUseCase)
    factoryOf(::ProcessPayrollPaymentUseCase)

    // ── Payroll / Attendance / Leave history (orphan use cases — B2 wiring) ──
    factoryOf(::GetPayrollHistoryUseCase)
    factoryOf(::GetAttendanceSummaryUseCase)
    factoryOf(::GetLeaveHistoryUseCase)

    // ── ViewModel ─────────────────────────────────────────────────────────
    viewModel {
        StaffViewModel(
            authRepository = get(),
            payrollRepository = get(),
            shiftRepository = get(),
            getEmployeesUseCase = get(),
            getEmployeeByIdUseCase = get(),
            saveEmployeeUseCase = get(),
            deleteEmployeeUseCase = get(),
            clockInUseCase = get(),
            clockOutUseCase = get(),
            getTodayAttendanceUseCase = get(),
            getAttendanceHistoryUseCase = get(),
            getPendingLeaveUseCase = get(),
            submitLeaveUseCase = get(),
            approveLeaveUseCase = get(),
            rejectLeaveUseCase = get(),
            getShiftScheduleUseCase = get(),
            saveShiftUseCase = get(),
            deleteShiftUseCase = get(),
            generatePayrollUseCase = get(),
            processPaymentUseCase = get(),
            getPayrollHistoryUseCase = get(),
            getAttendanceSummaryUseCase = get(),
            getLeaveHistoryUseCase = get(),
        )
    }
}

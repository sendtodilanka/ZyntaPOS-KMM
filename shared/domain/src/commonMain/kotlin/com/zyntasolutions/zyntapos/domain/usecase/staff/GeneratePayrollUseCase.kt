package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AttendanceSummary
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollStatus
import com.zyntasolutions.zyntapos.domain.model.SalaryType
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository
import com.zyntasolutions.zyntapos.domain.repository.PayrollRepository

/**
 * Generates a payroll record for an employee for the given pay period.
 *
 * Calculates base salary from attendance hours (for HOURLY employees)
 * or uses the fixed salary for DAILY/WEEKLY/MONTHLY employees.
 * Overtime is calculated at 1.5x the hourly rate.
 *
 * ### Business Rules
 * 1. Employee must have a salary configured.
 * 2. A payroll record must not already exist for the same employee+period.
 */
class GeneratePayrollUseCase(
    private val payrollRepository: PayrollRepository,
    private val attendanceRepository: AttendanceRepository,
) {
    suspend operator fun invoke(
        employee: Employee,
        periodStart: String,
        periodEnd: String,
        recordId: String,
        createdAt: Long,
        deductions: Double = 0.0,
    ): Result<Unit> {
        val salary = employee.salary
            ?: return Result.Error(
                ValidationException(
                    "Employee has no salary configured.",
                    field = "salary",
                    rule = "REQUIRED",
                ),
            )

        // Check for existing payroll record
        val existingResult = payrollRepository.getByEmployeeAndPeriod(employee.id, periodStart)
        if (existingResult is Result.Error) return existingResult
        val existing = (existingResult as Result.Success).data
        if (existing != null) {
            return Result.Error(
                ValidationException(
                    "Payroll for this period already exists.",
                    field = "periodStart",
                    rule = "DUPLICATE",
                ),
            )
        }

        // Get attendance summary for the period
        val summaryResult = attendanceRepository.getSummary(employee.id, periodStart, periodEnd)
        val summary: AttendanceSummary? = if (summaryResult is Result.Success) summaryResult.data else null

        val baseSalary: Double
        val overtimePay: Double

        when (employee.salaryType) {
            SalaryType.HOURLY -> {
                val regularHours = (summary?.totalHours ?: 0.0) - (summary?.overtimeHours ?: 0.0)
                baseSalary = regularHours * salary
                overtimePay = (summary?.overtimeHours ?: 0.0) * salary * 1.5
            }
            SalaryType.DAILY -> {
                baseSalary = (summary?.presentDays ?: 0) * salary
                overtimePay = (summary?.overtimeHours ?: 0.0) * (salary / 8.0) * 1.5
            }
            SalaryType.WEEKLY, SalaryType.MONTHLY -> {
                baseSalary = salary
                overtimePay = (summary?.overtimeHours ?: 0.0) * ((employee.hourlyRate ?: 0.0) * 1.5)
            }
        }

        val commission = (summary?.totalHours ?: 0.0) * 0.0 // Commission from sales — populated by sales integration
        val netPay = baseSalary + overtimePay + commission - deductions

        val record = PayrollRecord(
            id = recordId,
            employeeId = employee.id,
            periodStart = periodStart,
            periodEnd = periodEnd,
            baseSalary = baseSalary,
            overtimePay = overtimePay,
            commission = commission,
            deductions = deductions,
            netPay = netPay,
            status = PayrollStatus.PENDING,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

        return payrollRepository.insert(record)
    }
}

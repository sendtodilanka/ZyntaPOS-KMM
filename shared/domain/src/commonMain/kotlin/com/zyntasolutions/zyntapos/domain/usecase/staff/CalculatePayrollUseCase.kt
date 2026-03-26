package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.PayrollEntry
import com.zyntasolutions.zyntapos.domain.model.PayrollEntryStatus
import kotlin.time.Clock

/**
 * Calculates a payroll entry for one employee for a given pay period.
 *
 * This use case performs input validation and arithmetic:
 * - `overtimePay = overtimeHours * overtimeRate`
 * - `netPay = baseSalary + overtimePay - deductions`
 *
 * The resulting [PayrollEntry] is returned with status [PayrollEntryStatus.DRAFT].
 *
 * ### Validation Rules
 * 1. `baseSalary` must be >= 0
 * 2. `overtimeHours` must be >= 0
 * 3. `overtimeRate` must be >= 0
 * 4. `deductions` must be >= 0
 */
class CalculatePayrollUseCase {

    operator fun invoke(
        employeeId: String,
        periodStart: String,
        periodEnd: String,
        baseSalary: Double,
        overtimeHours: Double,
        overtimeRate: Double,
        deductions: Double,
        deductionNotes: String? = null,
    ): Result<PayrollEntry> {
        // ── Validation ──────────────────────────────────────────────────────
        if (baseSalary < 0) {
            return Result.Error(
                ValidationException(
                    "Base salary must not be negative.",
                    field = "baseSalary",
                    rule = "NON_NEGATIVE",
                ),
            )
        }
        if (overtimeHours < 0) {
            return Result.Error(
                ValidationException(
                    "Overtime hours must not be negative.",
                    field = "overtimeHours",
                    rule = "NON_NEGATIVE",
                ),
            )
        }
        if (overtimeRate < 0) {
            return Result.Error(
                ValidationException(
                    "Overtime rate must not be negative.",
                    field = "overtimeRate",
                    rule = "NON_NEGATIVE",
                ),
            )
        }
        if (deductions < 0) {
            return Result.Error(
                ValidationException(
                    "Deductions must not be negative.",
                    field = "deductions",
                    rule = "NON_NEGATIVE",
                ),
            )
        }

        // ── Calculation ─────────────────────────────────────────────────────
        val overtimePay = overtimeHours * overtimeRate
        val netPay = baseSalary + overtimePay - deductions
        val now = Clock.System.now().toEpochMilliseconds()

        val entry = PayrollEntry(
            id = IdGenerator.newId(),
            employeeId = employeeId,
            periodStart = periodStart,
            periodEnd = periodEnd,
            baseSalary = baseSalary,
            overtimeHours = overtimeHours,
            overtimeRate = overtimeRate,
            overtimePay = overtimePay,
            deductions = deductions,
            deductionNotes = deductionNotes,
            netPay = netPay,
            status = PayrollEntryStatus.DRAFT,
            createdAt = now,
            updatedAt = now,
        )

        return Result.Success(entry)
    }
}

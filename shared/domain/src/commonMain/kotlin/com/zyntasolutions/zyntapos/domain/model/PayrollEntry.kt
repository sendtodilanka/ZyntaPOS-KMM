package com.zyntasolutions.zyntapos.domain.model

/**
 * Represents a detailed payroll calculation entry for an employee for a single pay period.
 *
 * Unlike [PayrollRecord] which is generated from attendance data, a [PayrollEntry] captures
 * explicit overtime hours/rate breakdown and deduction notes, making it suitable for manual
 * payroll calculation workflows.
 *
 * @property id Unique identifier (UUID v4).
 * @property employeeId FK to [Employee].
 * @property periodStart ISO date (YYYY-MM-DD) — first day of the pay period.
 * @property periodEnd ISO date (YYYY-MM-DD) — last day of the pay period (inclusive).
 * @property baseSalary Base salary for the period.
 * @property overtimeHours Number of overtime hours worked.
 * @property overtimeRate Hourly rate applied to overtime hours.
 * @property overtimePay Calculated overtime pay (overtimeHours * overtimeRate).
 * @property deductions Total deductions (tax, insurance, advances, etc.).
 * @property deductionNotes Optional description of what the deductions cover.
 * @property netPay Final take-home amount (baseSalary + overtimePay - deductions).
 * @property status Lifecycle status of this payroll entry.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class PayrollEntry(
    val id: String,
    val employeeId: String,
    val periodStart: String,
    val periodEnd: String,
    val baseSalary: Double,
    val overtimeHours: Double,
    val overtimeRate: Double,
    val overtimePay: Double,
    val deductions: Double,
    val deductionNotes: String? = null,
    val netPay: Double,
    val status: PayrollEntryStatus = PayrollEntryStatus.DRAFT,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Lifecycle status of a [PayrollEntry].
 *
 * - [DRAFT] — entry created/calculated but not yet reviewed.
 * - [APPROVED] — reviewed and approved by a manager; ready for payment.
 * - [PAID] — payment has been processed.
 */
enum class PayrollEntryStatus {
    DRAFT,
    APPROVED,
    PAID,
}

package com.zyntasolutions.zyntapos.domain.model

/**
 * A payroll calculation record for one employee for one pay period.
 *
 * @property id Unique identifier (UUID v4).
 * @property employeeId FK to [Employee].
 * @property periodStart ISO date: YYYY-MM-DD (start of pay period).
 * @property periodEnd ISO date: YYYY-MM-DD (end of pay period, inclusive).
 * @property baseSalary Base salary for the period.
 * @property overtimePay Additional pay for overtime hours.
 * @property commission Sales commission earned in the period.
 * @property deductions Deductions (tax, insurance, etc.).
 * @property netPay Final take-home amount (baseSalary + overtimePay + commission - deductions).
 * @property status Payment status.
 * @property paidAt Epoch millis when payment was processed.
 * @property paymentRef External payment reference (bank transfer ID, cheque number, etc.).
 * @property notes Optional notes from payroll administrator.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class PayrollRecord(
    val id: String,
    val employeeId: String,
    val periodStart: String,
    val periodEnd: String,
    val baseSalary: Double,
    val overtimePay: Double = 0.0,
    val commission: Double = 0.0,
    val deductions: Double = 0.0,
    val netPay: Double,
    val status: PayrollStatus = PayrollStatus.PENDING,
    val paidAt: Long? = null,
    val paymentRef: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Gross pay before deductions. */
    val grossPay: Double get() = baseSalary + overtimePay + commission
}

/** Payment status of a payroll record. */
enum class PayrollStatus {
    PENDING,
    PAID,
}

/**
 * Aggregated payroll summary for a store/period.
 *
 * @property period Pay period identifier (e.g., "2026-02").
 * @property totalEmployees Number of employees included.
 * @property totalGrossPay Sum of all gross pay.
 * @property totalDeductions Sum of all deductions.
 * @property totalNetPay Sum of all net pay.
 * @property pendingCount Number of records not yet paid.
 * @property paidCount Number of records marked as paid.
 */
data class PayrollSummary(
    val period: String,
    val totalEmployees: Int,
    val totalGrossPay: Double,
    val totalDeductions: Double,
    val totalNetPay: Double,
    val pendingCount: Int,
    val paidCount: Int,
)

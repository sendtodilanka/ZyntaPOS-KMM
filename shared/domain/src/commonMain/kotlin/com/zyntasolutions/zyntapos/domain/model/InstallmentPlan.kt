package com.zyntasolutions.zyntapos.domain.model

/**
 * A credit / buy-now-pay-later plan for a specific order.
 *
 * @property id Unique identifier (UUID v4).
 * @property orderId The order this plan is financing.
 * @property customerId FK to the customer responsible for the payments.
 * @property totalAmount Total amount to be paid across all installments.
 * @property paidAmount Sum of all successful installment payments.
 * @property remainingAmount [totalAmount] − [paidAmount].
 * @property numInstallments Total number of payment installments.
 * @property frequency How often payments are due.
 * @property startDate Epoch millis of the first payment date.
 * @property endDate Epoch millis of the final payment deadline (optional).
 * @property status Current lifecycle status.
 * @property notes Internal notes.
 */
data class InstallmentPlan(
    val id: String,
    val orderId: String,
    val customerId: String,
    val totalAmount: Double,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double,
    val numInstallments: Int = 1,
    val frequency: Frequency = Frequency.MONTHLY,
    val startDate: Long,
    val endDate: Long? = null,
    val status: Status = Status.ACTIVE,
    val notes: String? = null,
) {
    enum class Frequency { WEEKLY, BIWEEKLY, MONTHLY }
    enum class Status { ACTIVE, COMPLETED, DEFAULTED }

    init {
        require(totalAmount > 0.0) { "Total amount must be positive" }
        require(numInstallments >= 1) { "Must have at least one installment" }
    }
}

/**
 * A single scheduled payment within an [InstallmentPlan].
 *
 * @property id Unique identifier (UUID v4).
 * @property planId FK to the parent [InstallmentPlan].
 * @property dueDate Epoch millis when this installment is due.
 * @property amount Expected payment amount for this installment.
 * @property paidAmount Amount actually received (may differ for partial payments).
 * @property paidAt Epoch millis when payment was received. Null if not yet paid.
 * @property status Current payment status.
 * @property paymentId FK to the payment transaction record, if settled.
 */
data class InstallmentPayment(
    val id: String,
    val planId: String,
    val dueDate: Long,
    val amount: Double,
    val paidAmount: Double = 0.0,
    val paidAt: Long? = null,
    val status: Status = Status.PENDING,
    val paymentId: String? = null,
) {
    enum class Status { PENDING, PAID, OVERDUE, PARTIAL }
}

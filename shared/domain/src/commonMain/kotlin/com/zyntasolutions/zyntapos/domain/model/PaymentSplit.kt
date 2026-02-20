package com.zyntasolutions.zyntapos.domain.model

/**
 * Represents one leg of a split payment.
 *
 * When an [Order] is paid with [PaymentMethod.SPLIT], its `paymentMethod` is
 * set to `SPLIT` and the individual legs are recorded here.
 * The sum of all [PaymentSplit.amount] values must equal [Order.total].
 *
 * Validated by `PaymentValidator` before `ProcessPaymentUseCase` persists the order.
 *
 * @property method The tender type for this portion of the payment.
 * @property amount The amount paid via [method]. Must be > 0.
 */
data class PaymentSplit(
    val method: PaymentMethod,
    val amount: Double,
) {
    init {
        require(amount > 0.0) { "Split amount must be positive, got $amount" }
        require(method != PaymentMethod.SPLIT) {
            "PaymentMethod.SPLIT cannot be used inside a PaymentSplit leg"
        }
    }
}

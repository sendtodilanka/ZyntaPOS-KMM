package com.zyntasolutions.zyntapos.domain.validation

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit

/**
 * Validates payment-related business rules for the POS payment flow.
 *
 * All methods return [Result.Success] with [Unit] on pass, or
 * [Result.Error] wrapping a [ValidationException] on failure.
 *
 * These validators are pure functions (no I/O) and may be called synchronously.
 */
object PaymentValidator {

    /**
     * Validates that the customer has tendered enough for a non-split payment.
     *
     * ### Rules
     * 1. [amountTendered] must be ≥ [orderTotal] for CASH payments.
     * 2. For CARD/MOBILE/BANK_TRANSFER, [amountTendered] is set equal to the total
     *    by the caller — this method verifies it is not negative.
     *
     * @param amountTendered Amount presented by the customer.
     * @param orderTotal     The grand total the customer must pay.
     * @param paymentMethod  The payment method being validated.
     * @return [Result.Success] or [Result.Error].
     */
    fun validateTender(
        amountTendered: Double,
        orderTotal: Double,
        paymentMethod: PaymentMethod,
    ): Result<Unit> {
        if (amountTendered < 0.0) {
            return Result.Error(
                ValidationException(
                    "Amount tendered cannot be negative. Got $amountTendered.",
                    field = "amountTendered",
                    rule = "MIN_VALUE",
                ),
            )
        }

        if (paymentMethod == PaymentMethod.CASH && amountTendered < orderTotal) {
            return Result.Error(
                ValidationException(
                    "Insufficient tender. Order total: $orderTotal, tendered: $amountTendered.",
                    field = "amountTendered",
                    rule = "INSUFFICIENT_TENDER",
                ),
            )
        }

        return Result.Success(Unit)
    }

    /**
     * Validates that split payment legs sum exactly to the order total.
     *
     * ### Rules
     * 1. [splits] must not be empty.
     * 2. Each split amount must be > 0.
     * 3. Sum of all split amounts must equal [orderTotal] (± [TOLERANCE] for float rounding).
     * 4. [PaymentMethod.SPLIT] is not allowed as a split leg method (would cause recursion).
     *
     * @param splits     The list of [PaymentSplit] legs.
     * @param orderTotal The expected grand total.
     * @return [Result.Success] or [Result.Error].
     */
    fun validateSplitPayment(
        splits: List<PaymentSplit>,
        orderTotal: Double,
    ): Result<Unit> {
        if (splits.isEmpty()) {
            return Result.Error(
                ValidationException("Split payment must have at least one leg.", field = "splits", rule = "EMPTY"),
            )
        }

        for ((index, split) in splits.withIndex()) {
            if (split.amount <= 0.0) {
                return Result.Error(
                    ValidationException(
                        "Split leg $index amount must be > 0. Got ${split.amount}.",
                        field = "splits[$index].amount",
                        rule = "MIN_VALUE",
                    ),
                )
            }
            if (split.method == PaymentMethod.SPLIT) {
                return Result.Error(
                    ValidationException(
                        "SPLIT is not a valid method for an individual payment leg.",
                        field = "splits[$index].method",
                        rule = "INVALID_SPLIT_METHOD",
                    ),
                )
            }
        }

        val splitTotal = splits.sumOf { it.amount }
        if (kotlin.math.abs(splitTotal - orderTotal) > TOLERANCE) {
            return Result.Error(
                ValidationException(
                    "Split amounts sum ($splitTotal) does not equal order total ($orderTotal).",
                    field = "splits",
                    rule = "SPLIT_SUM_MISMATCH",
                ),
            )
        }

        return Result.Success(Unit)
    }

    /** Floating-point tolerance for split payment sum comparison. */
    const val TOLERANCE = 0.001
}

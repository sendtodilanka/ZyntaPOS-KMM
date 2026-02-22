package com.zyntasolutions.zyntapos.domain.model

/**
 * An immutable computed value object summarising the financial totals of an order or cart.
 *
 * Produced exclusively by `CalculateOrderTotalsUseCase`. All values are rounded to 2
 * decimal places using [RoundingMode.HALF_UP] semantics.
 *
 * **Tax calculation scenarios handled (see §11.3 of the architecture plan):**
 * 1. No tax group assigned → taxAmount = 0.0
 * 2. Exclusive tax, no discount
 * 3. Exclusive tax, with item discount
 * 4. Exclusive tax, with order discount
 * 5. Inclusive tax, no discount
 * 6. Inclusive tax, with discount
 *
 * @property subtotal Sum of `(unitPrice × quantity - itemDiscount)` across all items,
 *                   before tax and order-level discount. Always ≥ 0.
 * @property taxAmount Total tax amount across all items (rounded to 2 d.p.). Always ≥ 0.
 * @property discountAmount Total order-level discount applied. Always ≥ 0.
 * @property total Grand total: `subtotal + taxAmount - discountAmount`. Always ≥ 0.
 * @property itemCount Total number of line items (cart rows) in the order.
 */
data class OrderTotals(
    val subtotal: Double,
    val taxAmount: Double,
    val discountAmount: Double,
    val total: Double,
    val itemCount: Int,
) {
    companion object {
        /** A zeroed-out totals object — useful as an initial state for an empty cart. */
        val EMPTY = OrderTotals(
            subtotal = 0.0,
            taxAmount = 0.0,
            discountAmount = 0.0,
            total = 0.0,
            itemCount = 0,
        )
    }
}

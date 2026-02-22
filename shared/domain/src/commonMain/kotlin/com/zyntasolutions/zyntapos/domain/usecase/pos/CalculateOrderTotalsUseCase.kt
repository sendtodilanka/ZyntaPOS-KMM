package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.OrderTotals
import kotlin.math.roundToInt

/**
 * Calculates the complete order financial totals from a list of [CartItem]s.
 *
 * ### Tax Calculation Scenarios (§11.3)
 *
 * This use case handles **6 tax scenarios**:
 *
 * **Scenario 1 — No Tax (rate = 0%)**
 * ```
 * lineTotal = unitPrice × qty − discountAmount
 * taxAmount = 0.0
 * ```
 *
 * **Scenario 2 — Exclusive Tax (isInclusive = false)**
 * ```
 * baseAmount = unitPrice × qty − discountAmount
 * taxAmount  = baseAmount × (taxRate / 100)
 * lineTotal  = baseAmount + taxAmount
 * ```
 *
 * **Scenario 3 — Inclusive Tax (isInclusive = true)**
 * ```
 * grossAmount = unitPrice × qty − discountAmount
 * taxAmount   = grossAmount − (grossAmount / (1 + taxRate / 100))
 * lineTotal   = grossAmount   // tax is already contained within the price
 * ```
 *
 * **Scenario 4 — Item-level Fixed Discount + Exclusive Tax**
 * ```
 * baseAmount = unitPrice × qty − fixedDiscount
 * taxAmount  = baseAmount × (taxRate / 100)
 * lineTotal  = baseAmount + taxAmount
 * ```
 *
 * **Scenario 5 — Item-level Percent Discount + Exclusive Tax**
 * ```
 * discountAmount = unitPrice × qty × (discount / 100)
 * baseAmount     = unitPrice × qty − discountAmount
 * taxAmount      = baseAmount × (taxRate / 100)
 * lineTotal      = baseAmount + taxAmount
 * ```
 *
 * **Scenario 6 — Order-level Discount applied after per-item tax**
 * ```
 * // Order discount reduces the subtotal (not per-item tax).
 * // Tax is calculated before the order-level discount.
 * subtotal       = sum of all item basePrices (before order discount)
 * totalItemTax   = sum of all item taxAmounts
 * orderDiscount  = subtotal × (discountPct / 100)  OR fixed amount
 * total          = subtotal + totalItemTax − orderDiscount
 * ```
 *
 * **Rounding:** All monetary values are rounded to **2 decimal places** using
 * `HALF_UP` semantics (multiply by 100, round to nearest Int, divide by 100).
 *
 * @param items        Cart line items (each carries its own [CartItem.taxRate]).
 * @param orderDiscount Order-level discount value (0.0 = no discount).
 * @param orderDiscountType Whether [orderDiscount] is [DiscountType.PERCENT] or [DiscountType.FIXED].
 * @param taxInclusive  When `true`, item prices are treated as tax-inclusive (Scenario 3).
 *                      When `false`, tax is added on top (Scenarios 2/4/5).
 * @return [Result.Success] with fully computed [OrderTotals].
 */
class CalculateOrderTotalsUseCase {
    operator fun invoke(
        items: List<CartItem>,
        orderDiscount: Double = 0.0,
        orderDiscountType: DiscountType = DiscountType.FIXED,
        taxInclusive: Boolean = false,
    ): Result<OrderTotals> {
        var subtotalBeforeTax = 0.0
        var totalTax = 0.0

        items.forEach { item ->
            val rawLine = item.unitPrice * item.quantity

            // Resolve item-level discount amount
            val itemDiscountAmt = when (item.discountType) {
                DiscountType.FIXED -> item.discount
                DiscountType.PERCENT -> rawLine * (item.discount / 100.0)
            }

            val baseAmount = rawLine - itemDiscountAmt

            val taxAmt = if (taxInclusive && item.taxRate > 0.0) {
                // Scenario 3: inclusive — extract tax from gross
                baseAmount - (baseAmount / (1.0 + item.taxRate / 100.0))
            } else {
                // Scenarios 1, 2, 4, 5: exclusive or zero-rate
                baseAmount * (item.taxRate / 100.0)
            }

            subtotalBeforeTax += baseAmount
            totalTax += taxAmt
        }

        // Order-level discount (Scenario 6) applied to subtotal (pre-tax base)
        val orderDiscountAmt = when (orderDiscountType) {
            DiscountType.FIXED -> orderDiscount
            DiscountType.PERCENT -> subtotalBeforeTax * (orderDiscount / 100.0)
        }

        // For inclusive tax, the tax is already contained within subtotalBeforeTax,
        // so we must not add totalTax again (that would double-count).
        val total = if (taxInclusive) {
            subtotalBeforeTax - orderDiscountAmt
        } else {
            subtotalBeforeTax + totalTax - orderDiscountAmt
        }

        return Result.Success(
            OrderTotals(
                subtotal = roundHalfUp(subtotalBeforeTax),
                taxAmount = roundHalfUp(totalTax),
                discountAmount = roundHalfUp(orderDiscountAmt),
                total = roundHalfUp(total),
                itemCount = items.size,
            ),
        )
    }

    /** Rounds [value] to 2 decimal places using HALF_UP semantics. */
    private fun roundHalfUp(value: Double): Double =
        (value * 100.0).roundToInt() / 100.0
}

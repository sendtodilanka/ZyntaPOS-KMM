package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.CompoundTaxComponent
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
 * @param items        Cart line items (each carries its own [CartItem.taxRate] and
 *                     [CartItem.isTaxInclusive] resolved from its TaxGroup + regional override).
 * @param orderDiscount Order-level discount value (0.0 = no discount).
 * @param orderDiscountType Whether [orderDiscount] is [DiscountType.PERCENT] or [DiscountType.FIXED].
 * @param taxInclusive  **Deprecated fallback.** Per-item [CartItem.isTaxInclusive] takes precedence.
 *                      This global flag is used ONLY for items where `isTaxInclusive` is `false`
 *                      AND the caller explicitly passes `taxInclusive = true` (backward compatibility).
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
        // Track exclusive-tax line totals separately for correct grand total
        var exclusiveTaxTotal = 0.0

        items.forEach { item ->
            val rawLine = item.unitPrice * item.quantity

            // Resolve item-level discount amount
            val itemDiscountAmt = when (item.discountType) {
                DiscountType.FIXED -> item.discount
                DiscountType.PERCENT -> rawLine * (item.discount / 100.0)
                DiscountType.BOGO -> 0.0 // BOGO qty handled upstream; discount amount already 0 here
            }

            val baseAmount = rawLine - itemDiscountAmt

            // Per-item isTaxInclusive takes precedence; fall back to global taxInclusive flag
            val itemTaxInclusive = item.isTaxInclusive || taxInclusive

            val taxAmt = if (item.compoundTaxComponents.isNotEmpty()) {
                // Compound tax (C2.3): apply multiple stacked tax components
                calculateCompoundTax(baseAmount, item.compoundTaxComponents, itemTaxInclusive).also { compoundTax ->
                    // For compound taxes, check if any component is exclusive
                    val hasExclusiveComponent = item.compoundTaxComponents.any { !it.componentIsInclusive }
                    if (hasExclusiveComponent && !itemTaxInclusive) {
                        exclusiveTaxTotal += compoundTax
                    }
                }
            } else if (itemTaxInclusive && item.taxRate > 0.0) {
                // Scenario 3: inclusive — extract tax from gross
                baseAmount - (baseAmount / (1.0 + item.taxRate / 100.0))
            } else {
                // Scenarios 1, 2, 4, 5: exclusive or zero-rate
                val excTax = baseAmount * (item.taxRate / 100.0)
                exclusiveTaxTotal += excTax
                excTax
            }

            subtotalBeforeTax += baseAmount
            totalTax += taxAmt
        }

        // Order-level discount (Scenario 6) applied to subtotal (pre-tax base)
        val orderDiscountAmt = when (orderDiscountType) {
            DiscountType.FIXED -> orderDiscount
            DiscountType.PERCENT -> subtotalBeforeTax * (orderDiscount / 100.0)
            DiscountType.BOGO -> 0.0 // BOGO applied per-item; no extra order-level deduction
        }

        // For items with inclusive tax, tax is already contained within subtotalBeforeTax
        // (no need to add it). For items with exclusive tax, we must add the tax on top.
        // Mixed carts: subtotal includes gross of inclusive items + net of exclusive items.
        // We add only the exclusive tax portion to avoid double-counting inclusive tax.
        val total = subtotalBeforeTax + exclusiveTaxTotal - orderDiscountAmt

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

    /**
     * Calculates compound tax for stacked tax components (C2.3).
     *
     * Components are applied in [CompoundTaxComponent.applicationOrder] sequence.
     * - **Compounding (tax-on-tax):** Each rate applies to the running total
     *   (e.g., Service Charge on VAT-inclusive amount).
     * - **Additive (parallel):** Each rate applies to the original base amount.
     *
     * For inclusive components, tax is extracted from the base (back-calculation).
     * For exclusive components, tax is added on top.
     *
     * @return Total compound tax amount across all components.
     */
    private fun calculateCompoundTax(
        baseAmount: Double,
        components: List<CompoundTaxComponent>,
        globalInclusive: Boolean,
    ): Double {
        var totalCompoundTax = 0.0
        var runningAmount = baseAmount

        for (component in components.sortedBy { it.applicationOrder }) {
            val isInclusive = component.componentIsInclusive || globalInclusive

            val componentTax = if (isInclusive && component.componentRate > 0.0) {
                // Extract tax from the running amount (inclusive)
                val taxBase = if (component.isCompounding) runningAmount else baseAmount
                taxBase - (taxBase / (1.0 + component.componentRate / 100.0))
            } else {
                // Add tax on top (exclusive)
                val taxBase = if (component.isCompounding) runningAmount else baseAmount
                taxBase * (component.componentRate / 100.0)
            }

            totalCompoundTax += componentTax

            // For compounding, update the running amount so next component
            // calculates on the tax-inclusive running total
            if (component.isCompounding && !isInclusive) {
                runningAmount += componentTax
            }
        }

        return totalCompoundTax
    }

    /** Rounds [value] to 2 decimal places using HALF_UP semantics. */
    private fun roundHalfUp(value: Double): Double =
        (value * 100.0).roundToInt() / 100.0
}

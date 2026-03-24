package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [CalculateOrderTotalsUseCase] covering all 6 tax scenarios from §11.3.
 *
 * Rounding tolerance: ±0.005 (values rounded to 2dp HALF_UP).
 */
class CalculateOrderTotalsUseCaseTest {

    private val useCase = CalculateOrderTotalsUseCase()

    // ─── Scenario 1: No Tax (rate = 0%) ───────────────────────────────────────

    @Test
    fun `scenario 1 - no tax, exclusive, zero rate - lineTotal equals baseAmount`() {
        val items = listOf(
            buildCartItem(unitPrice = 100.0, quantity = 2.0, taxRate = 0.0),
        )
        val result = useCase(items)
        assertIs<Result.Success<*>>(result)
        val totals = (result as Result.Success).data
        assertEquals(200.0, totals.subtotal, 0.005)
        assertEquals(0.0, totals.taxAmount, 0.005)
        assertEquals(200.0, totals.total, 0.005)
        assertEquals(0.0, totals.discountAmount, 0.005)
        assertEquals(1, totals.itemCount)
    }

    @Test
    fun `scenario 1 - no tax, multiple items - totals aggregated correctly`() {
        val items = listOf(
            buildCartItem(productId = "p1", unitPrice = 50.0, quantity = 1.0, taxRate = 0.0),
            buildCartItem(productId = "p2", unitPrice = 30.0, quantity = 2.0, taxRate = 0.0),
        )
        val result = useCase(items) as Result.Success
        assertEquals(110.0, result.data.subtotal, 0.005)
        assertEquals(0.0, result.data.taxAmount, 0.005)
        assertEquals(110.0, result.data.total, 0.005)
    }

    // ─── Scenario 2: Exclusive Tax (isInclusive = false) ─────────────────────

    @Test
    fun `scenario 2 - exclusive tax 10pct - tax added on top of base price`() {
        val items = listOf(
            buildCartItem(unitPrice = 100.0, quantity = 1.0, taxRate = 10.0),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(100.0, result.data.subtotal, 0.005)
        assertEquals(10.0, result.data.taxAmount, 0.005)
        assertEquals(110.0, result.data.total, 0.005)
    }

    @Test
    fun `scenario 2 - exclusive tax 15pct, multiple items - taxes summed correctly`() {
        val items = listOf(
            buildCartItem(productId = "p1", unitPrice = 200.0, quantity = 1.0, taxRate = 15.0),
            buildCartItem(productId = "p2", unitPrice = 100.0, quantity = 2.0, taxRate = 15.0),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        // subtotal = 200 + 200 = 400; tax = 60; total = 460
        assertEquals(400.0, result.data.subtotal, 0.005)
        assertEquals(60.0, result.data.taxAmount, 0.005)
        assertEquals(460.0, result.data.total, 0.005)
    }

    // ─── Scenario 3: Inclusive Tax (isInclusive = true) ──────────────────────

    @Test
    fun `scenario 3 - inclusive tax 10pct - tax extracted from gross price`() {
        // grossAmount = 110, inclusive tax 10% → taxAmt = 110 - (110 / 1.1) = 10
        val items = listOf(
            buildCartItem(unitPrice = 110.0, quantity = 1.0, taxRate = 10.0),
        )
        val result = useCase(items, taxInclusive = true) as Result.Success
        assertEquals(110.0, result.data.subtotal, 0.005)  // base is gross
        assertEquals(10.0, result.data.taxAmount, 0.005)   // tax extracted
        assertEquals(110.0, result.data.total, 0.005)       // total = gross (tax included)
    }

    @Test
    fun `scenario 3 - inclusive tax 18pct - tax extracted with correct rounding`() {
        // gross = 118, tax = 118 - (118 / 1.18) = 118 - 100 = 18
        val items = listOf(
            buildCartItem(unitPrice = 118.0, quantity = 1.0, taxRate = 18.0),
        )
        val result = useCase(items, taxInclusive = true) as Result.Success
        assertEquals(18.0, result.data.taxAmount, 0.005)
        assertEquals(118.0, result.data.total, 0.005)
    }

    // ─── Scenario 4: Item-level Fixed Discount + Exclusive Tax ───────────────

    @Test
    fun `scenario 4 - fixed item discount + exclusive tax - tax applied to discounted base`() {
        // unitPrice=100, qty=1, fixedDiscount=10 → base=90, tax=9 (10%), total=99
        val items = listOf(
            buildCartItem(unitPrice = 100.0, quantity = 1.0,
                discount = 10.0, discountType = DiscountType.FIXED, taxRate = 10.0),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(90.0, result.data.subtotal, 0.005)
        assertEquals(9.0, result.data.taxAmount, 0.005)
        assertEquals(99.0, result.data.total, 0.005)
    }

    @Test
    fun `scenario 4 - fixed discount removes item completely when discount equals price`() {
        val items = listOf(
            buildCartItem(unitPrice = 50.0, quantity = 1.0,
                discount = 50.0, discountType = DiscountType.FIXED, taxRate = 10.0),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(0.0, result.data.subtotal, 0.005)
        assertEquals(0.0, result.data.taxAmount, 0.005)
        assertEquals(0.0, result.data.total, 0.005)
    }

    // ─── Scenario 5: Item-level Percent Discount + Exclusive Tax ─────────────

    @Test
    fun `scenario 5 - percent item discount 20pct + exclusive tax 10pct`() {
        // price=100, qty=2 → raw=200, discount=40 (20%), base=160, tax=16, total=176
        val items = listOf(
            buildCartItem(unitPrice = 100.0, quantity = 2.0,
                discount = 20.0, discountType = DiscountType.PERCENT, taxRate = 10.0),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(160.0, result.data.subtotal, 0.005)
        assertEquals(16.0, result.data.taxAmount, 0.005)
        assertEquals(176.0, result.data.total, 0.005)
    }

    @Test
    fun `scenario 5 - percent discount 100pct results in zero total`() {
        val items = listOf(
            buildCartItem(unitPrice = 100.0, quantity = 1.0,
                discount = 100.0, discountType = DiscountType.PERCENT, taxRate = 15.0),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(0.0, result.data.total, 0.005)
    }

    // ─── Scenario 6: Order-level Discount ────────────────────────────────────

    @Test
    fun `scenario 6 - order percent discount applied after per-item tax`() {
        // items: 100 × 1 (10% tax) → subtotal=100, tax=10
        // order discount 10% → discountAmt = 100 × 10% = 10
        // total = 100 + 10 - 10 = 100
        val items = listOf(
            buildCartItem(unitPrice = 100.0, quantity = 1.0, taxRate = 10.0),
        )
        val result = useCase(items,
            orderDiscount = 10.0,
            orderDiscountType = DiscountType.PERCENT,
            taxInclusive = false
        ) as Result.Success
        assertEquals(100.0, result.data.subtotal, 0.005)
        assertEquals(10.0, result.data.taxAmount, 0.005)
        assertEquals(10.0, result.data.discountAmount, 0.005)
        assertEquals(100.0, result.data.total, 0.005)
    }

    @Test
    fun `scenario 6 - order fixed discount applied correctly`() {
        // items: 200 × 1 (0% tax) → subtotal=200, order fixed discount=50
        // total = 200 + 0 - 50 = 150
        val items = listOf(
            buildCartItem(unitPrice = 200.0, quantity = 1.0, taxRate = 0.0),
        )
        val result = useCase(items,
            orderDiscount = 50.0,
            orderDiscountType = DiscountType.FIXED,
            taxInclusive = false
        ) as Result.Success
        assertEquals(50.0, result.data.discountAmount, 0.005)
        assertEquals(150.0, result.data.total, 0.005)
    }

    @Test
    fun `scenario 6 - order discount does not affect per-item tax calculation`() {
        // Tax calculated before order discount is applied
        val items = listOf(
            buildCartItem(unitPrice = 100.0, quantity = 2.0, taxRate = 10.0),
        )
        val result = useCase(items,
            orderDiscount = 20.0,
            orderDiscountType = DiscountType.PERCENT,
            taxInclusive = false
        ) as Result.Success
        // subtotal before tax = 200; tax = 20 (10% of 200)
        // order discount = 40 (20% of subtotal 200)
        // total = 200 + 20 - 40 = 180
        assertEquals(200.0, result.data.subtotal, 0.005)
        assertEquals(20.0, result.data.taxAmount, 0.005)
        assertEquals(40.0, result.data.discountAmount, 0.005)
        assertEquals(180.0, result.data.total, 0.005)
    }

    // ─── Rounding ─────────────────────────────────────────────────────────────

    @Test
    fun `rounding to 2 decimal places using HALF_UP`() {
        // 1/3 price → 33.333... should round to 33.33
        val items = listOf(
            buildCartItem(unitPrice = 33.333, quantity = 1.0, taxRate = 0.0),
        )
        val result = useCase(items) as Result.Success
        assertEquals(33.33, result.data.subtotal, 0.005)
    }

    // ─── Edge Cases ───────────────────────────────────────────────────────────

    @Test
    fun `empty cart returns zeroed totals`() {
        val result = useCase(emptyList()) as Result.Success
        assertEquals(0.0, result.data.subtotal, 0.0)
        assertEquals(0.0, result.data.taxAmount, 0.0)
        assertEquals(0.0, result.data.total, 0.0)
        assertEquals(0, result.data.itemCount)
    }

    @Test
    fun `itemCount matches number of cart lines`() {
        val items = listOf(
            buildCartItem(productId = "p1"),
            buildCartItem(productId = "p2"),
            buildCartItem(productId = "p3"),
        )
        val result = useCase(items) as Result.Success
        assertEquals(3, result.data.itemCount)
    }

    // ─── Per-Item isTaxInclusive (C2.3) ────────────────────────────────────────

    @Test
    fun `per-item isTaxInclusive true - tax extracted from gross`() {
        // 110 × 1, inclusive 10% → tax = 10, total = 110 (tax is within price)
        val items = listOf(
            buildCartItem(unitPrice = 110.0, quantity = 1.0, taxRate = 10.0, isTaxInclusive = true),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(110.0, result.data.subtotal, 0.005)
        assertEquals(10.0, result.data.taxAmount, 0.005)
        assertEquals(110.0, result.data.total, 0.005) // inclusive — total = subtotal
    }

    @Test
    fun `per-item isTaxInclusive false - tax added on top`() {
        // 100 × 1, exclusive 10% → tax = 10, total = 110
        val items = listOf(
            buildCartItem(unitPrice = 100.0, quantity = 1.0, taxRate = 10.0, isTaxInclusive = false),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(100.0, result.data.subtotal, 0.005)
        assertEquals(10.0, result.data.taxAmount, 0.005)
        assertEquals(110.0, result.data.total, 0.005)
    }

    @Test
    fun `mixed inclusive and exclusive items - totals calculated correctly`() {
        // Item 1: 110 inclusive 10% → tax = 10, base = 110
        // Item 2: 100 exclusive 10% → tax = 10, base = 100
        // subtotal = 210, totalTax = 20, total = 210 + 10 (exclusive only) = 220
        val items = listOf(
            buildCartItem(productId = "p1", unitPrice = 110.0, quantity = 1.0,
                taxRate = 10.0, isTaxInclusive = true),
            buildCartItem(productId = "p2", unitPrice = 100.0, quantity = 1.0,
                taxRate = 10.0, isTaxInclusive = false),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(210.0, result.data.subtotal, 0.005)
        assertEquals(20.0, result.data.taxAmount, 0.005)
        // total = 210 (subtotal) + 10 (exclusive tax only) = 220
        assertEquals(220.0, result.data.total, 0.005)
    }

    @Test
    fun `per-item inclusive overrides global taxInclusive false`() {
        // Even though global taxInclusive=false, item's isTaxInclusive=true takes effect
        val items = listOf(
            buildCartItem(unitPrice = 110.0, quantity = 1.0, taxRate = 10.0, isTaxInclusive = true),
        )
        val result = useCase(items, taxInclusive = false) as Result.Success
        assertEquals(110.0, result.data.total, 0.005) // inclusive — no extra tax on top
    }
}

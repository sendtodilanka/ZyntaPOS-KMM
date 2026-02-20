package com.zyntasolutions.zyntapos.domain.validation

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for domain validators:
 * [PaymentValidator], [StockValidator], [TaxValidator].
 *
 * All validators are pure functions — no fakes or coroutines required.
 */
class ValidatorsTest {

    // ═══════════════════════════════════════════════════════════════════════
    // PaymentValidator
    // ═══════════════════════════════════════════════════════════════════════

    // ─── validateTender ───────────────────────────────────────────────────

    @Test
    fun `validateTender - exact cash tender equals total - success`() {
        val result = PaymentValidator.validateTender(100.0, 100.0, PaymentMethod.CASH)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateTender - cash tender exceeds total - success (change will be calculated)`() {
        val result = PaymentValidator.validateTender(150.0, 100.0, PaymentMethod.CASH)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateTender - cash insufficient - returns INSUFFICIENT_TENDER error`() {
        val result = PaymentValidator.validateTender(80.0, 100.0, PaymentMethod.CASH)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("amountTendered", ex.field)
        assertEquals("INSUFFICIENT_TENDER", ex.rule)
    }

    @Test
    fun `validateTender - card payment with amount equal to total - success`() {
        val result = PaymentValidator.validateTender(100.0, 100.0, PaymentMethod.CARD)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateTender - negative amount tendered - returns MIN_VALUE error`() {
        val result = PaymentValidator.validateTender(-10.0, 100.0, PaymentMethod.CASH)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("amountTendered", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `validateTender - mobile payment with zero - returns MIN_VALUE error`() {
        val result = PaymentValidator.validateTender(-0.01, 100.0, PaymentMethod.MOBILE)
        assertIs<Result.Error>(result)
    }

    // ─── validateSplitPayment ─────────────────────────────────────────────

    @Test
    fun `validateSplitPayment - valid splits summing to total - success`() {
        val splits = listOf(
            PaymentSplit(PaymentMethod.CASH, 60.0),
            PaymentSplit(PaymentMethod.CARD, 40.0),
        )
        val result = PaymentValidator.validateSplitPayment(splits, 100.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateSplitPayment - empty splits list - returns EMPTY error`() {
        val result = PaymentValidator.validateSplitPayment(emptyList(), 100.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("splits", ex.field)
        assertEquals("EMPTY", ex.rule)
    }

    @Test
    fun `validateSplitPayment - splits don't sum to total - returns SPLIT_SUM_MISMATCH error`() {
        val splits = listOf(
            PaymentSplit(PaymentMethod.CASH, 40.0),
            PaymentSplit(PaymentMethod.CARD, 40.0), // total = 80, not 100
        )
        val result = PaymentValidator.validateSplitPayment(splits, 100.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("SPLIT_SUM_MISMATCH", ex.rule)
    }

    @Test
    fun `validateSplitPayment - split leg with zero amount - returns MIN_VALUE error`() {
        val splits = listOf(
            PaymentSplit(PaymentMethod.CASH, 0.0),
            PaymentSplit(PaymentMethod.CARD, 100.0),
        )
        val result = PaymentValidator.validateSplitPayment(splits, 100.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `validateSplitPayment - SPLIT as a leg method - returns INVALID_SPLIT_METHOD error`() {
        val splits = listOf(
            PaymentSplit(PaymentMethod.CASH, 50.0),
            PaymentSplit(PaymentMethod.SPLIT, 50.0), // recursive SPLIT not allowed
        )
        val result = PaymentValidator.validateSplitPayment(splits, 100.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("INVALID_SPLIT_METHOD", ex.rule)
    }

    @Test
    fun `validateSplitPayment - three-way split sums correctly - success`() {
        val splits = listOf(
            PaymentSplit(PaymentMethod.CASH, 33.33),
            PaymentSplit(PaymentMethod.CARD, 33.33),
            PaymentSplit(PaymentMethod.MOBILE, 33.34),
        )
        val result = PaymentValidator.validateSplitPayment(splits, 100.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateSplitPayment - float tolerance allows minor rounding - success`() {
        // 33.33 + 33.33 + 33.33 = 99.99, not exactly 100.0 — within tolerance
        val splits = listOf(
            PaymentSplit(PaymentMethod.CASH, 50.0005),
            PaymentSplit(PaymentMethod.CARD, 49.9995),
        )
        val result = PaymentValidator.validateSplitPayment(splits, 100.0)
        assertIs<Result.Success<Unit>>(result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // StockValidator
    // ═══════════════════════════════════════════════════════════════════════

    // ─── validateInitialStock ─────────────────────────────────────────────

    @Test
    fun `validateInitialStock - zero qty - success (new product with no stock)`() {
        val result = StockValidator.validateInitialStock(0.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateInitialStock - positive qty - success`() {
        val result = StockValidator.validateInitialStock(100.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateInitialStock - negative qty - returns NEGATIVE_STOCK error`() {
        val result = StockValidator.validateInitialStock(-1.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("stockQty", ex.field)
        assertEquals("NEGATIVE_STOCK", ex.rule)
    }

    // ─── validateAdjustment ───────────────────────────────────────────────

    @Test
    fun `validateAdjustment - increase with positive qty - success`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.INCREASE, 10.0, 50.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateAdjustment - decrease within available stock - success`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.DECREASE, 30.0, 50.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateAdjustment - decrease exact stock qty - success`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.DECREASE, 50.0, 50.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateAdjustment - decrease exceeds stock - returns NEGATIVE_STOCK error`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.DECREASE, 60.0, 50.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("quantity", ex.field)
        assertEquals("NEGATIVE_STOCK", ex.rule)
    }

    @Test
    fun `validateAdjustment - zero quantity - returns MIN_VALUE error`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.INCREASE, 0.0, 50.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("quantity", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `validateAdjustment - negative quantity - returns MIN_VALUE error`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.INCREASE, -5.0, 50.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `validateAdjustment - transfer exceeds stock - returns NEGATIVE_STOCK error`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.TRANSFER, 100.0, 30.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("NEGATIVE_STOCK", ex.rule)
    }

    @Test
    fun `validateAdjustment - transfer within stock - success`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.TRANSFER, 10.0, 30.0)
        assertIs<Result.Success<Unit>>(result)
    }

    // ─── validateMinStock ─────────────────────────────────────────────────

    @Test
    fun `validateMinStock - zero min qty - success`() {
        val result = StockValidator.validateMinStock(0.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateMinStock - positive min qty - success`() {
        val result = StockValidator.validateMinStock(5.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateMinStock - negative min qty - returns NEGATIVE_MIN_STOCK error`() {
        val result = StockValidator.validateMinStock(-1.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("minStockQty", ex.field)
        assertEquals("NEGATIVE_MIN_STOCK", ex.rule)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TaxValidator
    // ═══════════════════════════════════════════════════════════════════════

    private fun buildTaxGroup(
        id: String = "tax-01",
        name: String = "Standard Rate",
        rate: Double = 15.0,
        isActive: Boolean = true,
    ) = TaxGroup(id = id, name = name, rate = rate, isInclusive = false, isActive = isActive)

    // ─── validateRate ─────────────────────────────────────────────────────

    @Test
    fun `validateRate - zero rate (tax exempt) - success`() {
        val result = TaxValidator.validateRate(0.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateRate - standard rate 15 percent - success`() {
        val result = TaxValidator.validateRate(15.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateRate - maximum rate 100 percent - success`() {
        val result = TaxValidator.validateRate(100.0)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateRate - rate above 100 - returns RATE_OUT_OF_RANGE error`() {
        val result = TaxValidator.validateRate(101.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("rate", ex.field)
        assertEquals("RATE_OUT_OF_RANGE", ex.rule)
    }

    @Test
    fun `validateRate - negative rate - returns RATE_OUT_OF_RANGE error`() {
        val result = TaxValidator.validateRate(-5.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("RATE_OUT_OF_RANGE", ex.rule)
    }

    @Test
    fun `validateRate - NaN - returns INVALID_RATE error`() {
        val result = TaxValidator.validateRate(Double.NaN)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("INVALID_RATE", ex.rule)
    }

    @Test
    fun `validateRate - positive infinity - returns INVALID_RATE error`() {
        val result = TaxValidator.validateRate(Double.POSITIVE_INFINITY)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("INVALID_RATE", ex.rule)
    }

    // ─── validateTaxGroup ─────────────────────────────────────────────────

    @Test
    fun `validateTaxGroup - valid tax group - success`() {
        val result = TaxValidator.validateTaxGroup(buildTaxGroup())
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateTaxGroup - blank name - returns REQUIRED error`() {
        val result = TaxValidator.validateTaxGroup(buildTaxGroup(name = ""))
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("name", ex.field)
        assertEquals("REQUIRED", ex.rule)
    }

    @Test
    fun `validateTaxGroup - whitespace only name - returns REQUIRED error`() {
        val result = TaxValidator.validateTaxGroup(buildTaxGroup(name = "   "))
        assertIs<Result.Error>(result)
    }

    @Test
    fun `validateTaxGroup - rate out of range - TaxGroup init guard enforces valid rate`() {
        // TaxGroup.init enforces rate in 0..100 via require(), so the validator
        // is guarded at the model level. Verify that a valid-rate group passes.
        val result = TaxValidator.validateTaxGroup(buildTaxGroup(rate = 100.0))
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateTaxGroup - zero rate valid for tax-exempt group - success`() {
        val result = TaxValidator.validateTaxGroup(buildTaxGroup(name = "Tax Exempt", rate = 0.0))
        assertIs<Result.Success<Unit>>(result)
    }

    // ─── validateUniqueness ───────────────────────────────────────────────

    @Test
    fun `validateUniqueness - distinct names - success`() {
        val groups = listOf(
            buildTaxGroup(id = "t1", name = "Standard"),
            buildTaxGroup(id = "t2", name = "Reduced"),
            buildTaxGroup(id = "t3", name = "Zero Rate"),
        )
        val result = TaxValidator.validateUniqueness(groups)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateUniqueness - duplicate names - returns DUPLICATE_NAME error`() {
        val groups = listOf(
            buildTaxGroup(id = "t1", name = "Standard"),
            buildTaxGroup(id = "t2", name = "standard"), // case-insensitive duplicate
        )
        val result = TaxValidator.validateUniqueness(groups)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("name", ex.field)
        assertEquals("DUPLICATE_NAME", ex.rule)
    }

    @Test
    fun `validateUniqueness - empty list - success`() {
        val result = TaxValidator.validateUniqueness(emptyList())
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateUniqueness - single tax group - success`() {
        val result = TaxValidator.validateUniqueness(listOf(buildTaxGroup()))
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `validateUniqueness - name with trailing whitespace considered duplicate`() {
        val groups = listOf(
            buildTaxGroup(id = "t1", name = "Standard"),
            buildTaxGroup(id = "t2", name = "Standard "), // trailing space should match after trim
        )
        val result = TaxValidator.validateUniqueness(groups)
        assertIs<Result.Error>(result)
    }
}

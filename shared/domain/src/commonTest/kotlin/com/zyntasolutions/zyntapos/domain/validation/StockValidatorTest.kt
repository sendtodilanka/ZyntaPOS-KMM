package com.zyntasolutions.zyntapos.domain.validation

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * ZyntaPOS — StockValidator Unit Tests (commonTest)
 *
 * All methods under test are pure functions (no I/O).
 *
 * Coverage:
 *  A.  validateInitialStock — zero is valid
 *  B.  validateInitialStock — positive is valid
 *  C.  validateInitialStock — negative returns NEGATIVE_STOCK error
 *  D.  validateAdjustment — INCREASE with positive qty is valid
 *  E.  validateAdjustment — INCREASE with zero qty returns MIN_VALUE error
 *  F.  validateAdjustment — INCREASE with negative qty returns MIN_VALUE error
 *  G.  validateAdjustment — DECREASE exactly to zero is valid
 *  H.  validateAdjustment — DECREASE below zero returns NEGATIVE_STOCK error
 *  I.  validateAdjustment — TRANSFER exactly to zero is valid
 *  J.  validateAdjustment — TRANSFER below zero returns NEGATIVE_STOCK error
 *  K.  validateMinStock — zero is valid
 *  L.  validateMinStock — positive is valid
 *  M.  validateMinStock — negative returns NEGATIVE_MIN_STOCK error
 */
class StockValidatorTest {

    // ── validateInitialStock ──────────────────────────────────────────────────

    @Test
    fun `A - validateInitialStock zero is valid`() {
        assertIs<Result.Success<Unit>>(StockValidator.validateInitialStock(0.0))
    }

    @Test
    fun `B - validateInitialStock positive is valid`() {
        assertIs<Result.Success<Unit>>(StockValidator.validateInitialStock(50.5))
    }

    @Test
    fun `C - validateInitialStock negative returns NEGATIVE_STOCK error`() {
        val result = StockValidator.validateInitialStock(-1.0)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NEGATIVE_STOCK", ex.rule)
        assertEquals("stockQty", ex.field)
    }

    // ── validateAdjustment — INCREASE ─────────────────────────────────────────

    @Test
    fun `D - validateAdjustment INCREASE positive qty is valid`() {
        assertIs<Result.Success<Unit>>(
            StockValidator.validateAdjustment(StockAdjustment.Type.INCREASE, 10.0, 5.0),
        )
    }

    @Test
    fun `E - validateAdjustment INCREASE zero qty returns MIN_VALUE error`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.INCREASE, 0.0, 100.0)
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", (result.exception as ValidationException).rule)
    }

    @Test
    fun `F - validateAdjustment INCREASE negative qty returns MIN_VALUE error`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.INCREASE, -5.0, 100.0)
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", (result.exception as ValidationException).rule)
    }

    // ── validateAdjustment — DECREASE ─────────────────────────────────────────

    @Test
    fun `G - validateAdjustment DECREASE exactly to zero is valid`() {
        assertIs<Result.Success<Unit>>(
            StockValidator.validateAdjustment(StockAdjustment.Type.DECREASE, 10.0, 10.0),
        )
    }

    @Test
    fun `H - validateAdjustment DECREASE below zero returns NEGATIVE_STOCK error`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.DECREASE, 11.0, 10.0)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NEGATIVE_STOCK", ex.rule)
        assertEquals("quantity", ex.field)
    }

    // ── validateAdjustment — TRANSFER ─────────────────────────────────────────

    @Test
    fun `I - validateAdjustment TRANSFER exactly to zero is valid`() {
        assertIs<Result.Success<Unit>>(
            StockValidator.validateAdjustment(StockAdjustment.Type.TRANSFER, 5.0, 5.0),
        )
    }

    @Test
    fun `J - validateAdjustment TRANSFER below zero returns NEGATIVE_STOCK error`() {
        val result = StockValidator.validateAdjustment(StockAdjustment.Type.TRANSFER, 6.0, 5.0)
        assertIs<Result.Error>(result)
        assertEquals("NEGATIVE_STOCK", (result.exception as ValidationException).rule)
    }

    // ── validateMinStock ──────────────────────────────────────────────────────

    @Test
    fun `K - validateMinStock zero is valid`() {
        assertIs<Result.Success<Unit>>(StockValidator.validateMinStock(0.0))
    }

    @Test
    fun `L - validateMinStock positive is valid`() {
        assertIs<Result.Success<Unit>>(StockValidator.validateMinStock(5.0))
    }

    @Test
    fun `M - validateMinStock negative returns NEGATIVE_MIN_STOCK error`() {
        val result = StockValidator.validateMinStock(-0.5)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NEGATIVE_MIN_STOCK", ex.rule)
        assertEquals("minStockQty", ex.field)
    }
}

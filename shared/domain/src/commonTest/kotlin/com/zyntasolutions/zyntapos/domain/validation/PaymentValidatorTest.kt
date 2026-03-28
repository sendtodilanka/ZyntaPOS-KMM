package com.zyntasolutions.zyntapos.domain.validation

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * ZyntaPOS — PaymentValidator Unit Tests (commonTest)
 *
 * All methods under test are pure functions (no I/O).
 *
 * Coverage:
 *  validateTender:
 *  A.  Negative amountTendered returns MIN_VALUE error (any method)
 *  B.  CASH with exact tender is valid
 *  C.  CASH with over-tender is valid
 *  D.  CASH with insufficient tender returns INSUFFICIENT_TENDER error
 *  E.  CARD with zero tender is valid (not CASH — no minimum)
 *  F.  MOBILE with amount == total is valid
 *
 *  validateSplitPayment:
 *  G.  Empty splits list returns EMPTY error
 *  H.  Single valid split matching total returns success
 *  I.  Two valid splits summing to total returns success
 *  J.  Splits sum below total returns SPLIT_SUM_MISMATCH error
 *  K.  Splits sum above total returns SPLIT_SUM_MISMATCH error
 *  L.  Sum within TOLERANCE of total returns success (float rounding tolerance)
 */
class PaymentValidatorTest {

    // ── validateTender ────────────────────────────────────────────────────────

    @Test
    fun `A - negative amountTendered returns MIN_VALUE error`() {
        val result = PaymentValidator.validateTender(-0.01, 100.0, PaymentMethod.CASH)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("MIN_VALUE", ex.rule)
        assertEquals("amountTendered", ex.field)
    }

    @Test
    fun `B - CASH exact tender is valid`() {
        assertIs<Result.Success<Unit>>(
            PaymentValidator.validateTender(100.0, 100.0, PaymentMethod.CASH),
        )
    }

    @Test
    fun `C - CASH over-tender is valid`() {
        assertIs<Result.Success<Unit>>(
            PaymentValidator.validateTender(150.0, 100.0, PaymentMethod.CASH),
        )
    }

    @Test
    fun `D - CASH insufficient tender returns INSUFFICIENT_TENDER error`() {
        val result = PaymentValidator.validateTender(99.99, 100.0, PaymentMethod.CASH)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("INSUFFICIENT_TENDER", ex.rule)
        assertEquals("amountTendered", ex.field)
    }

    @Test
    fun `E - CARD zero amountTendered is valid (no minimum for non-cash)`() {
        // CARD payments have amount set to total by caller — 0.0 passes as long as not negative
        assertIs<Result.Success<Unit>>(
            PaymentValidator.validateTender(0.0, 100.0, PaymentMethod.CARD),
        )
    }

    @Test
    fun `F - MOBILE amount equal to total is valid`() {
        assertIs<Result.Success<Unit>>(
            PaymentValidator.validateTender(250.0, 250.0, PaymentMethod.MOBILE),
        )
    }

    // ── validateSplitPayment ──────────────────────────────────────────────────

    @Test
    fun `G - empty splits list returns EMPTY error`() {
        val result = PaymentValidator.validateSplitPayment(emptyList(), 100.0)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("EMPTY", ex.rule)
        assertEquals("splits", ex.field)
    }

    @Test
    fun `H - single valid split matching total returns success`() {
        val splits = listOf(PaymentSplit(method = PaymentMethod.CASH, amount = 100.0))
        assertIs<Result.Success<Unit>>(PaymentValidator.validateSplitPayment(splits, 100.0))
    }

    @Test
    fun `I - two valid splits summing to total returns success`() {
        val splits = listOf(
            PaymentSplit(method = PaymentMethod.CASH, amount = 60.0),
            PaymentSplit(method = PaymentMethod.CARD, amount = 40.0),
        )
        assertIs<Result.Success<Unit>>(PaymentValidator.validateSplitPayment(splits, 100.0))
    }

    @Test
    fun `J - splits sum below total returns SPLIT_SUM_MISMATCH error`() {
        val splits = listOf(PaymentSplit(method = PaymentMethod.CASH, amount = 50.0))
        val result = PaymentValidator.validateSplitPayment(splits, 100.0)
        assertIs<Result.Error>(result)
        assertEquals("SPLIT_SUM_MISMATCH", (result.exception as ValidationException).rule)
    }

    @Test
    fun `K - splits sum above total returns SPLIT_SUM_MISMATCH error`() {
        val splits = listOf(PaymentSplit(method = PaymentMethod.CASH, amount = 150.0))
        val result = PaymentValidator.validateSplitPayment(splits, 100.0)
        assertIs<Result.Error>(result)
        assertEquals("SPLIT_SUM_MISMATCH", (result.exception as ValidationException).rule)
    }

    @Test
    fun `L - split sum within TOLERANCE of total returns success`() {
        // 100.0001 - 100.0 = 0.0001 < TOLERANCE (0.001) → should pass
        val splits = listOf(PaymentSplit(method = PaymentMethod.CARD, amount = 100.0001))
        assertIs<Result.Success<Unit>>(PaymentValidator.validateSplitPayment(splits, 100.0))
    }
}

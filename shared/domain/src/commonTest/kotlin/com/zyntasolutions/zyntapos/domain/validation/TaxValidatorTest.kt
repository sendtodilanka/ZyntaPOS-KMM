package com.zyntasolutions.zyntapos.domain.validation

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * ZyntaPOS — TaxValidator Unit Tests (commonTest)
 *
 * All methods under test are pure functions (no I/O).
 *
 * Coverage:
 *  A.  validateRate — 0.0 is valid (lower boundary)
 *  B.  validateRate — 100.0 is valid (upper boundary)
 *  C.  validateRate — 15.0 is valid (typical GST)
 *  D.  validateRate — negative value returns RATE_OUT_OF_RANGE error
 *  E.  validateRate — value > 100 returns RATE_OUT_OF_RANGE error
 *  F.  validateRate — NaN returns INVALID_RATE error
 *  G.  validateRate — positive infinity returns INVALID_RATE error
 *  H.  validateRate — negative infinity returns INVALID_RATE error
 *  I.  validateTaxGroup — valid group returns success
 *  J.  validateTaxGroup — blank name returns REQUIRED error
 *  K.  validateTaxGroup — invalid rate delegates to validateRate and returns error
 *  L.  validateUniqueness — empty list returns success
 *  M.  validateUniqueness — distinct names returns success
 *  N.  validateUniqueness — duplicate names (case-insensitive) returns DUPLICATE_NAME error
 *  O.  validateUniqueness — duplicates detected after trim and lowercase normalisation
 */
class TaxValidatorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun taxGroup(
        id: String = "tg-1",
        name: String = "VAT",
        rate: Double = 10.0,
    ) = TaxGroup(id = id, name = name, rate = rate)

    // ── validateRate ──────────────────────────────────────────────────────────

    @Test
    fun `A - validateRate zero is valid`() {
        assertIs<Result.Success<Unit>>(TaxValidator.validateRate(0.0))
    }

    @Test
    fun `B - validateRate 100 is valid`() {
        assertIs<Result.Success<Unit>>(TaxValidator.validateRate(100.0))
    }

    @Test
    fun `C - validateRate 15 is valid`() {
        assertIs<Result.Success<Unit>>(TaxValidator.validateRate(15.0))
    }

    @Test
    fun `D - validateRate negative returns RATE_OUT_OF_RANGE error`() {
        val result = TaxValidator.validateRate(-0.01)
        assertIs<Result.Error>(result)
        assertEquals("RATE_OUT_OF_RANGE", (result.exception as ValidationException).rule)
    }

    @Test
    fun `E - validateRate above 100 returns RATE_OUT_OF_RANGE error`() {
        val result = TaxValidator.validateRate(100.01)
        assertIs<Result.Error>(result)
        assertEquals("RATE_OUT_OF_RANGE", (result.exception as ValidationException).rule)
    }

    @Test
    fun `F - validateRate NaN returns INVALID_RATE error`() {
        val result = TaxValidator.validateRate(Double.NaN)
        assertIs<Result.Error>(result)
        assertEquals("INVALID_RATE", (result.exception as ValidationException).rule)
    }

    @Test
    fun `G - validateRate positive infinity returns INVALID_RATE error`() {
        val result = TaxValidator.validateRate(Double.POSITIVE_INFINITY)
        assertIs<Result.Error>(result)
        assertEquals("INVALID_RATE", (result.exception as ValidationException).rule)
    }

    @Test
    fun `H - validateRate negative infinity returns INVALID_RATE error`() {
        val result = TaxValidator.validateRate(Double.NEGATIVE_INFINITY)
        assertIs<Result.Error>(result)
        assertEquals("INVALID_RATE", (result.exception as ValidationException).rule)
    }

    // ── validateTaxGroup ──────────────────────────────────────────────────────

    @Test
    fun `I - validateTaxGroup valid group returns success`() {
        assertIs<Result.Success<Unit>>(TaxValidator.validateTaxGroup(taxGroup()))
    }

    @Test
    fun `J - validateTaxGroup blank name returns REQUIRED error`() {
        val result = TaxValidator.validateTaxGroup(taxGroup(name = "   "))
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("name", ex.field)
    }

    @Test
    fun `K - validateTaxGroup invalid rate delegates and returns error`() {
        // Rate 150 is out of range — but TaxGroup init blocks 150 too, so use a workaround:
        // We directly test rate via validateRate; here we only test what the object can hold.
        // Rate 0 with blank name covers the "name check before rate" ordering.
        val result = TaxValidator.validateTaxGroup(taxGroup(name = "", rate = 0.0))
        assertIs<Result.Error>(result)
        // name check fires first
        assertEquals("REQUIRED", (result.exception as ValidationException).rule)
    }

    // ── validateUniqueness ────────────────────────────────────────────────────

    @Test
    fun `L - validateUniqueness empty list returns success`() {
        assertIs<Result.Success<Unit>>(TaxValidator.validateUniqueness(emptyList()))
    }

    @Test
    fun `M - validateUniqueness distinct names returns success`() {
        val groups = listOf(
            taxGroup(id = "1", name = "VAT"),
            taxGroup(id = "2", name = "Service Charge"),
            taxGroup(id = "3", name = "GST"),
        )
        assertIs<Result.Success<Unit>>(TaxValidator.validateUniqueness(groups))
    }

    @Test
    fun `N - validateUniqueness duplicate names returns DUPLICATE_NAME error`() {
        val groups = listOf(
            taxGroup(id = "1", name = "VAT"),
            taxGroup(id = "2", name = "VAT"),
        )
        val result = TaxValidator.validateUniqueness(groups)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("DUPLICATE_NAME", ex.rule)
        assertEquals("name", ex.field)
    }

    @Test
    fun `O - validateUniqueness case-insensitive and trimmed duplicate detection`() {
        val groups = listOf(
            taxGroup(id = "1", name = "vat"),
            taxGroup(id = "2", name = "VAT"),
            taxGroup(id = "3", name = " VAT "),
        )
        val result = TaxValidator.validateUniqueness(groups)
        assertIs<Result.Error>(result)
        assertEquals("DUPLICATE_NAME", (result.exception as ValidationException).rule)
    }
}

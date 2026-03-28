package com.zyntasolutions.zyntapos.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — ProductValidator Unit Tests (commonTest)
 *
 * Validates form-field validation rules for product create/edit operations.
 * All validators are pure functions — no fakes or coroutines required.
 *
 * Coverage (validate):
 *  A. valid params with all required fields returns empty errors map
 *  B. blank name returns "name" error
 *  C. blank categoryId returns "categoryId" error
 *  D. blank unitId returns "unitId" error
 *  E. blank price returns "price" error
 *  F. non-numeric price returns "price" error
 *  G. negative price returns "price" error
 *  H. zero price is valid
 *  I. optional costPrice blank is valid
 *  J. non-numeric costPrice returns "costPrice" error
 *  K. negative costPrice returns "costPrice" error
 *  L. zero costPrice is valid
 *  M. blank stockQty is valid (optional)
 *  N. non-numeric stockQty returns "stockQty" error
 *  O. negative stockQty returns "stockQty" error
 *  P. zero stockQty is valid
 *  Q. blank minStockQty is valid (optional)
 *  R. negative minStockQty returns "minStockQty" error
 *  S. barcode shorter than 4 chars returns "barcode" error
 *  T. barcode exactly 4 chars is valid
 *  U. blank barcode is valid (optional)
 *  V. multiple errors returned in a single call
 *
 * Coverage (validateField):
 *  W. name field: blank returns error, non-blank returns null
 *  X. price field: blank returns error, negative returns error, valid returns null
 *  Y. costPrice field: blank returns null, negative returns error, valid returns null
 *  Z. categoryId and unitId: blank returns error, non-blank returns null
 *  Z2. stockQty and minStockQty fields: negative returns error, valid returns null
 *  Z3. unknown field returns null (no validation)
 */
class ProductValidatorTest {

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun validParams(
        name: String = "Coca-Cola 1L",
        categoryId: String = "cat-01",
        unitId: String = "unit-01",
        price: String = "150.00",
        costPrice: String = "",
        stockQty: String = "",
        minStockQty: String = "",
        barcode: String = "",
    ) = ProductValidationParams(
        name = name,
        categoryId = categoryId,
        unitId = unitId,
        price = price,
        costPrice = costPrice,
        stockQty = stockQty,
        minStockQty = minStockQty,
        barcode = barcode,
    )

    // ── validate() ────────────────────────────────────────────────────────────

    @Test
    fun `A - valid params returns empty errors map`() {
        val errors = ProductValidator.validate(validParams())
        assertTrue(errors.isEmpty(), "Expected no validation errors, got: $errors")
    }

    @Test
    fun `B - blank name returns name error`() {
        val errors = ProductValidator.validate(validParams(name = ""))
        assertTrue(errors.containsKey("name"), "Expected 'name' error")
    }

    @Test
    fun `B2 - whitespace-only name returns name error`() {
        val errors = ProductValidator.validate(validParams(name = "   "))
        assertTrue(errors.containsKey("name"), "Expected 'name' error for whitespace")
    }

    @Test
    fun `C - blank categoryId returns categoryId error`() {
        val errors = ProductValidator.validate(validParams(categoryId = ""))
        assertTrue(errors.containsKey("categoryId"), "Expected 'categoryId' error")
    }

    @Test
    fun `D - blank unitId returns unitId error`() {
        val errors = ProductValidator.validate(validParams(unitId = ""))
        assertTrue(errors.containsKey("unitId"), "Expected 'unitId' error")
    }

    @Test
    fun `E - blank price returns price error`() {
        val errors = ProductValidator.validate(validParams(price = ""))
        assertTrue(errors.containsKey("price"), "Expected 'price' error for blank")
    }

    @Test
    fun `F - non-numeric price returns price error`() {
        val errors = ProductValidator.validate(validParams(price = "abc"))
        assertTrue(errors.containsKey("price"), "Expected 'price' error for non-numeric")
    }

    @Test
    fun `G - negative price returns price error`() {
        val errors = ProductValidator.validate(validParams(price = "-10.00"))
        assertTrue(errors.containsKey("price"), "Expected 'price' error for negative value")
    }

    @Test
    fun `H - zero price is valid`() {
        val errors = ProductValidator.validate(validParams(price = "0.00"))
        assertTrue(!errors.containsKey("price"), "Zero price must be valid")
    }

    @Test
    fun `I - blank costPrice is valid`() {
        val errors = ProductValidator.validate(validParams(costPrice = ""))
        assertTrue(!errors.containsKey("costPrice"), "Blank costPrice must be valid")
    }

    @Test
    fun `J - non-numeric costPrice returns costPrice error`() {
        val errors = ProductValidator.validate(validParams(costPrice = "xyz"))
        assertTrue(errors.containsKey("costPrice"), "Expected 'costPrice' error for non-numeric")
    }

    @Test
    fun `K - negative costPrice returns costPrice error`() {
        val errors = ProductValidator.validate(validParams(costPrice = "-5"))
        assertTrue(errors.containsKey("costPrice"), "Expected 'costPrice' error for negative")
    }

    @Test
    fun `L - zero costPrice is valid`() {
        val errors = ProductValidator.validate(validParams(costPrice = "0"))
        assertTrue(!errors.containsKey("costPrice"), "Zero costPrice must be valid")
    }

    @Test
    fun `M - blank stockQty is valid`() {
        val errors = ProductValidator.validate(validParams(stockQty = ""))
        assertTrue(!errors.containsKey("stockQty"), "Blank stockQty must be valid")
    }

    @Test
    fun `N - non-numeric stockQty returns stockQty error`() {
        val errors = ProductValidator.validate(validParams(stockQty = "ten"))
        assertTrue(errors.containsKey("stockQty"), "Expected 'stockQty' error for non-numeric")
    }

    @Test
    fun `O - negative stockQty returns stockQty error`() {
        val errors = ProductValidator.validate(validParams(stockQty = "-1"))
        assertTrue(errors.containsKey("stockQty"), "Expected 'stockQty' error for negative")
    }

    @Test
    fun `P - zero stockQty is valid`() {
        val errors = ProductValidator.validate(validParams(stockQty = "0"))
        assertTrue(!errors.containsKey("stockQty"), "Zero stockQty must be valid")
    }

    @Test
    fun `Q - blank minStockQty is valid`() {
        val errors = ProductValidator.validate(validParams(minStockQty = ""))
        assertTrue(!errors.containsKey("minStockQty"), "Blank minStockQty must be valid")
    }

    @Test
    fun `R - negative minStockQty returns minStockQty error`() {
        val errors = ProductValidator.validate(validParams(minStockQty = "-1"))
        assertTrue(errors.containsKey("minStockQty"), "Expected 'minStockQty' error for negative")
    }

    @Test
    fun `S - barcode shorter than 4 chars returns barcode error`() {
        val errors = ProductValidator.validate(validParams(barcode = "123"))
        assertTrue(errors.containsKey("barcode"), "Expected 'barcode' error for short barcode")
    }

    @Test
    fun `T - barcode exactly 4 chars is valid`() {
        val errors = ProductValidator.validate(validParams(barcode = "1234"))
        assertTrue(!errors.containsKey("barcode"), "4-char barcode must be valid")
    }

    @Test
    fun `U - blank barcode is valid`() {
        val errors = ProductValidator.validate(validParams(barcode = ""))
        assertTrue(!errors.containsKey("barcode"), "Blank barcode must be valid")
    }

    @Test
    fun `V - multiple errors returned in a single validation call`() {
        val errors = ProductValidator.validate(
            ProductValidationParams(
                name = "",
                categoryId = "",
                unitId = "",
                price = "",
            )
        )
        assertTrue(errors.size >= 4, "Expected at least 4 errors, got ${errors.size}: $errors")
        assertTrue(errors.containsKey("name"))
        assertTrue(errors.containsKey("categoryId"))
        assertTrue(errors.containsKey("unitId"))
        assertTrue(errors.containsKey("price"))
    }

    // ── validateField() ───────────────────────────────────────────────────────

    @Test
    fun `W - validateField name blank returns error string`() {
        val result = ProductValidator.validateField("name", "")
        assertTrue(result != null, "Expected non-null error for blank name")
    }

    @Test
    fun `W2 - validateField name non-blank returns null`() {
        val result = ProductValidator.validateField("name", "Bread")
        assertNull(result, "Expected null for valid name")
    }

    @Test
    fun `X - validateField price blank returns error`() {
        val result = ProductValidator.validateField("price", "")
        assertTrue(result != null, "Expected error for blank price")
    }

    @Test
    fun `X2 - validateField price negative returns error`() {
        val result = ProductValidator.validateField("price", "-5.0")
        assertTrue(result != null, "Expected error for negative price")
    }

    @Test
    fun `X3 - validateField price valid returns null`() {
        val result = ProductValidator.validateField("price", "100.00")
        assertNull(result, "Expected null for valid price")
    }

    @Test
    fun `Y - validateField costPrice blank returns null`() {
        val result = ProductValidator.validateField("costPrice", "")
        assertNull(result, "Expected null for blank optional costPrice")
    }

    @Test
    fun `Y2 - validateField costPrice negative returns error`() {
        val result = ProductValidator.validateField("costPrice", "-1")
        assertTrue(result != null, "Expected error for negative costPrice")
    }

    @Test
    fun `Y3 - validateField costPrice valid returns null`() {
        val result = ProductValidator.validateField("costPrice", "50.00")
        assertNull(result, "Expected null for valid costPrice")
    }

    @Test
    fun `Z - validateField categoryId blank returns error`() {
        val result = ProductValidator.validateField("categoryId", "")
        assertTrue(result != null, "Expected error for blank categoryId")
    }

    @Test
    fun `Z2 - validateField unitId blank returns error`() {
        val result = ProductValidator.validateField("unitId", "")
        assertTrue(result != null, "Expected error for blank unitId")
    }

    @Test
    fun `Z3 - validateField stockQty negative returns error`() {
        val result = ProductValidator.validateField("stockQty", "-5")
        assertTrue(result != null, "Expected error for negative stockQty")
    }

    @Test
    fun `Z4 - validateField minStockQty negative returns error`() {
        val result = ProductValidator.validateField("minStockQty", "-1")
        assertTrue(result != null, "Expected error for negative minStockQty")
    }

    @Test
    fun `Z5 - validateField unknown field returns null`() {
        val result = ProductValidator.validateField("unknownField", "any value")
        assertNull(result, "Unknown field must return null")
    }
}

package com.zyntasolutions.zyntapos.domain.validation

import com.zyntasolutions.zyntapos.core.result.Result

/**
 * Domain-layer validator for product create/edit operations (Sprint 18, task 10.1.3).
 *
 * Validates the raw string values supplied from a product form **before** they are
 * converted into a [com.zyntasolutions.zyntapos.domain.model.Product] domain object and
 * handed to [com.zyntasolutions.zyntapos.domain.usecase.inventory.CreateProductUseCase]
 * or [com.zyntasolutions.zyntapos.domain.usecase.inventory.UpdateProductUseCase].
 *
 * ### Validation Rules
 * 1. **Name:** Required, must not be blank.
 * 2. **Price:** Required, must parse to a valid non-negative number.
 * 3. **Cost Price:** If provided, must parse to a valid non-negative number.
 * 4. **Category:** Required — a category ID must be selected.
 * 5. **Unit:** Required — a unit-of-measure ID must be selected.
 * 6. **Stock Qty:** If provided, must parse to ≥ 0.  Delegates to [StockValidator.validateInitialStock].
 * 7. **Min Stock Qty:** If provided, must parse to ≥ 0.  Delegates to [StockValidator.validateMinStock].
 * 8. **Barcode:** If provided, must be at least 4 characters (Phase 1 soft hint).
 *
 * Server-side uniqueness checks (barcode, SKU) are handled by the use cases via
 * [com.zyntasolutions.zyntapos.domain.repository.ProductRepository.getByBarcode].
 *
 * ### Usage
 * ```kotlin
 * val params = ProductValidationParams(
 *     name = form.name, price = form.price, categoryId = form.categoryId,
 *     unitId = form.unitId, stockQty = form.stockQty, minStockQty = form.minStockQty,
 * )
 * val errors = ProductValidator.validate(params)
 * if (errors.isNotEmpty()) {
 *     updateState { copy(editFormState = editFormState.copy(validationErrors = errors)) }
 * }
 * ```
 */
object ProductValidator {

    /**
     * Validates all fields in [params] and returns a map of field name → error message.
     *
     * An empty map indicates all validations passed.
     *
     * Stock-quantity rules (non-negativity) are intentionally delegated to [StockValidator]
     * to avoid duplicating the single source of truth for stock business rules.
     *
     * @param params The [ProductValidationParams] to validate.
     * @return Map of field names to their validation error messages.
     */
    fun validate(params: ProductValidationParams): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // ── Name (required) ───────────────────────────────────────────
        if (params.name.isBlank()) {
            errors["name"] = "Product name is required."
        }

        // ── Category (required) ───────────────────────────────────────
        if (params.categoryId.isBlank()) {
            errors["categoryId"] = "Please select a category."
        }

        // ── Unit (required) ───────────────────────────────────────────
        if (params.unitId.isBlank()) {
            errors["unitId"] = "Please select a unit of measure."
        }

        // ── Price (required, non-negative number) ─────────────────────
        val price = params.price.toDoubleOrNull()
        if (params.price.isBlank()) {
            errors["price"] = "Selling price is required."
        } else if (price == null || price < 0.0) {
            errors["price"] = "Selling price must be a valid non-negative number."
        }

        // ── Cost Price (optional, but if provided must be valid) ──────
        if (params.costPrice.isNotBlank()) {
            val cost = params.costPrice.toDoubleOrNull()
            if (cost == null || cost < 0.0) {
                errors["costPrice"] = "Cost price must be a valid non-negative number."
            }
        }

        // ── Stock Qty — delegate to StockValidator ────────────────────
        // Avoids duplicating the non-negativity rule already owned by StockValidator.
        if (params.stockQty.isNotBlank()) {
            val qty = params.stockQty.toDoubleOrNull()
            if (qty == null) {
                errors["stockQty"] = "Stock quantity must be a valid number."
            } else {
                val result = StockValidator.validateInitialStock(qty)
                if (result is Result.Error) {
                    errors["stockQty"] = result.exception.message
                        ?: "Stock quantity must be a valid non-negative number."
                }
            }
        }

        // ── Min Stock Qty — delegate to StockValidator ────────────────
        if (params.minStockQty.isNotBlank()) {
            val minQty = params.minStockQty.toDoubleOrNull()
            if (minQty == null) {
                errors["minStockQty"] = "Minimum stock threshold must be a valid number."
            } else {
                val result = StockValidator.validateMinStock(minQty)
                if (result is Result.Error) {
                    errors["minStockQty"] = result.exception.message
                        ?: "Minimum stock threshold must be a valid non-negative number."
                }
            }
        }

        // ── Barcode format hint (soft check — Phase 2 adds strict EAN validation) ──
        if (params.barcode.isNotBlank() && params.barcode.length < 4) {
            errors["barcode"] = "Barcode seems too short. Verify the value."
        }

        return errors
    }

    /**
     * Validates a single field and returns an error message, or `null` if valid.
     *
     * Useful for real-time field validation as the user types.
     *
     * Stock-quantity fields also delegate to [StockValidator] to stay DRY.
     *
     * @param field The field name (must match [ProductValidationParams] property names).
     * @param value The current field value.
     * @return Error message or `null`.
     */
    fun validateField(field: String, value: String): String? = when (field) {
        "name" -> if (value.isBlank()) "Product name is required." else null

        "price" -> {
            val d = value.toDoubleOrNull()
            when {
                value.isBlank() -> "Selling price is required."
                d == null || d < 0.0 -> "Must be a valid non-negative number."
                else -> null
            }
        }

        "costPrice" -> if (value.isNotBlank()) {
            val d = value.toDoubleOrNull()
            if (d == null || d < 0.0) "Must be a valid non-negative number." else null
        } else null

        "categoryId" -> if (value.isBlank()) "Please select a category." else null

        "unitId" -> if (value.isBlank()) "Please select a unit." else null

        "stockQty" -> if (value.isNotBlank()) {
            val d = value.toDoubleOrNull()
            when {
                d == null -> "Must be a valid number."
                else -> (StockValidator.validateInitialStock(d) as? Result.Error)
                    ?.exception?.message
            }
        } else null

        "minStockQty" -> if (value.isNotBlank()) {
            val d = value.toDoubleOrNull()
            when {
                d == null -> "Must be a valid number."
                else -> (StockValidator.validateMinStock(d) as? Result.Error)
                    ?.exception?.message
            }
        } else null

        else -> null
    }
}

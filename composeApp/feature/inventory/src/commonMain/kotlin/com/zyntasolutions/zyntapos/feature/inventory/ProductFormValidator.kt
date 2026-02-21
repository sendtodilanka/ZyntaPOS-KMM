package com.zyntasolutions.zyntapos.feature.inventory

/**
 * Client-side validation for the product create/edit form (Sprint 18, task 10.1.3).
 *
 * ### Validation Rules
 * 1. **Name:** Required, must not be blank.
 * 2. **Price:** Required, must be a valid non-negative number.
 * 3. **Cost Price:** If provided, must be a valid non-negative number.
 * 4. **Category:** Required — a category must be selected.
 * 5. **Unit:** Required — a unit of measure must be selected.
 * 6. **Stock Qty:** If provided, must be a valid non-negative number.
 * 7. **Min Stock Qty:** If provided, must be a valid non-negative number.
 *
 * Server-side uniqueness checks (barcode, SKU) are handled by [CreateProductUseCase]
 * and [UpdateProductUseCase] via [ProductRepository.getByBarcode] — the form validator
 * only checks local formatting constraints to give instant feedback.
 *
 * ### Usage
 * ```kotlin
 * val errors = ProductFormValidator.validate(formState)
 * if (errors.isNotEmpty()) {
 *     updateState { copy(editFormState = formState.copy(validationErrors = errors)) }
 * }
 * ```
 */
object ProductFormValidator {

    /**
     * Validates all fields in [form] and returns a map of field name → error message.
     *
     * An empty map indicates all validations passed.
     *
     * @param form The [ProductFormState] to validate.
     * @return Map of field names to their validation error messages.
     */
    fun validate(form: ProductFormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // ── Name (required) ───────────────────────────────────────────
        if (form.name.isBlank()) {
            errors["name"] = "Product name is required."
        }

        // ── Category (required) ───────────────────────────────────────
        if (form.categoryId.isBlank()) {
            errors["categoryId"] = "Please select a category."
        }

        // ── Unit (required) ───────────────────────────────────────────
        if (form.unitId.isBlank()) {
            errors["unitId"] = "Please select a unit of measure."
        }

        // ── Price (required, non-negative number) ─────────────────────
        val price = form.price.toDoubleOrNull()
        if (form.price.isBlank()) {
            errors["price"] = "Selling price is required."
        } else if (price == null || price < 0.0) {
            errors["price"] = "Selling price must be a valid non-negative number."
        }

        // ── Cost Price (optional, but if provided must be valid) ──────
        if (form.costPrice.isNotBlank()) {
            val cost = form.costPrice.toDoubleOrNull()
            if (cost == null || cost < 0.0) {
                errors["costPrice"] = "Cost price must be a valid non-negative number."
            }
        }

        // ── Stock Qty (non-negative if provided) ──────────────────────
        if (form.stockQty.isNotBlank()) {
            val stockQty = form.stockQty.toDoubleOrNull()
            if (stockQty == null || stockQty < 0.0) {
                errors["stockQty"] = "Stock quantity must be a valid non-negative number."
            }
        }

        // ── Min Stock Qty (non-negative if provided) ──────────────────
        if (form.minStockQty.isNotBlank()) {
            val minStock = form.minStockQty.toDoubleOrNull()
            if (minStock == null || minStock < 0.0) {
                errors["minStockQty"] = "Minimum stock threshold must be a valid non-negative number."
            }
        }

        // ── Barcode format hint (not a hard rule — Phase 2 strict check)
        if (form.barcode.isNotBlank() && form.barcode.length < 4) {
            errors["barcode"] = "Barcode seems too short. Verify the value."
        }

        return errors
    }

    /**
     * Validates a single field and returns an error message, or null if valid.
     *
     * Useful for real-time field validation as the user types.
     *
     * @param field The field name (must match [ProductFormState] property names).
     * @param value The current field value.
     * @return Error message or null.
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
        "costPrice" -> {
            if (value.isNotBlank()) {
                val d = value.toDoubleOrNull()
                if (d == null || d < 0.0) "Must be a valid non-negative number." else null
            } else null
        }
        "categoryId" -> if (value.isBlank()) "Please select a category." else null
        "unitId" -> if (value.isBlank()) "Please select a unit." else null
        "stockQty" -> {
            if (value.isNotBlank()) {
                val d = value.toDoubleOrNull()
                if (d == null || d < 0.0) "Must be a valid non-negative number." else null
            } else null
        }
        "minStockQty" -> {
            if (value.isNotBlank()) {
                val d = value.toDoubleOrNull()
                if (d == null || d < 0.0) "Must be a valid non-negative number." else null
            } else null
        }
        else -> null
    }
}

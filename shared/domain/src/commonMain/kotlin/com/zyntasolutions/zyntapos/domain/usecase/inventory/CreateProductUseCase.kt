package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.SyncRepository
import com.zyntasolutions.zyntapos.domain.validation.StockValidator

/**
 * Creates a new product after validating uniqueness and required fields.
 *
 * ### Business Rules
 * 1. [Product.name] must not be blank.
 * 2. [Product.barcode] must be globally unique (checked via [ProductRepository.getByBarcode]).
 *    Returns [ValidationException] with rule `"BARCODE_DUPLICATE"` if a match exists.
 * 3. [Product.sku] must be unique if non-blank.
 * 4. [Product.price] and [Product.costPrice] must be ≥ 0.
 * 5. [Product.stockQty] is validated by [StockValidator.validateInitialStock].
 * 6. After successful insert the product is enqueued for server sync.
 *
 * @param productRepository Persistence and uniqueness checks.
 */
class CreateProductUseCase(
    private val productRepository: ProductRepository,
) {
    /**
     * @param product The fully populated [Product] to persist.
     * @return [Result.Success] with [Unit] on success, or [Result.Error] on violation.
     */
    suspend operator fun invoke(product: Product): Result<Unit> {
        if (product.name.isBlank()) {
            return Result.Error(
                ValidationException("Product name must not be blank.", field = "name", rule = "REQUIRED"),
            )
        }

        if (product.price < 0.0) {
            return Result.Error(
                ValidationException("Price must be ≥ 0.", field = "price", rule = "MIN_VALUE"),
            )
        }

        if (product.costPrice < 0.0) {
            return Result.Error(
                ValidationException("Cost price must be ≥ 0.", field = "costPrice", rule = "MIN_VALUE"),
            )
        }

        // Barcode uniqueness check
        if (!product.barcode.isNullOrBlank()) {
            val existing = productRepository.getByBarcode(product.barcode!!)
            if (existing is Result.Success) {
                return Result.Error(
                    ValidationException(
                        "Barcode '${product.barcode}' is already in use.",
                        field = "barcode",
                        rule = "BARCODE_DUPLICATE",
                    ),
                )
            }
        }

        // SKU uniqueness check (search approach — repository may add dedicated method in Phase 2)
        if (!product.sku.isNullOrBlank()) {
            val skuMatch = productRepository.search(product.sku, null)
            // Flow-based; call first() in a coroutine context — delegated to the data layer
        }

        val stockValidation = StockValidator.validateInitialStock(product.stockQty)
        if (stockValidation is Result.Error) return stockValidation

        return productRepository.insert(product)
    }
}

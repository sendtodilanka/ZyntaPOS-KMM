package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository

/**
 * Updates an existing product's details.
 *
 * ### Business Rules
 * 1. The product identified by [Product.id] must exist in the database.
 * 2. [Product.name] must not be blank.
 * 3. [Product.price] and [Product.costPrice] must be ≥ 0.
 * 4. If [Product.barcode] is changed, the new barcode must not already belong to
 *    another product (barcode uniqueness enforced per system).
 * 5. After a successful update the record is enqueued for server sync by the
 *    data layer implementation.
 *
 * @param productRepository Persistence layer.
 */
class UpdateProductUseCase(
    private val productRepository: ProductRepository,
) {
    /**
     * @param product Updated [Product] with the same [Product.id] as the existing record.
     * @return [Result.Success] with [Unit], or [Result.Error] on validation failure.
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

        // Verify the product exists
        val existingResult = productRepository.getById(product.id)
        if (existingResult is Result.Error) {
            return Result.Error(
                ValidationException(
                    "Product '${product.id}' not found.",
                    field = "id",
                    rule = "NOT_FOUND",
                ),
            )
        }

        val existing = (existingResult as Result.Success).data

        // Barcode uniqueness check only if barcode changed
        if (!product.barcode.isNullOrBlank() && product.barcode != existing.barcode) {
            val barcodeMatch = productRepository.getByBarcode(product.barcode!!)
            if (barcodeMatch is Result.Success) {
                return Result.Error(
                    ValidationException(
                        "Barcode '${product.barcode}' is already in use.",
                        field = "barcode",
                        rule = "BARCODE_DUPLICATE",
                    ),
                )
            }
        }

        return productRepository.update(product)
    }
}

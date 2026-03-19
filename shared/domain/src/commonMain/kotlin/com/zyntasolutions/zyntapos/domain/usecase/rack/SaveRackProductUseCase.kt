package com.zyntasolutions.zyntapos.domain.usecase.rack

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.RackProduct
import com.zyntasolutions.zyntapos.domain.repository.RackProductRepository

/**
 * Inserts or updates a [RackProduct] bin location mapping.
 *
 * Validates that rack and product IDs are non-blank and quantity is ≥ 0.
 */
class SaveRackProductUseCase(
    private val repo: RackProductRepository,
) {
    suspend operator fun invoke(
        rackId: String,
        productId: String,
        quantity: Double,
        binLocation: String?,
        existingId: String? = null,
    ): Result<Unit> {
        if (rackId.isBlank()) return Result.Error(ValidationException("Rack ID required"))
        if (productId.isBlank()) return Result.Error(ValidationException("Product ID required"))
        if (quantity < 0.0) return Result.Error(ValidationException("Quantity cannot be negative"))

        val rackProduct = RackProduct(
            id = existingId ?: IdGenerator.newId(),
            rackId = rackId,
            productId = productId,
            quantity = quantity,
            binLocation = binLocation?.trim()?.takeIf { it.isNotBlank() },
        )
        return repo.upsert(rackProduct)
    }
}

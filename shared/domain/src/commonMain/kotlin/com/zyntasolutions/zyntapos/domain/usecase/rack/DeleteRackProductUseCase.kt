package com.zyntasolutions.zyntapos.domain.usecase.rack

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.RackProductRepository

/**
 * Removes a product from a rack bin location.
 */
class DeleteRackProductUseCase(
    private val repo: RackProductRepository,
) {
    suspend operator fun invoke(rackId: String, productId: String): Result<Unit> {
        if (rackId.isBlank()) return Result.Error(ValidationException("Rack ID required"))
        if (productId.isBlank()) return Result.Error(ValidationException("Product ID required"))
        return repo.delete(rackId, productId)
    }
}

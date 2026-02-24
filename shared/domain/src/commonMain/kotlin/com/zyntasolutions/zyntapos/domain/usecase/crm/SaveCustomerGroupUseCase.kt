package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.repository.CustomerGroupRepository

/**
 * Inserts or updates a [CustomerGroup] after validating its fields.
 *
 * A [CustomerGroup.id] that already exists in the repository triggers an update;
 * otherwise, an insert is performed.
 */
class SaveCustomerGroupUseCase(
    private val repo: CustomerGroupRepository,
) {
    suspend operator fun invoke(group: CustomerGroup, isNew: Boolean): Result<Unit> {
        if (group.name.isBlank()) {
            return Result.Error(ValidationException("Group name cannot be blank"))
        }
        if (group.discountValue < 0.0) {
            return Result.Error(ValidationException("Discount value cannot be negative"))
        }
        if (group.discountValue > 100.0) {
            return Result.Error(ValidationException("Discount value cannot exceed 100%"))
        }
        return if (isNew) repo.insert(group) else repo.update(group)
    }
}

package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository

/**
 * Creates or updates a [CustomRole].
 *
 * ### Validation rules
 * - [CustomRole.name] must not be blank.
 * - Delegates uniqueness checks to [RoleRepository].
 *
 * @param roleRepository Persistence layer for custom role definitions.
 */
class SaveCustomRoleUseCase(private val roleRepository: RoleRepository) {

    /**
     * Persists [role].
     *
     * @param role      The custom role to create or update.
     * @param isUpdate  When `true` calls [RoleRepository.updateCustomRole]; otherwise creates.
     */
    suspend operator fun invoke(role: CustomRole, isUpdate: Boolean): Result<Unit> {
        if (role.name.isBlank()) {
            return Result.Error(
                ValidationException("Role name is required.", field = "name", rule = "REQUIRED"),
            )
        }
        return if (isUpdate) {
            roleRepository.updateCustomRole(role)
        } else {
            roleRepository.createCustomRole(role)
        }
    }
}

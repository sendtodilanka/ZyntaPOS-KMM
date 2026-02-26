package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository

/**
 * Permanently deletes a custom role by its [id].
 *
 * Users previously assigned this role retain their built-in [com.zyntasolutions.zyntapos.domain.model.Role]
 * as a fallback. Callers should reassign affected users before invoking this use case.
 *
 * @param roleRepository Persistence layer for custom role definitions.
 */
class DeleteCustomRoleUseCase(private val roleRepository: RoleRepository) {

    /**
     * @param id UUID of the custom role to delete.
     * @return [Result.Success] on success; [Result.Error] on storage failure.
     */
    suspend operator fun invoke(id: String): Result<Unit> = roleRepository.deleteCustomRole(id)
}

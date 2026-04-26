package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import kotlinx.datetime.Clock

/**
 * Default implementation of [CloneRoleUseCase].
 *
 * Used by the RBAC role editor to seed a new custom role from an existing one
 * — the source's permission set is copied verbatim, but the new role gets a
 * fresh UUID, the caller-supplied [newName], an empty description (so the
 * admin can write context that's specific to the clone), and current
 * timestamps.
 *
 * ### Validation rules
 * - [newName] must not be blank.
 * - The source role must exist (delegated to [RoleRepository.getCustomRoleById]).
 *
 * ### Failure cases
 * - Source not found → propagates the repository's `Result.Error`.
 * - Persistence failure → propagates the repository's `Result.Error`.
 *
 * @param roleRepository Persistence layer for custom role definitions.
 */
class CloneRoleUseCaseImpl(private val roleRepository: RoleRepository) : CloneRoleUseCase {

    override suspend fun invoke(sourceRoleId: String, newName: String): Result<CustomRole> {
        if (newName.isBlank()) {
            return Result.Error(
                ValidationException("Role name is required.", field = "name", rule = "REQUIRED"),
            )
        }
        val source = when (val lookup = roleRepository.getCustomRoleById(sourceRoleId)) {
            is Result.Success -> lookup.data
            is Result.Error -> return Result.Error(lookup.exception)
        }
        val now = Clock.System.now()
        val cloned = CustomRole(
            id = IdGenerator.newId(),
            name = newName,
            description = "",
            permissions = source.permissions,
            createdAt = now,
            updatedAt = now,
        )
        return when (val persisted = roleRepository.createCustomRole(cloned)) {
            is Result.Success -> Result.Success(cloned)
            is Result.Error -> Result.Error(persisted.exception)
        }
    }
}

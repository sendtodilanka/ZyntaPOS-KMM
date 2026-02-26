package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import kotlinx.coroutines.flow.Flow

/**
 * Provides a reactive stream of all admin-defined [CustomRole]s, ordered by name.
 *
 * @param roleRepository Source of custom role definitions.
 */
class GetCustomRolesUseCase(private val roleRepository: RoleRepository) {

    /** Returns a [Flow] that re-emits whenever the custom role set changes. */
    operator fun invoke(): Flow<List<CustomRole>> = roleRepository.getAllCustomRoles()
}

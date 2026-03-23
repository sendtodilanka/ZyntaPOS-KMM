package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository

/**
 * Checks whether a user has access to a specific store (C3.2).
 *
 * Access is granted if any of these are true:
 * 1. The store is the user's primary store ([User.storeId]).
 * 2. An active [UserStoreAccess] grant exists for the user-store pair.
 *
 * Also resolves the effective role at the target store:
 * - If a per-store role override exists in the access grant, that role is used.
 * - Otherwise, the user's default role is used.
 */
class CheckStoreAccessUseCase(
    private val userRepository: UserRepository,
    private val accessRepository: UserStoreAccessRepository,
) {
    data class AccessResult(
        val hasAccess: Boolean,
        val effectiveRole: Role?,
    )

    suspend operator fun invoke(userId: String, storeId: String): AccessResult {
        // Check primary store
        val userResult = userRepository.getById(userId)
        if (userResult is Result.Success) {
            val user = userResult.data
            if (user.storeId == storeId && user.isActive) {
                return AccessResult(hasAccess = true, effectiveRole = user.role)
            }
        }

        // Check access grants
        val grantResult = accessRepository.getByUserAndStore(userId, storeId)
        if (grantResult is Result.Success) {
            val grant = grantResult.data
            if (grant != null && grant.isActive) {
                val effectiveRole = grant.roleAtStore
                    ?: (userResult as? Result.Success)?.data?.role
                return AccessResult(hasAccess = true, effectiveRole = effectiveRole)
            }
        }

        return AccessResult(hasAccess = false, effectiveRole = null)
    }
}

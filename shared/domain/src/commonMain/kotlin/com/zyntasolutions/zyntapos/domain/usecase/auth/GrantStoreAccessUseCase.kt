package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository
import kotlin.time.Clock

/**
 * Grants a user access to an additional store (C3.2).
 *
 * Validates that:
 * 1. The target user exists and is active.
 * 2. The user doesn't already have active access to the store.
 * 3. If a roleAtStore is specified, it is a valid POS role.
 */
class GrantStoreAccessUseCase(
    private val userRepository: UserRepository,
    private val accessRepository: UserStoreAccessRepository,
) {
    data class Params(
        val userId: String,
        val storeId: String,
        val roleAtStore: Role? = null,
        val grantedBy: String? = null,
    )

    suspend operator fun invoke(params: Params): Result<UserStoreAccess> {
        // Validate user exists and is active
        val userResult = userRepository.getById(params.userId)
        if (userResult is Result.Error) {
            return Result.Error(ValidationException("User not found: ${params.userId}"))
        }
        val user = (userResult as Result.Success).data
        if (!user.isActive) {
            return Result.Error(ValidationException("Cannot grant store access to inactive user"))
        }

        // Check if user already has active access to this store
        if (user.storeId == params.storeId) {
            return Result.Error(ValidationException("User already has primary access to this store"))
        }
        if (accessRepository.hasAccess(params.userId, params.storeId)) {
            return Result.Error(ValidationException("User already has active access to this store"))
        }

        val now = Clock.System.now()
        val access = UserStoreAccess(
            id = IdGenerator.newId(),
            userId = params.userId,
            storeId = params.storeId,
            roleAtStore = params.roleAtStore,
            isActive = true,
            grantedBy = params.grantedBy,
            createdAt = now,
            updatedAt = now,
        )

        return when (val result = accessRepository.grantAccess(access)) {
            is Result.Success -> Result.Success(access)
            is Result.Error -> result
            is Result.Loading -> Result.Loading
        }
    }
}

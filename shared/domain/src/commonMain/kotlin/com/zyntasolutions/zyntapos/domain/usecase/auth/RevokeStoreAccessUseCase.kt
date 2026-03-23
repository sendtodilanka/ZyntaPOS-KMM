package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository

/**
 * Revokes a user's access to an additional store (C3.2).
 *
 * Sets the access grant to inactive. The user retains access to their primary
 * store ([com.zyntasolutions.zyntapos.domain.model.User.storeId]) — that
 * cannot be revoked via this use case.
 */
class RevokeStoreAccessUseCase(
    private val accessRepository: UserStoreAccessRepository,
) {
    suspend operator fun invoke(userId: String, storeId: String): Result<Unit> {
        if (!accessRepository.hasAccess(userId, storeId)) {
            return Result.Error(ValidationException("User does not have active access to this store"))
        }
        return accessRepository.revokeAccess(userId, storeId)
    }
}

package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.StocktakeSession
import com.zyntasolutions.zyntapos.domain.repository.StocktakeRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase

/**
 * Starts a new stocktake session.
 *
 * RBAC gated: the active user must hold [Permission.MANAGE_STOCKTAKE].
 * Only one IN_PROGRESS stocktake session is allowed at a time — enforced by
 * the data layer implementation.
 *
 * @param stocktakeRepository Persistence contract for stocktake sessions.
 * @param checkPermission     RBAC check use case.
 */
class StartStocktakeUseCase(
    private val stocktakeRepository: StocktakeRepository,
    private val checkPermission: CheckPermissionUseCase,
) {

    /**
     * Starts a new stocktake session on behalf of [userId].
     *
     * @param userId UUID of the user initiating the stocktake.
     * @return [Result.Success] with the created [StocktakeSession];
     *         [Result.Error] with [com.zyntasolutions.zyntapos.core.result.AuthException]
     *         on insufficient permissions, or [ValidationException] if a session is
     *         already in progress.
     */
    suspend fun execute(userId: String): Result<StocktakeSession> {
        if (!checkPermission(userId, Permission.MANAGE_STOCKTAKE)) {
            return Result.Error(
                com.zyntasolutions.zyntapos.core.result.AuthException(
                    "User $userId does not have MANAGE_STOCKTAKE permission"
                )
            )
        }
        return stocktakeRepository.startSession(userId)
    }
}

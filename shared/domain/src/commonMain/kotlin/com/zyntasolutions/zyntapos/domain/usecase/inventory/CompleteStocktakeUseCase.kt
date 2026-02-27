package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.StocktakeRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import kotlin.math.abs
import kotlin.time.Clock

/**
 * Completes an in-progress stocktake session and applies stock variances.
 *
 * RBAC gated: the active user must hold [Permission.MANAGE_STOCKTAKE].
 *
 * **Process:**
 * 1. Calls [StocktakeRepository.complete] to mark the session as COMPLETED and
 *    retrieve the variance map (`productId → variance`).
 * 2. For each non-zero variance, creates a [StockAdjustment] and persists it via
 *    [StockRepository.adjustStock].
 *
 * @param stocktakeRepository Persistence contract for stocktake sessions.
 * @param stockRepository     Persistence contract for stock adjustments.
 * @param checkPermission     RBAC check use case.
 */
class CompleteStocktakeUseCase(
    private val stocktakeRepository: StocktakeRepository,
    private val stockRepository: StockRepository,
    private val checkPermission: CheckPermissionUseCase,
) {

    /**
     * Finalises the stocktake session identified by [sessionId].
     *
     * @param sessionId UUID of the [com.zyntasolutions.zyntapos.domain.model.StocktakeSession].
     * @param userId    UUID of the user completing the stocktake.
     * @return [Result.Success] with the variance map (`productId → variance`);
     *         [Result.Error] on permission failure, session not found, or stock
     *         adjustment failure.
     */
    suspend fun execute(sessionId: String, userId: String): Result<Map<String, Int>> {
        if (!checkPermission(userId, Permission.MANAGE_STOCKTAKE)) {
            return Result.Error(AuthException("User $userId does not have MANAGE_STOCKTAKE permission"))
        }

        return when (val completeResult = stocktakeRepository.complete(sessionId)) {
            is Result.Error -> completeResult
            is Result.Success -> {
                val variances = completeResult.data
                val now = Clock.System.now()

                // Apply each non-zero variance as a stock adjustment
                for ((productId, variance) in variances) {
                    if (variance == 0) continue
                    val adjustment = StockAdjustment(
                        id = IdGenerator.newId(),
                        productId = productId,
                        type = if (variance > 0) StockAdjustment.Type.INCREASE else StockAdjustment.Type.DECREASE,
                        quantity = abs(variance).toDouble(),
                        reason = "Stocktake variance — session $sessionId",
                        adjustedBy = userId,
                        timestamp = now,
                        syncStatus = SyncStatus(state = SyncStatus.State.PENDING),
                    )
                    when (val adjResult = stockRepository.adjustStock(adjustment)) {
                        is Result.Error -> return adjResult
                        is Result.Success -> Unit
                        else -> Unit
                    }
                }
                Result.Success(variances)
            }
            else -> Result.Loading
        }
    }
}

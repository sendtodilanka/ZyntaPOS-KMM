package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.StocktakeCount
import com.zyntasolutions.zyntapos.domain.model.StocktakeSession

/**
 * Persistence contract for stocktake sessions and counts.
 *
 * Implementations live in `:shared:data`.
 */
interface StocktakeRepository {

    /**
     * Creates a new [StocktakeSession] with [IN_PROGRESS] status.
     *
     * @param userId ID of the staff member starting the count.
     * @return [Result.Success] with the newly created session.
     */
    suspend fun startSession(userId: String): Result<StocktakeSession>

    /**
     * Updates or inserts the counted quantity for [barcode] within [sessionId].
     *
     * If the product was already scanned, its count is replaced with [qty].
     */
    suspend fun updateCount(sessionId: String, barcode: String, qty: Int): Result<Unit>

    /**
     * Retrieves the full [StocktakeSession] including all counts.
     */
    suspend fun getSession(id: String): Result<StocktakeSession>

    /**
     * Returns all [StocktakeCount] entries for the given [sessionId].
     */
    suspend fun getCountsForSession(sessionId: String): Result<List<StocktakeCount>>

    /**
     * Marks the session as completed and returns the variance map
     * (`productId → variance`) for all counted items.
     *
     * @return [Result.Success] with a map of `productId → variance`
     *         (positive = overage, negative = shortage).
     */
    suspend fun complete(sessionId: String): Result<Map<String, Int>>

    /**
     * Marks the session as cancelled without applying any adjustments.
     */
    suspend fun cancel(sessionId: String): Result<Unit>
}

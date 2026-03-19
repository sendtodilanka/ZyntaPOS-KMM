package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.StoreProductOverride
import kotlinx.coroutines.flow.Flow

/**
 * Contract for per-store product override operations.
 *
 * Store overrides are writable on POS devices — price and stock changes
 * are synced back to the backend via SyncEnqueuer.
 */
interface StoreProductOverrideRepository {

    /** Emits all active overrides for the given [storeId]. */
    fun getByStore(storeId: String): Flow<List<StoreProductOverride>>

    /** Returns the override for a specific master product at a specific store. */
    suspend fun getOverride(masterProductId: String, storeId: String): Result<StoreProductOverride>

    /** Upserts a store product override from sync data. */
    suspend fun upsertFromSync(override: StoreProductOverride): Result<Unit>

    /** Updates the local price override and enqueues a sync operation. */
    suspend fun updateLocalPrice(masterProductId: String, storeId: String, price: Double?): Result<Unit>

    /** Updates the local stock quantity. */
    suspend fun updateLocalStock(masterProductId: String, storeId: String, qty: Double): Result<Unit>
}

package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.domain.model.Store
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for store / branch data.
 *
 * Provides reactive access to the local stores table (synced from backend).
 */
interface StoreRepository {

    /** Observe all active stores, ordered by name. */
    fun getAllStores(): Flow<List<Store>>

    /** Get a single store by ID. */
    suspend fun getById(storeId: String): Store?

    /** Get store name by ID (lightweight lookup). */
    suspend fun getStoreName(storeId: String): String?

    /** Insert or update a store from sync. */
    suspend fun upsertFromSync(store: Store)
}

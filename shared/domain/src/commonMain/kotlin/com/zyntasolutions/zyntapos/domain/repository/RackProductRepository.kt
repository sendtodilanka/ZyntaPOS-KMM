package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RackProduct
import kotlinx.coroutines.flow.Flow

/**
 * Contract for managing product-to-rack mappings (bin locations) within a warehouse.
 */
interface RackProductRepository {

    /**
     * Emits all [RackProduct] entries for [rackId], joined with product info.
     * Re-emits on any change to the rack's product list.
     */
    fun getByRack(rackId: String): Flow<List<RackProduct>>

    /**
     * Inserts or updates the (rack, product) entry.
     * Uses ON CONFLICT DO UPDATE — safe to call for both create and edit.
     */
    suspend fun upsert(rackProduct: RackProduct): Result<Unit>

    /**
     * Removes the product from the rack.
     */
    suspend fun delete(rackId: String, productId: String): Result<Unit>
}

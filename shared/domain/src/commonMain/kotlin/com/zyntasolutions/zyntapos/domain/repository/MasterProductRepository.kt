package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import kotlinx.coroutines.flow.Flow

/**
 * Contract for master product catalog operations.
 *
 * Master products are read-only on POS devices — they are pulled from the
 * backend via sync. The only write path is [upsertFromSync] for incoming data.
 *
 * All [Flow]-returning methods reflect live SQLDelight queries.
 */
interface MasterProductRepository {

    /** Emits the full active master product list, ordered by name. */
    fun getAll(): Flow<List<MasterProduct>>

    /** Returns a single [MasterProduct] by its UUID [id]. */
    suspend fun getById(id: String): Result<MasterProduct>

    /** Looks up a master product by its [barcode]. */
    suspend fun getByBarcode(barcode: String): Result<MasterProduct>

    /** Full-text search on name, SKU, and barcode. */
    fun search(query: String): Flow<List<MasterProduct>>

    /** Upserts a master product from sync data. */
    suspend fun upsertFromSync(masterProduct: MasterProduct): Result<Unit>

    /** Returns the total number of active master products. */
    suspend fun getCount(): Int
}

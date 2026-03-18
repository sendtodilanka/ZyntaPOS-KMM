package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
import kotlinx.coroutines.flow.Flow

/**
 * Contract for product variant persistence.
 *
 * Variants are child entities of [com.zyntasolutions.zyntapos.domain.model.Product],
 * each with its own barcode, price override, and stock level.
 */
interface ProductVariantRepository {

    /** Emits all variants for the given [productId], ordered by name. */
    fun getByProductId(productId: String): Flow<List<ProductVariant>>

    /** Returns a single variant by [id]. */
    suspend fun getById(id: String): Result<ProductVariant>

    /** Looks up a variant by its unique [barcode]. */
    suspend fun getByBarcode(barcode: String): Result<ProductVariant>

    /** Inserts a new variant. */
    suspend fun insert(variant: ProductVariant): Result<Unit>

    /** Updates an existing variant. */
    suspend fun update(variant: ProductVariant): Result<Unit>

    /** Deletes a single variant by [id]. */
    suspend fun delete(id: String): Result<Unit>

    /** Deletes all variants belonging to [productId]. */
    suspend fun deleteByProductId(productId: String): Result<Unit>

    /**
     * Replaces all variants for [productId] with the given [variants] list.
     *
     * This is the primary method used by create/update product flows:
     * it deletes any existing variants not in the new list and upserts the rest,
     * all within a single transaction.
     */
    suspend fun replaceAll(productId: String, variants: List<ProductVariant>): Result<Unit>
}

package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.models.ProductDto

/**
 * Repository interface for server-side product table access (S3-15).
 *
 * Extracts direct Exposed DB operations from ProductService into a testable boundary.
 */
interface ProductRepository {

    suspend fun list(storeId: String, page: Int, size: Int, updatedSince: Long?): ProductPageResult
}

data class ProductPageResult(
    val items: List<ProductDto>,
    val total: Long,
)

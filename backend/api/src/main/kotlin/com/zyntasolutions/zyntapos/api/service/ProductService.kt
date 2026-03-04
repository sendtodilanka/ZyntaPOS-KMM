package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.PagedResponse
import com.zyntasolutions.zyntapos.api.models.ProductDto
import org.slf4j.LoggerFactory

class ProductService {
    private val logger = LoggerFactory.getLogger(ProductService::class.java)

    suspend fun list(storeId: String, page: Int, size: Int, updatedSince: Long?): PagedResponse<ProductDto> {
        logger.info("Products list: storeId=$storeId page=$page size=$size updatedSince=$updatedSince")
        // TODO: Query products table in PostgreSQL, filtered by storeId and updatedSince
        return PagedResponse(
            data = emptyList(),
            page = page,
            size = size,
            total = 0L,
            hasMore = false
        )
    }
}

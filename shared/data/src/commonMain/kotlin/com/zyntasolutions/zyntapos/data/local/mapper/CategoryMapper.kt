package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Categories
import com.zyntasolutions.zyntapos.domain.model.Category

/**
 * Maps between the SQLDelight-generated [Categories] entity and the domain [Category] model.
 */
object CategoryMapper {

    /** Converts a SQLDelight [Categories] row to a domain [Category]. */
    fun toDomain(row: Categories): Category = Category(
        id           = row.id,
        name         = row.name,
        parentId     = row.parent_id,
        imageUrl     = row.image_url,
        displayOrder = row.display_order.toInt(),
        isActive     = row.is_active == 1L,
    )

    /** Converts a domain [Category] to SQL insert parameters. */
    fun toInsertParams(c: Category, syncStatus: String = "PENDING") = InsertParams(
        id           = c.id,
        name         = c.name,
        parentId     = c.parentId,
        imageUrl     = c.imageUrl,
        displayOrder = c.displayOrder.toLong(),
        isActive     = if (c.isActive) 1L else 0L,
        syncStatus   = syncStatus,
    )

    data class InsertParams(
        val id: String,
        val name: String,
        val parentId: String?,
        val imageUrl: String?,
        val displayOrder: Long,
        val isActive: Long,
        val syncStatus: String,
    )
}

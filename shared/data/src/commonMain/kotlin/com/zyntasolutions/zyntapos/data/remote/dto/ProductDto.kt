package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    @SerialName("id")            val id: String,
    @SerialName("name")          val name: String,
    @SerialName("barcode")       val barcode: String? = null,
    @SerialName("sku")           val sku: String? = null,
    @SerialName("category_id")   val categoryId: String? = null,
    @SerialName("unit_id")       val unitId: String? = null,
    @SerialName("price")         val price: Double,
    @SerialName("cost_price")    val costPrice: Double = 0.0,
    @SerialName("tax_group_id")  val taxGroupId: String? = null,
    @SerialName("stock_qty")     val stockQty: Double = 0.0,
    @SerialName("min_stock_qty") val minStockQty: Double = 0.0,
    @SerialName("image_url")     val imageUrl: String? = null,
    @SerialName("description")   val description: String? = null,
    @SerialName("is_active")     val isActive: Boolean = true,
    @SerialName("created_at")    val createdAt: Long,
    @SerialName("updated_at")    val updatedAt: Long,
    @SerialName("sync_status")   val syncStatus: String = "SYNCED",
    @SerialName("master_product_id") val masterProductId: String? = null,
)

@Serializable
data class CategoryDto(
    @SerialName("id")            val id: String,
    @SerialName("name")          val name: String,
    @SerialName("parent_id")     val parentId: String? = null,
    @SerialName("image_url")     val imageUrl: String? = null,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("is_active")     val isActive: Boolean = true,
    @SerialName("updated_at")    val updatedAt: Long,
)

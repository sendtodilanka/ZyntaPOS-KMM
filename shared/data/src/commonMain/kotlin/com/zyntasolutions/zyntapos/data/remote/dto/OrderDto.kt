package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    @SerialName("id")                val id: String,
    @SerialName("order_number")      val orderNumber: String,
    @SerialName("type")              val type: String,
    @SerialName("status")            val status: String,
    @SerialName("items")             val items: List<OrderItemDto> = emptyList(),
    @SerialName("subtotal")          val subtotal: Double,
    @SerialName("tax_amount")        val taxAmount: Double,
    @SerialName("discount_amount")   val discountAmount: Double,
    @SerialName("total")             val total: Double,
    @SerialName("payment_method")    val paymentMethod: String,
    @SerialName("amount_tendered")   val amountTendered: Double = 0.0,
    @SerialName("change_amount")     val changeAmount: Double = 0.0,
    @SerialName("customer_id")       val customerId: String? = null,
    @SerialName("cashier_id")        val cashierId: String,
    @SerialName("store_id")          val storeId: String,
    @SerialName("register_session_id") val registerSessionId: String,
    @SerialName("notes")             val notes: String? = null,
    @SerialName("reference")         val reference: String? = null,
    @SerialName("created_at")        val createdAt: Long,
    @SerialName("updated_at")        val updatedAt: Long,
    @SerialName("sync_status")       val syncStatus: String = "SYNCED",
)

@Serializable
data class OrderItemDto(
    @SerialName("id")            val id: String,
    @SerialName("order_id")      val orderId: String,
    @SerialName("product_id")    val productId: String,
    @SerialName("product_name")  val productName: String,
    @SerialName("unit_price")    val unitPrice: Double,
    @SerialName("quantity")      val quantity: Double,
    @SerialName("discount")      val discount: Double = 0.0,
    @SerialName("discount_type") val discountType: String = "FIXED",
    @SerialName("tax_rate")      val taxRate: Double = 0.0,
    @SerialName("tax_amount")    val taxAmount: Double = 0.0,
    @SerialName("line_total")    val lineTotal: Double,
)

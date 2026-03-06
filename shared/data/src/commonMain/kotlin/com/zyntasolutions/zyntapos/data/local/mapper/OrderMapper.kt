package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Orders
import com.zyntasolutions.zyntapos.db.Order_items
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

/**
 * Maps between SQLDelight [Orders] / [Order_items] entities and domain [Order] / [OrderItem] models.
 *
 * [PaymentSplit] list is serialised as JSON in the `payment_splits_json` column.
 */
object OrderMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /** Safe enum lookup that falls back to [default] on unknown values instead of crashing. */
    private inline fun <reified T : Enum<T>> safeValueOf(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default

    fun toDomain(row: Orders, items: List<Order_items>): Order = Order(
        id                = row.id,
        orderNumber       = row.order_number,
        type              = safeValueOf(row.type, OrderType.SALE),
        status            = safeValueOf(row.status, OrderStatus.IN_PROGRESS),
        items             = items.map { itemToDomain(it) },
        subtotal          = row.subtotal,
        taxAmount         = row.tax_amount,
        discountAmount    = row.discount_amount,
        total             = row.total,
        paymentMethod     = safeValueOf(row.payment_method, PaymentMethod.CASH),
        paymentSplits     = parsePaymentSplits(row.payment_splits_json),
        amountTendered    = row.amount_tendered ?: 0.0,
        changeAmount      = row.change_amount ?: 0.0,
        customerId        = row.customer_id,
        cashierId         = row.cashier_id,
        storeId           = row.store_id,
        registerSessionId = row.register_session_id ?: "",
        notes             = row.notes,
        reference         = row.reference,
        createdAt         = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt         = Instant.fromEpochMilliseconds(row.updated_at),
        syncStatus        = SyncStatus(
            state = safeValueOf(row.sync_status.uppercase(), SyncStatus.State.PENDING),
        ),
    )

    fun itemToDomain(row: Order_items): OrderItem = OrderItem(
        id           = row.id,
        orderId      = row.order_id,
        productId    = row.product_id,
        productName  = row.product_name,
        unitPrice    = row.unit_price,
        quantity     = row.quantity,
        discount     = row.discount,
        discountType = safeValueOf(row.discount_type, DiscountType.FIXED),
        taxRate      = row.tax_rate,
        taxAmount    = row.tax_amount,
        lineTotal    = row.line_total,
    )

    private fun parsePaymentSplits(raw: String?): List<PaymentSplit> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<PaymentSplit>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializePaymentSplits(splits: List<PaymentSplit>): String? {
        if (splits.isEmpty()) return null
        return json.encodeToString(splits)
    }
}

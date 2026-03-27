package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.sync.OrderItems
import com.zyntasolutions.zyntapos.api.sync.Orders
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun Route.orderRoutes() {
    route("/orders") {

        /**
         * GET /v1/orders
         *
         * Returns paginated orders for the current store (derived from JWT storeId).
         *
         * Query params:
         *   - status   (optional) — filter by order status (e.g. COMPLETED, PENDING, REFUNDED)
         *   - page     (optional, default 0)
         *   - size     (optional, default 50, max 200)
         *
         * Response: OrderListResponse { total, page, size, items }
         */
        get {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()

            val status = call.request.queryParameters["status"]?.trim()?.takeIf { it.isNotEmpty() }
            val page   = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size   = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

            val result = newSuspendedTransaction {
                var query = Orders.selectAll().where {
                    (Orders.storeId eq storeId) and (Orders.isActive eq true)
                }

                if (status != null) {
                    query = query.adjustWhere { Orders.status eq status }
                }

                val total = query.count().toInt()
                val items = query
                    .orderBy(Orders.createdAt, SortOrder.DESC)
                    .limit(size, offset = (page * size).toLong())
                    .map { row ->
                        OrderSummaryDto(
                            id            = row[Orders.id],
                            storeId       = row[Orders.storeId],
                            orderNumber   = row[Orders.orderNumber],
                            customerId    = row[Orders.customerId],
                            cashierId     = row[Orders.cashierId],
                            status        = row[Orders.status],
                            orderType     = row[Orders.orderType],
                            subtotal      = row[Orders.subtotal].toDouble(),
                            taxTotal      = row[Orders.taxTotal].toDouble(),
                            discountTotal = row[Orders.discountTotal].toDouble(),
                            grandTotal    = row[Orders.grandTotal].toDouble(),
                            notes         = row[Orders.notes],
                            createdAt     = row[Orders.createdAt]?.toString(),
                            updatedAt     = row[Orders.updatedAt].toString(),
                        )
                    }
                OrderListResponse(total = total, page = page, size = size, items = items)
            }

            call.respond(HttpStatusCode.OK, result)
        }

        /**
         * GET /v1/orders/{orderId}
         *
         * Returns a single order with its line items.
         * Returns 404 if the order does not belong to the current store.
         */
        get("/{orderId}") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val orderId = call.parameters["orderId"] ?: return@get call.respond(HttpStatusCode.BadRequest)

            val result = newSuspendedTransaction {
                val orderRow = Orders.selectAll().where {
                    (Orders.id eq orderId) and
                    (Orders.storeId eq storeId) and
                    (Orders.isActive eq true)
                }.singleOrNull() ?: return@newSuspendedTransaction null

                val lineItems = OrderItems.selectAll().where {
                    OrderItems.orderId eq orderId
                }.map { row ->
                    OrderItemDto(
                        id          = row[OrderItems.id],
                        orderId     = row[OrderItems.orderId],
                        productId   = row[OrderItems.productId],
                        productName = row[OrderItems.productName],
                        quantity    = row[OrderItems.quantity].toDouble(),
                        unitPrice   = row[OrderItems.unitPrice].toDouble(),
                        discount    = row[OrderItems.discount].toDouble(),
                        tax         = row[OrderItems.tax].toDouble(),
                        subtotal    = row[OrderItems.subtotal].toDouble(),
                        notes       = row[OrderItems.notes],
                    )
                }

                OrderDetailDto(
                    id            = orderRow[Orders.id],
                    storeId       = orderRow[Orders.storeId],
                    orderNumber   = orderRow[Orders.orderNumber],
                    customerId    = orderRow[Orders.customerId],
                    cashierId     = orderRow[Orders.cashierId],
                    status        = orderRow[Orders.status],
                    orderType     = orderRow[Orders.orderType],
                    subtotal      = orderRow[Orders.subtotal].toDouble(),
                    taxTotal      = orderRow[Orders.taxTotal].toDouble(),
                    discountTotal = orderRow[Orders.discountTotal].toDouble(),
                    grandTotal    = orderRow[Orders.grandTotal].toDouble(),
                    notes         = orderRow[Orders.notes],
                    createdAt     = orderRow[Orders.createdAt]?.toString(),
                    updatedAt     = orderRow[Orders.updatedAt].toString(),
                    items         = lineItems,
                )
            }

            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(HttpStatusCode.OK, result)
            }
        }
    }
}

// ── Response DTOs ──────────────────────────────────────────────────────────────

@Serializable
data class OrderListResponse(
    val total: Int,
    val page: Int,
    val size: Int,
    val items: List<OrderSummaryDto>,
)

@Serializable
data class OrderSummaryDto(
    val id: String,
    @SerialName("store_id")       val storeId: String,
    @SerialName("order_number")   val orderNumber: String?,
    @SerialName("customer_id")    val customerId: String?,
    @SerialName("cashier_id")     val cashierId: String?,
    val status: String,
    @SerialName("order_type")     val orderType: String,
    val subtotal: Double,
    @SerialName("tax_total")      val taxTotal: Double,
    @SerialName("discount_total") val discountTotal: Double,
    @SerialName("grand_total")    val grandTotal: Double,
    val notes: String?,
    @SerialName("created_at")     val createdAt: String?,
    @SerialName("updated_at")     val updatedAt: String,
)

@Serializable
data class OrderDetailDto(
    val id: String,
    @SerialName("store_id")       val storeId: String,
    @SerialName("order_number")   val orderNumber: String?,
    @SerialName("customer_id")    val customerId: String?,
    @SerialName("cashier_id")     val cashierId: String?,
    val status: String,
    @SerialName("order_type")     val orderType: String,
    val subtotal: Double,
    @SerialName("tax_total")      val taxTotal: Double,
    @SerialName("discount_total") val discountTotal: Double,
    @SerialName("grand_total")    val grandTotal: Double,
    val notes: String?,
    @SerialName("created_at")     val createdAt: String?,
    @SerialName("updated_at")     val updatedAt: String,
    val items: List<OrderItemDto>,
)

@Serializable
data class OrderItemDto(
    val id: String,
    @SerialName("order_id")     val orderId: String,
    @SerialName("product_id")   val productId: String,
    @SerialName("product_name") val productName: String,
    val quantity: Double,
    @SerialName("unit_price")   val unitPrice: Double,
    val discount: Double,
    val tax: Double,
    val subtotal: Double,
    val notes: String?,
)

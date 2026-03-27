package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.sync.Orders
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Valid FulfillmentStatus transitions accepted by this endpoint. */
private val VALID_FULFILLMENT_STATUSES = setOf(
    "RECEIVED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "EXPIRED", "CANCELLED"
)

fun Route.fulfillmentRoutes() {
    route("/fulfillment") {

        /**
         * GET /v1/fulfillment
         *
         * Returns all CLICK_AND_COLLECT orders for the current store, ordered by
         * created_at descending. Optionally filter by fulfillment status.
         *
         * Query params:
         *   - status  (optional) — RECEIVED | PREPARING | READY_FOR_PICKUP | PICKED_UP | EXPIRED | CANCELLED
         *   - page    (optional, default 0)
         *   - size    (optional, default 50, max 200)
         */
        get {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()

            val status = call.request.queryParameters["status"]?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            val page   = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size   = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

            val result = newSuspendedTransaction {
                var query = Orders.selectAll().where {
                    (Orders.storeId eq storeId) and
                    (Orders.isActive eq true) and
                    (Orders.orderType eq "CLICK_AND_COLLECT")
                }

                if (status != null) {
                    query = query.adjustWhere { Orders.fulfillmentStatus eq status }
                }

                val total = query.count().toInt()
                val items = query
                    .orderBy(Orders.createdAt, SortOrder.DESC)
                    .limit(size).offset((page * size).toLong())
                    .map { row ->
                        FulfillmentOrderDto(
                            id                = row[Orders.id],
                            storeId           = row[Orders.storeId],
                            orderNumber       = row[Orders.orderNumber],
                            customerId        = row[Orders.customerId],
                            cashierId         = row[Orders.cashierId],
                            status            = row[Orders.status],
                            fulfillmentStatus = row[Orders.fulfillmentStatus],
                            grandTotal        = row[Orders.grandTotal].toDouble(),
                            notes             = row[Orders.notes],
                            createdAt         = row[Orders.createdAt]?.toString(),
                            updatedAt         = row[Orders.updatedAt].toString(),
                        )
                    }
                FulfillmentListResponse(total = total, page = page, size = size, items = items)
            }

            call.respond(HttpStatusCode.OK, result)
        }

        /**
         * PATCH /v1/fulfillment/{orderId}/status
         *
         * Updates the fulfillment status of a CLICK_AND_COLLECT order.
         * Returns 404 if the order is not found or not owned by the calling store.
         * Returns 400 if the order is not CLICK_AND_COLLECT type.
         *
         * Body: UpdateFulfillmentStatusRequest { status }
         */
        patch("/{orderId}/status") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val orderId = call.parameters["orderId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)

            val body = runCatching { call.receive<UpdateFulfillmentStatusRequest>() }.getOrElse {
                return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "Invalid request body")
                )
            }

            val newStatus = body.status.uppercase()
            if (newStatus !in VALID_FULFILLMENT_STATUSES) {
                return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "VALIDATION_ERROR",
                        "status must be one of: ${VALID_FULFILLMENT_STATUSES.joinToString()}"
                    )
                )
            }

            val updated = newSuspendedTransaction {
                val existing = Orders.selectAll().where {
                    (Orders.id eq orderId) and
                    (Orders.storeId eq storeId) and
                    (Orders.isActive eq true)
                }.singleOrNull()

                when {
                    existing == null -> UpdateResult.NOT_FOUND
                    existing[Orders.orderType] != "CLICK_AND_COLLECT" -> UpdateResult.WRONG_TYPE
                    else -> {
                        val now = OffsetDateTime.now(ZoneOffset.UTC)
                        Orders.update({ Orders.id eq orderId }) {
                            it[Orders.fulfillmentStatus] = newStatus
                            it[Orders.updatedAt] = now
                        }
                        UpdateResult.OK(
                            FulfillmentOrderDto(
                                id                = existing[Orders.id],
                                storeId           = existing[Orders.storeId],
                                orderNumber       = existing[Orders.orderNumber],
                                customerId        = existing[Orders.customerId],
                                cashierId         = existing[Orders.cashierId],
                                status            = existing[Orders.status],
                                fulfillmentStatus = newStatus,
                                grandTotal        = existing[Orders.grandTotal].toDouble(),
                                notes             = existing[Orders.notes],
                                createdAt         = existing[Orders.createdAt]?.toString(),
                                updatedAt         = now.toString(),
                            )
                        )
                    }
                }
            }

            when (updated) {
                is UpdateResult.NOT_FOUND -> call.respond(HttpStatusCode.NotFound)
                is UpdateResult.WRONG_TYPE -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "Order is not a CLICK_AND_COLLECT order")
                )
                is UpdateResult.OK -> call.respond(HttpStatusCode.OK, updated.dto)
            }
        }
    }
}

private sealed interface UpdateResult {
    data object NOT_FOUND : UpdateResult
    data object WRONG_TYPE : UpdateResult
    data class OK(val dto: FulfillmentOrderDto) : UpdateResult
}

// ── DTOs ──────────────────────────────────────────────────────────────────────

@Serializable
data class FulfillmentListResponse(
    val total: Int,
    val page: Int,
    val size: Int,
    val items: List<FulfillmentOrderDto>,
)

@Serializable
data class FulfillmentOrderDto(
    val id: String,
    @SerialName("store_id")            val storeId: String,
    @SerialName("order_number")        val orderNumber: String?,
    @SerialName("customer_id")         val customerId: String?,
    @SerialName("cashier_id")          val cashierId: String?,
    val status: String,
    @SerialName("fulfillment_status")  val fulfillmentStatus: String?,
    @SerialName("grand_total")         val grandTotal: Double,
    val notes: String?,
    @SerialName("created_at")          val createdAt: String?,
    @SerialName("updated_at")          val updatedAt: String,
)

@Serializable
data class UpdateFulfillmentStatusRequest(
    val status: String,
)

package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.sync.Customers as NormalizedCustomers
import com.zyntasolutions.zyntapos.api.sync.Orders as NormalizedOrders
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * GDPR customer data export endpoint (S4-10).
 *
 * GET /v1/export/customer/{customerId}
 *
 * Returns all PII and order data associated with a customer for GDPR
 * data portability compliance (Article 20). Requires admin or manager role.
 */
fun Route.exportRoutes() {
    val auditService: AdminAuditService by inject()

    route("/export") {
        get("/customer/{customerId}") {
            val customerId = call.parameters["customerId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "customerId required"))
                return@get
            }

            if (!call.validateOr422 {
                requireUUID("customerId", customerId)
            }) return@get

            val principal = call.principal<JWTPrincipal>()
            val adminId = principal?.payload?.subject
            val role = principal?.payload?.getClaim("role")?.asString()

            // Only ADMIN or MANAGER roles can export customer data
            if (role != "ADMIN" && role != "MANAGER") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "Only ADMIN or MANAGER can export customer data")
                )
                return@get
            }

            // Query customer data from normalized entity tables (V12)
            val export = newSuspendedTransaction {
                val customerRows = NormalizedCustomers.selectAll()
                    .where { NormalizedCustomers.id eq customerId }
                    .toList()

                if (customerRows.isEmpty()) {
                    return@newSuspendedTransaction null
                }

                val customer = customerRows.first()
                val orders = NormalizedOrders.selectAll()
                    .where { NormalizedOrders.customerId eq customerId }
                    .map { row ->
                        CustomerOrderExport(
                            id = row[NormalizedOrders.id],
                            orderNumber = row[NormalizedOrders.orderNumber],
                            status = row[NormalizedOrders.status],
                            grandTotal = row[NormalizedOrders.grandTotal]?.toDouble(),
                            createdAt = row[NormalizedOrders.createdAt]?.toInstant()?.toEpochMilli(),
                        )
                    }

                CustomerDataExportResponse(
                    customer = CustomerProfileExport(
                        id = customer[NormalizedCustomers.id],
                        name = customer[NormalizedCustomers.name],
                        email = customer[NormalizedCustomers.email],
                        phone = customer[NormalizedCustomers.phone],
                        address = customer[NormalizedCustomers.address],
                        notes = customer[NormalizedCustomers.notes],
                        loyaltyPoints = customer[NormalizedCustomers.loyaltyPoints],
                    ),
                    orders = orders,
                    exportedAt = java.time.Instant.now().toEpochMilli(),
                )
            }

            if (export == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Customer not found"))
                return@get
            }

            // Audit the export
            auditService.log(
                adminId = adminId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                adminName = null,
                eventType = "CUSTOMER_DATA_EXPORTED",
                category = "DATA",
                entityType = "customer",
                entityId = customerId,
                success = true,
            )

            call.respond(HttpStatusCode.OK, export)
        }
    }
}

@Serializable
data class CustomerDataExportResponse(
    val customer: CustomerProfileExport,
    val orders: List<CustomerOrderExport>,
    val exportedAt: Long,
)

@Serializable
data class CustomerProfileExport(
    val id: String,
    val name: String?,
    val email: String?,
    val phone: String?,
    val address: String?,
    val notes: String?,
    val loyaltyPoints: Int?,
)

@Serializable
data class CustomerOrderExport(
    val id: String,
    val orderNumber: String?,
    val status: String?,
    val grandTotal: Double?,
    val createdAt: Long?,
)

package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.sync.Customers
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject

fun Route.adminCustomersRoutes() {
    val authService: AdminAuthService by inject()

    route("/admin/customers") {

        /**
         * GET /admin/customers/global
         *
         * Cross-store read-only customer directory for the admin panel.
         * HELPDESK can look up customers across stores for support queries.
         *
         * Query params:
         *   - search  (optional) — case-insensitive substring match on name / email / phone
         *   - storeId (optional) — filter to a specific store's customers
         *   - page    (optional, default 0)
         *   - size    (optional, default 50, max 200)
         *
         * Roles: ADMIN, OPERATOR, HELPDESK  (customers:read)
         */
        get("/global") {
            val user = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(user.role, "customers:read")) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "customers:read permission required")
                )
            }

            val search  = call.request.queryParameters["search"]?.trim()?.takeIf { it.isNotEmpty() }
            val storeId = call.request.queryParameters["storeId"]?.trim()?.takeIf { it.isNotEmpty() }
            val page    = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size    = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

            val result = newSuspendedTransaction {
                var query = Customers.selectAll().where { Customers.isActive eq true }

                if (storeId != null) {
                    query = query.adjustWhere { Customers.storeId eq storeId }
                }

                if (search != null) {
                    val pattern = "%${search.lowercase()}%"
                    query = query.adjustWhere {
                        (Customers.name.lowerCase() like pattern) or
                        (Customers.email.lowerCase() like pattern) or
                        (Customers.phone.lowerCase() like pattern)
                    }
                }

                val total = query.count().toInt()
                val items = query
                    .orderBy(Customers.name)
                    .limit(size).offset((page * size).toLong())
                    .map { row ->
                        CustomerSummaryDto(
                            id            = row[Customers.id],
                            storeId       = row[Customers.storeId],
                            name          = row[Customers.name],
                            email         = row[Customers.email],
                            phone         = row[Customers.phone],
                            loyaltyPoints = row[Customers.loyaltyPoints],
                        )
                    }
                GlobalCustomersResponse(total = total, page = page, size = size, items = items)
            }

            call.respond(HttpStatusCode.OK, result)
        }
    }
}

// ── Response DTOs ──────────────────────────────────────────────────────────────

@Serializable
data class GlobalCustomersResponse(
    val total: Int,
    val page: Int,
    val size: Int,
    val items: List<CustomerSummaryDto>,
)

@Serializable
data class CustomerSummaryDto(
    val id: String,
    val storeId: String?,
    val name: String,
    val email: String?,
    val phone: String?,
    val loyaltyPoints: Int,
)

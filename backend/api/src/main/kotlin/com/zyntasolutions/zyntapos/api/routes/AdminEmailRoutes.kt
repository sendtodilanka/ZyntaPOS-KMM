package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.db.EmailDeliveryLogs
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject

/**
 * Admin email management routes (TODO-008a).
 *
 * ## Routes
 * - `GET /admin/email/delivery-logs` — paginated delivery log (email:logs permission)
 */
fun Route.adminEmailRoutes() {
    val authService: AdminAuthService by inject()

    route("/admin/email") {

        get("/delivery-logs") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "email:logs")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }

            val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val offset = ((page - 1) * pageSize).toLong()

            val (logs, total) = newSuspendedTransaction {
                val total = EmailDeliveryLogs.selectAll().count()
                val rows = EmailDeliveryLogs
                    .selectAll()
                    .orderBy(EmailDeliveryLogs.createdAt, SortOrder.DESC)
                    .limit(pageSize)
                    .offset(offset)
                    .map { row ->
                        DeliveryLogEntry(
                            id = row[EmailDeliveryLogs.id].toString(),
                            to = row[EmailDeliveryLogs.toAddress],
                            subject = row[EmailDeliveryLogs.subject],
                            template = row[EmailDeliveryLogs.templateSlug],
                            status = row[EmailDeliveryLogs.status],
                            sentAt = row[EmailDeliveryLogs.sentAt]?.toString(),
                            error = row[EmailDeliveryLogs.errorMessage],
                        )
                    }
                Pair(rows, total)
            }

            call.respond(HttpStatusCode.OK, DeliveryLogResponse(
                logs = logs,
                total = total,
                page = page,
                pageSize = pageSize,
            ))
        }
    }
}

@Serializable
private data class DeliveryLogEntry(
    val id: String,
    val to: String,
    val subject: String,
    val template: String?,
    val status: String,
    val sentAt: String?,
    val error: String? = null,
)

@Serializable
private data class DeliveryLogResponse(
    val logs: List<DeliveryLogEntry>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)

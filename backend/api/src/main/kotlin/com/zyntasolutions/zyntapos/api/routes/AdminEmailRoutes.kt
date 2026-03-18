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
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Admin email management routes (TODO-008a).
 *
 * ## Routes
 * - `GET /admin/email/delivery-logs` — paginated delivery log with optional filters (email:logs permission)
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

            // Optional filters
            val statusFilter = call.request.queryParameters["status"]
            val startDate = call.request.queryParameters["startDate"]?.runCatching {
                LocalDate.parse(this).atStartOfDay().atOffset(ZoneOffset.UTC)
            }?.getOrNull()
            val endDate = call.request.queryParameters["endDate"]?.runCatching {
                LocalDate.parse(this).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
            }?.getOrNull()

            val (logs, total) = newSuspendedTransaction {
                val query = EmailDeliveryLogs.selectAll().apply {
                    if (!statusFilter.isNullOrBlank()) {
                        andWhere { EmailDeliveryLogs.status eq statusFilter }
                    }
                    if (startDate != null) {
                        andWhere { EmailDeliveryLogs.createdAt greaterEq startDate }
                    }
                    if (endDate != null) {
                        andWhere { EmailDeliveryLogs.createdAt less endDate }
                    }
                }

                val total = query.count()
                val rows = query
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

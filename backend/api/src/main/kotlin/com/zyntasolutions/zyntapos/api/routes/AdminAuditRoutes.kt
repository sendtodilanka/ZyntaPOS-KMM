package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.adminAuditRoutes() {
    val auditService: AdminAuditService by inject()
    val authService: AdminAuthService by inject()

    route("/admin/audit") {

        get {
            resolveAdminUser(call, authService) ?: return@get
            val page      = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size      = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
            val category  = call.request.queryParameters["category"]
            val eventType = call.request.queryParameters["eventType"]
            val adminId   = call.request.queryParameters["userId"]
            val from      = call.request.queryParameters["from"]
            val to        = call.request.queryParameters["to"]
            val search    = call.request.queryParameters["search"]

            call.respond(
                HttpStatusCode.OK,
                auditService.listEntries(page, size, category, eventType, adminId, from, to, search)
            )
        }

        get("/export") {
            resolveAdminUser(call, authService) ?: return@get
            val category  = call.request.queryParameters["category"]
            val eventType = call.request.queryParameters["eventType"]
            val from      = call.request.queryParameters["from"]
            val to        = call.request.queryParameters["to"]

            val entries = auditService.exportEntries(category, eventType, from, to)
            val csv = buildString {
                appendLine("id,eventType,category,userId,userName,entityType,entityId,success,ipAddress,createdAt")
                for (e in entries) {
                    appendLine("${e.id},${e.eventType},${e.category},${e.userId ?: ""},${e.userName ?: ""},${e.entityType ?: ""},${e.entityId ?: ""},${e.success},${e.ipAddress ?: ""},${e.createdAt}")
                }
            }

            call.response.headers.append(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"audit-export.csv\""
            )
            call.respondText(csv, ContentType.Text.CSV)
        }
    }
}

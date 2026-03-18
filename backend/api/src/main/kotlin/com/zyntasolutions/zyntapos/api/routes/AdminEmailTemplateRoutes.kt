package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.db.EmailTemplates
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.koin.ktor.ext.inject
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Admin email template management routes (A2 completion).
 *
 * ## Routes
 * - `GET  /admin/email/templates`          — list all email templates
 * - `GET  /admin/email/templates/{slug}`   — get single template by slug
 * - `PUT  /admin/email/templates/{slug}`   — update template subject + body
 */
fun Route.adminEmailTemplateRoutes() {
    val authService: AdminAuthService by inject()

    route("/admin/email/templates") {

        get {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "email:settings")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }

            val templates = newSuspendedTransaction {
                EmailTemplates.selectAll()
                    .orderBy(EmailTemplates.slug, SortOrder.ASC)
                    .map { row ->
                        EmailTemplateDto(
                            slug = row[EmailTemplates.slug],
                            name = row[EmailTemplates.name],
                            subject = row[EmailTemplates.subject],
                            htmlBody = row[EmailTemplates.htmlBody],
                            updatedAt = row[EmailTemplates.updatedAt].toString(),
                        )
                    }
            }

            call.respond(HttpStatusCode.OK, EmailTemplateListResponse(templates))
        }

        get("/{slug}") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "email:settings")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }

            val slug = call.parameters["slug"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Missing slug"))
                return@get
            }

            val template = newSuspendedTransaction {
                EmailTemplates.selectAll()
                    .where { EmailTemplates.slug eq slug }
                    .firstOrNull()
                    ?.let { row ->
                        EmailTemplateDto(
                            slug = row[EmailTemplates.slug],
                            name = row[EmailTemplates.name],
                            subject = row[EmailTemplates.subject],
                            htmlBody = row[EmailTemplates.htmlBody],
                            updatedAt = row[EmailTemplates.updatedAt].toString(),
                        )
                    }
            }

            if (template == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Template not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, template)
        }

        put("/{slug}") {
            val admin = resolveAdminUser(call, authService) ?: return@put
            if (!AdminPermissions.check(admin.role, "email:settings")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@put
            }

            val slug = call.parameters["slug"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Missing slug"))
                return@put
            }

            val body = call.receive<UpdateTemplateRequest>()
            if (body.subject.isBlank() || body.htmlBody.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Subject and body are required"))
                return@put
            }

            val updated = newSuspendedTransaction {
                EmailTemplates.update({ EmailTemplates.slug eq slug }) {
                    it[subject] = body.subject
                    it[htmlBody] = body.htmlBody
                    it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }

            if (updated == 0) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Template not found"))
                return@put
            }

            call.respond(HttpStatusCode.OK, mapOf("message" to "Template updated"))
        }
    }
}

@Serializable
data class EmailTemplateDto(
    val slug: String,
    val name: String,
    val subject: String,
    val htmlBody: String,
    val updatedAt: String,
)

@Serializable
private data class EmailTemplateListResponse(
    val templates: List<EmailTemplateDto>,
)

@Serializable
private data class UpdateTemplateRequest(
    val subject: String,
    val htmlBody: String,
)

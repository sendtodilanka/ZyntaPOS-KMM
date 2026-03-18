package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.AdminTicketService
import com.zyntasolutions.zyntapos.api.service.EmailService
import com.zyntasolutions.zyntapos.api.service.AddCommentRequest
import com.zyntasolutions.zyntapos.api.service.AssignTicketRequest
import com.zyntasolutions.zyntapos.api.service.BulkAssignRequest
import com.zyntasolutions.zyntapos.api.service.BulkResolveRequest
import com.zyntasolutions.zyntapos.api.service.CreateTicketRequest
import com.zyntasolutions.zyntapos.api.service.ResolveTicketRequest
import com.zyntasolutions.zyntapos.api.service.UpdateTicketRequest
import com.zyntasolutions.zyntapos.common.validation.validateOr422
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.adminTicketRoutes() {
    val ticketService: AdminTicketService by inject()
    val authService: AdminAuthService by inject()
    val auditService: AdminAuditService by inject()
    val emailService: EmailService by inject()

    route("/admin/tickets") {

        // GET /admin/tickets — list tickets with filters
        get {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "tickets:read")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }

            val page     = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size     = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val status   = call.request.queryParameters["status"]
            val priority = call.request.queryParameters["priority"]
            val category = call.request.queryParameters["category"]
            val assignedTo = call.request.queryParameters["assignedTo"]
            val storeId  = call.request.queryParameters["storeId"]
            val search   = call.request.queryParameters["search"]
            val searchBody    = call.request.queryParameters["searchBody"] == "true"
            val createdAfter  = call.request.queryParameters["createdAfter"]?.toLongOrNull()
            val createdBefore = call.request.queryParameters["createdBefore"]?.toLongOrNull()

            val result = ticketService.listTickets(status, priority, category, assignedTo, storeId, search, searchBody, createdAfter, createdBefore, page, size)
            call.respond(HttpStatusCode.OK, result)
        }

        // GET /admin/tickets/metrics — aggregate ticket metrics
        get("/metrics") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "tickets:read")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }
            val metrics = ticketService.getMetrics()
            call.respond(HttpStatusCode.OK, metrics)
        }

        // POST /admin/tickets/bulk-assign — bulk assign tickets
        post("/bulk-assign") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(admin.role, "tickets:assign")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@post
            }
            val body = call.receive<BulkAssignRequest>()
            val assigneeId = runCatching { UUID.fromString(body.assigneeId) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid assignee ID"))
            if (body.ticketIds.isEmpty() || body.ticketIds.size > 100) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_COUNT", "Select 1-100 tickets"))
                return@post
            }
            val result = ticketService.bulkAssign(body.ticketIds, assigneeId)
            call.respond(HttpStatusCode.OK, result)
        }

        // POST /admin/tickets/bulk-resolve — bulk resolve tickets
        post("/bulk-resolve") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(admin.role, "tickets:resolve")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@post
            }
            val body = call.receive<BulkResolveRequest>()
            if (body.ticketIds.isEmpty() || body.ticketIds.size > 100) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_COUNT", "Select 1-100 tickets"))
                return@post
            }
            if (body.resolutionNote.isBlank()) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("VALIDATION_ERROR", "Resolution note is required"))
                return@post
            }
            val result = ticketService.bulkResolve(body.ticketIds, body.resolutionNote, admin.id)
            call.respond(HttpStatusCode.OK, result)
        }

        // GET /admin/tickets/export — export tickets as CSV
        get("/export") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "tickets:read")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }
            val status   = call.request.queryParameters["status"]
            val priority = call.request.queryParameters["priority"]
            val category = call.request.queryParameters["category"]
            val assignedTo = call.request.queryParameters["assignedTo"]
            val storeId  = call.request.queryParameters["storeId"]
            val search   = call.request.queryParameters["search"]

            val csv = ticketService.exportTicketsCsv(status, priority, category, assignedTo, storeId, search)
            call.response.header("Content-Disposition", "attachment; filename=tickets.csv")
            call.respondText(csv, ContentType.Text.CSV, HttpStatusCode.OK)
        }

        // POST /admin/tickets — create a new ticket
        post {
            val admin = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(admin.role, "tickets:create")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@post
            }

            val body = call.receive<CreateTicketRequest>()
            if (!call.validateOr422 {
                requireNotBlank("customerName", body.customerName)
                requireNotBlank("title", body.title)
                requireMaxLength("title", body.title, 255)
                requireNotBlank("category", body.category)
                requireNotBlank("priority", body.priority)
            }) return@post

            if (!ticketService.isValidCategory(body.category)) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_CATEGORY",
                    "Invalid category. Must be one of: HARDWARE, SOFTWARE, SYNC, BILLING, OTHER"))
                return@post
            }
            if (!ticketService.isValidPriority(body.priority)) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_PRIORITY",
                    "Invalid priority. Must be one of: LOW, MEDIUM, HIGH, CRITICAL"))
                return@post
            }

            val ticket = ticketService.createTicket(body, admin.id)

            body.customerEmail?.takeIf { it.isNotBlank() }?.let {
                emailService.sendTicketCreated(it, ticket.ticketNumber, ticket.title, ticket.customerAccessToken)
            }

            auditService.log(
                adminId    = admin.id,
                adminName  = admin.name,
                eventType  = "TICKET_CREATED",
                category   = "SUPPORT",
                entityType = "support_ticket",
                entityId   = ticket.id,
                newValues  = mapOf("ticketNumber" to ticket.ticketNumber, "priority" to ticket.priority),
                success    = true,
            )
            call.respond(HttpStatusCode.Created, ticket)
        }

        // GET /admin/tickets/{id} — get ticket detail with comments
        get("/{id}") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "tickets:read")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }

            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Ticket ID required"))
            val ticket = ticketService.getTicket(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))
            call.respond(HttpStatusCode.OK, ticket)
        }

        // PATCH /admin/tickets/{id} — update title/description/priority
        patch("/{id}") {
            val admin = resolveAdminUser(call, authService) ?: return@patch
            if (!AdminPermissions.check(admin.role, "tickets:update")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@patch
            }

            val id = call.parameters["id"] ?: return@patch call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Ticket ID required"))
            val body = call.receive<UpdateTicketRequest>()

            body.priority?.let {
                if (!ticketService.isValidPriority(it)) {
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_PRIORITY",
                        "Invalid priority. Must be one of: LOW, MEDIUM, HIGH, CRITICAL"))
                    return@patch
                }
            }

            val ticket = ticketService.updateTicket(id, body)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))
            call.respond(HttpStatusCode.OK, ticket)
        }

        // POST /admin/tickets/{id}/assign — assign to an operator
        post("/{id}/assign") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(admin.role, "tickets:assign")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@post
            }

            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Ticket ID required"))
            val body = call.receive<AssignTicketRequest>()

            val assigneeId = runCatching { UUID.fromString(body.assigneeId) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_ID", "Invalid assignee ID"))

            val ticket = ticketService.assignTicket(id, assigneeId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))
            call.respond(HttpStatusCode.OK, ticket)
        }

        // POST /admin/tickets/{id}/resolve — resolve a ticket (ADMIN/OPERATOR only)
        post("/{id}/resolve") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(admin.role, "tickets:resolve")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN",
                    "Only ADMIN or OPERATOR can resolve tickets"))
                return@post
            }

            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Ticket ID required"))
            val body = call.receive<ResolveTicketRequest>()
            if (!call.validateOr422 {
                requireNotBlank("resolutionNote", body.resolutionNote)
            }) return@post

            val ticket = try {
                ticketService.resolveTicket(id, body, admin.id)
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_STATE", e.message ?: ""))
                return@post
            } ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))

            ticket.customerEmail?.takeIf { it.isNotBlank() }?.let {
                emailService.sendTicketUpdated(it, ticket.ticketNumber, ticket.status)
            }

            auditService.log(
                adminId    = admin.id,
                adminName  = admin.name,
                eventType  = "TICKET_RESOLVED",
                category   = "SUPPORT",
                entityType = "support_ticket",
                entityId   = id,
                newValues  = mapOf("resolutionNote" to body.resolutionNote, "timeSpentMin" to body.timeSpentMin.toString()),
                success    = true,
            )
            call.respond(HttpStatusCode.OK, ticket)
        }

        // POST /admin/tickets/{id}/close — close a ticket (must be RESOLVED first)
        post("/{id}/close") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(admin.role, "tickets:close")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@post
            }

            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Ticket ID required"))

            val ticket = try {
                ticketService.closeTicket(id)
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_STATE", e.message ?: ""))
                return@post
            } ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))

            call.respond(HttpStatusCode.OK, ticket)
        }

        // GET /admin/tickets/{id}/email-threads — list email threads for a ticket
        get("/{id}/email-threads") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "tickets:read")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }

            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Ticket ID required"))
            val threads = ticketService.getEmailThreads(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))
            call.respond(HttpStatusCode.OK, threads)
        }

        // GET /admin/tickets/{id}/comments — list ticket comments
        get("/{id}/comments") {
            val admin = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(admin.role, "tickets:read")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@get
            }

            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Ticket ID required"))
            val comments = ticketService.listComments(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))
            call.respond(HttpStatusCode.OK, comments)
        }

        // POST /admin/tickets/{id}/comments — add a comment
        post("/{id}/comments") {
            val admin = resolveAdminUser(call, authService) ?: return@post
            if (!AdminPermissions.check(admin.role, "tickets:comment")) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient permissions"))
                return@post
            }

            val id = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Ticket ID required"))
            val body = call.receive<AddCommentRequest>()
            if (!call.validateOr422 {
                requireNotBlank("body", body.body)
            }) return@post

            val comment = ticketService.addComment(id, body, admin.id)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))
            call.respond(HttpStatusCode.Created, comment)
        }
    }
}

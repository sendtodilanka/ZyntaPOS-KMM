package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.service.AdminTicketService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Public (unauthenticated) route for customers to check their ticket status.
 * The customer_access_token in the URL acts as the credential — no login required.
 *
 * Returns a limited public view: ticket number, status, priority, title, timestamps.
 * Does NOT expose internal notes, assignee details, or customer PII.
 */
fun Route.customerTicketRoutes() {
    val ticketService: AdminTicketService by inject()

    // GET /tickets/status/{token} — public ticket status check
    get("/tickets/status/{token}") {
        val token = call.parameters["token"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("MISSING_TOKEN", "Token is required"))

        val ticket = ticketService.getByCustomerToken(token)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Ticket not found"))

        call.respond(HttpStatusCode.OK, ticket)
    }
}

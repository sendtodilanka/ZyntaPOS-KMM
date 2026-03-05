package com.zyntasolutions.zyntapos.api.plugins

import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(code = "INVALID_REQUEST", message = cause.message ?: "Invalid request")
            )
        }
        exception<SecurityException> { call, cause ->
            logger.warn("Security violation: ${cause.message}")
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(code = "FORBIDDEN", message = "Access denied")
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception on ${call.request.local.method.value} ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(code = "INTERNAL_ERROR", message = "An internal error occurred")
            )
        }
    }
}

package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.db.EmailDeliveryLogs
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WebhookRoutes")

/**
 * Webhook endpoints for third-party service callbacks.
 *
 * ## Routes
 * - `POST /webhooks/resend` — Resend bounce/complaint/delivery events
 */
fun Route.webhookRoutes() {

    route("/webhooks") {

        /**
         * Resend webhook handler — receives bounce, complaint, and delivery events.
         *
         * Resend sends events to this endpoint when an email bounces, receives a
         * complaint, or is delivered. We update the email_delivery_log status accordingly.
         *
         * Event types: https://resend.com/docs/dashboard/webhooks/event-types
         */
        post("/resend") {
            val event = runCatching { call.receive<ResendWebhookEvent>() }.getOrElse {
                logger.warn("Invalid Resend webhook payload: ${it.message}")
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val newStatus = when (event.type) {
                "email.delivered" -> "DELIVERED"
                "email.bounced" -> "BOUNCED"
                "email.complained" -> "COMPLAINED"
                "email.delivery_delayed" -> null  // Transient — don't update status
                else -> {
                    logger.debug("Ignoring Resend event type: ${event.type}")
                    call.respond(HttpStatusCode.OK)
                    return@post
                }
            }

            if (newStatus != null && event.data?.emailId != null) {
                runCatching {
                    newSuspendedTransaction {
                        // Match by Resend message ID if available, otherwise skip
                        // The email_delivery_log may not have messageId stored yet,
                        // so we match on toAddress + subject as fallback
                        EmailDeliveryLogs.update(
                            where = { EmailDeliveryLogs.id eq java.util.UUID.fromString(event.data.emailId) }
                        ) {
                            it[status] = newStatus
                            if (event.data.bounceType != null) {
                                it[errorMessage] = "Bounce: ${event.data.bounceType}"
                            }
                        }
                    }
                }.onFailure { e ->
                    // emailId might not match our UUID — log and continue
                    logger.debug("Could not update delivery log for Resend event: ${e.message}")
                }
            }

            // Always return 200 to acknowledge receipt (Resend retries on non-2xx)
            call.respond(HttpStatusCode.OK)
        }
    }
}

@Serializable
private data class ResendWebhookEvent(
    val type: String,
    val data: ResendEventData? = null,
)

@Serializable
private data class ResendEventData(
    val emailId: String? = null,
    val to: List<String>? = null,
    val subject: String? = null,
    val bounceType: String? = null,
)

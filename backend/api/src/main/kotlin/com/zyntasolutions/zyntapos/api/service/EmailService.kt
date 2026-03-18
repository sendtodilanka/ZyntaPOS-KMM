package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.db.EmailDeliveryLogs
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Transactional email delivery via the Resend HTTP API.
 *
 * Uses the Ktor CIO client (already in the project) to POST to `api.resend.com/emails`.
 * Email failures are logged but never thrown — email delivery must not crash request flow.
 * When [AppConfig.resendApiKey] is blank the service logs a warning and returns success silently.
 *
 * ## Templates
 * Templates are inlined as Kotlin string literals for simplicity (no template engine dep).
 * Each `send*` method builds an HTML body with the minimal data for that email type.
 */
class EmailService(private val config: AppConfig) {

    private val log = LoggerFactory.getLogger(EmailService::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Serializable
    private data class ResendEmailRequest(
        val from: String,
        val to: List<String>,
        val subject: String,
        val html: String,
    )

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun sendPasswordReset(toEmail: String, resetLink: String) =
        send(
            to = toEmail,
            subject = "Reset your ZyntaPOS password",
            html = passwordResetHtml(resetLink),
            templateSlug = "password_reset",
        )

    suspend fun sendWelcomeAdmin(toEmail: String, name: String) =
        send(
            to = toEmail,
            subject = "Welcome to ZyntaPOS Admin Panel",
            html = welcomeAdminHtml(name),
            templateSlug = "welcome_admin",
        )

    suspend fun sendTicketCreated(toEmail: String, ticketNumber: String, title: String, customerAccessToken: String? = null) =
        send(
            to = toEmail,
            subject = "Support Ticket Created: #$ticketNumber",
            html = ticketCreatedHtml(ticketNumber, title, customerAccessToken),
            templateSlug = "ticket_created",
        )

    suspend fun sendTicketUpdated(toEmail: String, ticketNumber: String, newStatus: String) =
        send(
            to = toEmail,
            subject = "Ticket #$ticketNumber status updated: $newStatus",
            html = ticketUpdatedHtml(ticketNumber, newStatus),
            templateSlug = "ticket_updated",
        )

    suspend fun sendSlaBreachAlert(toEmail: String, ticketNumber: String, title: String, priority: String) =
        send(
            to = toEmail,
            subject = "SLA Breach: $ticketNumber [$priority]",
            html = slaBreachHtml(ticketNumber, title, priority),
            templateSlug = "sla_breach",
        )

    suspend fun sendTicketReply(toEmail: String, customerName: String?, ticketNumber: String, agentName: String, messageBody: String) =
        send(
            to = toEmail,
            subject = "Re: [$ticketNumber] Your support request",
            html = ticketReplyHtml(ticketNumber, customerName, agentName, messageBody),
            templateSlug = "ticket_reply",
        )

    // ── Private helpers ────────────────────────────────────────────────────────

    private suspend fun send(to: String, subject: String, html: String, templateSlug: String? = null) {
        if (config.resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY not configured — skipping email to $to (subject: $subject)")
            return
        }
        withContext(Dispatchers.IO) {
            val fromAddress = config.emailFromAddress
            val result = runCatching {
                val response = client.post("https://api.resend.com/emails") {
                    bearerAuth(config.resendApiKey)
                    contentType(ContentType.Application.Json)
                    setBody(
                        ResendEmailRequest(
                            from = "${config.emailFromName} <$fromAddress>",
                            to   = listOf(to),
                            subject = subject,
                            html = html,
                        )
                    )
                }
                if (response.status != HttpStatusCode.OK && response.status.value !in 200..299) {
                    val body = response.bodyAsText()
                    log.warn("Resend API returned ${response.status} for email to $to: $body")
                    throw RuntimeException("Resend API ${response.status}: $body")
                }
                log.info("Email sent to $to (subject: $subject)")
            }
            // Log delivery to email_delivery_log table
            logDelivery(to, fromAddress, subject, templateSlug, result)
        }
    }

    private suspend fun logDelivery(
        to: String,
        from: String,
        subject: String,
        templateSlug: String?,
        result: Result<Unit>,
    ) {
        runCatching {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            newSuspendedTransaction {
                EmailDeliveryLogs.insert {
                    it[id]           = UUID.randomUUID()
                    it[toAddress]    = to
                    it[fromAddress]  = from
                    it[EmailDeliveryLogs.subject] = subject
                    it[EmailDeliveryLogs.templateSlug] = templateSlug
                    it[status]       = if (result.isSuccess) "SENT" else "FAILED"
                    it[errorMessage] = result.exceptionOrNull()?.message
                    it[sentAt]       = if (result.isSuccess) now else null
                    it[createdAt]    = now
                }
            }
        }.onFailure { e ->
            log.warn("Failed to log email delivery: ${e.message}")
        }
    }

    /** Closes the HTTP client. Call during application shutdown (§3.4). */
    fun close() {
        client.close()
    }

    // ── HTML templates ─────────────────────────────────────────────────────────

    private fun passwordResetHtml(resetLink: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2>Reset your ZyntaPOS password</h2>
        <p>You requested a password reset for your ZyntaPOS Admin Panel account.</p>
        <p>Click the link below to set a new password. This link expires in 1 hour.</p>
        <p><a href="${resetLink.htmlEscape()}" style="background:#1976d2;color:#fff;padding:12px 24px;border-radius:4px;text-decoration:none;display:inline-block">Reset Password</a></p>
        <p>If you did not request this, you can safely ignore this email.</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    private fun welcomeAdminHtml(name: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2>Welcome to ZyntaPOS, ${name.htmlEscape()}!</h2>
        <p>Your ZyntaPOS Admin Panel account has been created.</p>
        <p>You can access the admin panel at <a href="${config.adminPanelUrl}">${config.adminPanelUrl.htmlEscape()}</a>.</p>
        <p>Use the credentials set during account creation to log in. We recommend enabling MFA immediately.</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    private fun ticketCreatedHtml(ticketNumber: String, title: String, customerAccessToken: String?) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2>Support Ticket Created: #${ticketNumber.htmlEscape()}</h2>
        <p>Your support ticket has been received and will be addressed shortly.</p>
        <p><strong>Title:</strong> ${title.htmlEscape()}</p>
        ${if (customerAccessToken != null) """
        <p>Track your ticket status: <a href="${config.adminPanelUrl}/ticket-status/${customerAccessToken.htmlEscape()}" style="color:#1976d2">View Ticket Status</a></p>
        """ else """
        <p>You can track the status of your ticket in the ZyntaPOS Admin Panel.</p>
        """}
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    private fun ticketUpdatedHtml(ticketNumber: String, newStatus: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2>Ticket #${ticketNumber.htmlEscape()} Updated</h2>
        <p>The status of your support ticket has been updated to: <strong>${newStatus.htmlEscape()}</strong></p>
        <p>Log in to the ZyntaPOS Admin Panel for more details.</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    private fun slaBreachHtml(ticketNumber: String, title: String, priority: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2 style="color:#dc2626">SLA Breach Alert</h2>
        <p>Ticket <strong>#${ticketNumber.htmlEscape()}</strong> has breached its SLA deadline.</p>
        <p><strong>Title:</strong> ${title.htmlEscape()}</p>
        <p><strong>Priority:</strong> ${priority.htmlEscape()}</p>
        <p style="color:#dc2626;font-weight:600">Please take immediate action to resolve this ticket.</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    private fun ticketReplyHtml(ticketNumber: String, customerName: String?, agentName: String, messageBody: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <p>Dear ${(customerName ?: "Customer").htmlEscape()},</p>
        <p>${messageBody.htmlEscape().replace("\n", "<br/>")}</p>
        <hr style="border:none;border-top:1px solid #e5e7eb;margin:16px 0"/>
        <p style="color:#666;font-size:12px">Replied by ${agentName.htmlEscape()} &mdash; Ticket #${ticketNumber.htmlEscape()}</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    /** Escapes HTML special characters to prevent XSS in email templates (A2). */
    private fun String.htmlEscape(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
}

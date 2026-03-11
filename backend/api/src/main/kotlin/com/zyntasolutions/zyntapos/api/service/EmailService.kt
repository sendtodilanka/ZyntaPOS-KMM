package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.config.AppConfig
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
import org.slf4j.LoggerFactory

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
        )

    suspend fun sendWelcomeAdmin(toEmail: String, name: String) =
        send(
            to = toEmail,
            subject = "Welcome to ZyntaPOS Admin Panel",
            html = welcomeAdminHtml(name),
        )

    suspend fun sendTicketCreated(toEmail: String, ticketNumber: String, title: String) =
        send(
            to = toEmail,
            subject = "Support Ticket Created: #$ticketNumber",
            html = ticketCreatedHtml(ticketNumber, title),
        )

    suspend fun sendTicketUpdated(toEmail: String, ticketNumber: String, newStatus: String) =
        send(
            to = toEmail,
            subject = "Ticket #$ticketNumber status updated: $newStatus",
            html = ticketUpdatedHtml(ticketNumber, newStatus),
        )

    // ── Private helpers ────────────────────────────────────────────────────────

    private suspend fun send(to: String, subject: String, html: String) {
        if (config.resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY not configured — skipping email to $to (subject: $subject)")
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client.post("https://api.resend.com/emails") {
                    bearerAuth(config.resendApiKey)
                    contentType(ContentType.Application.Json)
                    setBody(
                        ResendEmailRequest(
                            from = "${config.emailFromName} <${config.emailFromAddress}>",
                            to   = listOf(to),
                            subject = subject,
                            html = html,
                        )
                    )
                }
                if (response.status != HttpStatusCode.OK && response.status.value !in 200..299) {
                    log.warn("Resend API returned ${response.status} for email to $to: ${response.bodyAsText()}")
                } else {
                    log.info("Email sent to $to (subject: $subject)")
                }
            }.onFailure { e ->
                log.error("Failed to send email to $to (subject: $subject): ${e.message}", e)
            }
        }
    }

    // ── HTML templates ─────────────────────────────────────────────────────────

    private fun passwordResetHtml(resetLink: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2>Reset your ZyntaPOS password</h2>
        <p>You requested a password reset for your ZyntaPOS Admin Panel account.</p>
        <p>Click the link below to set a new password. This link expires in 1 hour.</p>
        <p><a href="$resetLink" style="background:#1976d2;color:#fff;padding:12px 24px;border-radius:4px;text-decoration:none;display:inline-block">Reset Password</a></p>
        <p>If you did not request this, you can safely ignore this email.</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    private fun welcomeAdminHtml(name: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2>Welcome to ZyntaPOS, $name!</h2>
        <p>Your ZyntaPOS Admin Panel account has been created.</p>
        <p>You can access the admin panel at <a href="${config.adminPanelUrl}">${config.adminPanelUrl}</a>.</p>
        <p>Use the credentials set during account creation to log in. We recommend enabling MFA immediately.</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    private fun ticketCreatedHtml(ticketNumber: String, title: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2>Support Ticket Created: #$ticketNumber</h2>
        <p>Your support ticket has been received and will be addressed shortly.</p>
        <p><strong>Title:</strong> $title</p>
        <p>You can track the status of your ticket in the ZyntaPOS Admin Panel.</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()

    private fun ticketUpdatedHtml(ticketNumber: String, newStatus: String) = """
        <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto">
        <h2>Ticket #$ticketNumber Updated</h2>
        <p>The status of your support ticket has been updated to: <strong>$newStatus</strong></p>
        <p>Log in to the ZyntaPOS Admin Panel for more details.</p>
        <p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
        </body></html>
    """.trimIndent()
}

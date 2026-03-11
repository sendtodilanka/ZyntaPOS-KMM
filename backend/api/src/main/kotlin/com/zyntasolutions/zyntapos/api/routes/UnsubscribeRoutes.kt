package com.zyntasolutions.zyntapos.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

// ── Exposed table for email preferences ──────────────────────────────────────

object EmailPreferences : Table("email_preferences") {
    val userId            = uuid("user_id")
    val marketingEmails   = bool("marketing_emails")
    val ticketNotifications = bool("ticket_notifications")
    val unsubscribeToken  = varchar("unsubscribe_token", 64)
    val unsubscribedAt    = long("unsubscribed_at").nullable()
    override val primaryKey = PrimaryKey(userId)
}

// ── Route ─────────────────────────────────────────────────────────────────────

fun Route.unsubscribeRoutes() {

    // GET /unsubscribe?token=<signed_token>
    // Public endpoint — validates token and records unsubscribe preference
    get("/unsubscribe") {
        val token = call.request.queryParameters["token"]
        if (token.isNullOrBlank()) {
            call.respondText(
                contentType = ContentType.Text.Html,
                status = HttpStatusCode.BadRequest,
                text = unsubscribeErrorHtml("Missing or invalid unsubscribe token.")
            )
            return@get
        }

        val updated = newSuspendedTransaction {
            val now = System.currentTimeMillis()
            val count = EmailPreferences.update(
                where = {
                    (EmailPreferences.unsubscribeToken eq token) and
                    EmailPreferences.unsubscribedAt.isNull()
                }
            ) {
                it[unsubscribedAt] = now
                it[marketingEmails] = false
                it[ticketNotifications] = false
            }
            count > 0
        }

        if (updated) {
            call.respondText(
                contentType = ContentType.Text.Html,
                status = HttpStatusCode.OK,
                text = unsubscribeSuccessHtml()
            )
        } else {
            // Either already unsubscribed or token is invalid — always show success to avoid enumeration
            call.respondText(
                contentType = ContentType.Text.Html,
                status = HttpStatusCode.OK,
                text = unsubscribeAlreadyHtml()
            )
        }
    }
}

// ── HTML responses ────────────────────────────────────────────────────────────

private fun unsubscribeSuccessHtml() = """
    <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:40px auto;text-align:center">
    <h2>You've been unsubscribed</h2>
    <p>You have been successfully unsubscribed from ZyntaPOS email notifications.</p>
    <p>You will no longer receive marketing emails or ticket notifications from us.</p>
    <p style="color:#666;font-size:12px;margin-top:40px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
    </body></html>
""".trimIndent()

private fun unsubscribeAlreadyHtml() = """
    <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:40px auto;text-align:center">
    <h2>Already unsubscribed</h2>
    <p>This email address is already unsubscribed from ZyntaPOS notifications.</p>
    <p style="color:#666;font-size:12px;margin-top:40px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
    </body></html>
""".trimIndent()

private fun unsubscribeErrorHtml(message: String) = """
    <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:40px auto;text-align:center">
    <h2>Invalid request</h2>
    <p>$message</p>
    <p style="color:#666;font-size:12px;margin-top:40px">ZyntaPOS &mdash; Enterprise Point of Sale</p>
    </body></html>
""".trimIndent()

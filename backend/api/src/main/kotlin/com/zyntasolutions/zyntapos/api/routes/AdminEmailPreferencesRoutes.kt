package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Admin email preference management routes (A2 completion).
 *
 * ## Routes
 * - `GET  /admin/email/preferences`  — get current admin user's email preferences
 * - `PUT  /admin/email/preferences`  — update current admin user's email preferences
 */
fun Route.adminEmailPreferencesRoutes() {
    val authService: AdminAuthService by inject()

    route("/admin/email/preferences") {

        get {
            val admin = resolveAdminUser(call, authService) ?: return@get

            val prefs = newSuspendedTransaction {
                EmailPreferences.selectAll()
                    .where { EmailPreferences.userId eq admin.id }
                    .firstOrNull()
                    ?.let { row ->
                        EmailPreferencesDto(
                            marketingEmails = row[EmailPreferences.marketingEmails],
                            ticketNotifications = row[EmailPreferences.ticketNotifications],
                            unsubscribed = row[EmailPreferences.unsubscribedAt] != null,
                        )
                    }
            } ?: EmailPreferencesDto(
                marketingEmails = true,
                ticketNotifications = true,
                unsubscribed = false,
            )

            call.respond(HttpStatusCode.OK, prefs)
        }

        put {
            val admin = resolveAdminUser(call, authService) ?: return@put

            val body = call.receive<UpdateEmailPreferencesRequest>()
            val userId = admin.id

            newSuspendedTransaction {
                val exists = EmailPreferences.selectAll()
                    .where { EmailPreferences.userId eq userId }
                    .count() > 0

                if (exists) {
                    EmailPreferences.update({ EmailPreferences.userId eq userId }) {
                        it[marketingEmails] = body.marketingEmails
                        it[ticketNotifications] = body.ticketNotifications
                        if (body.marketingEmails || body.ticketNotifications) {
                            it[unsubscribedAt] = null
                        }
                    }
                } else {
                    EmailPreferences.upsert {
                        it[EmailPreferences.userId] = userId
                        it[marketingEmails] = body.marketingEmails
                        it[ticketNotifications] = body.ticketNotifications
                        it[unsubscribeToken] = UUID.randomUUID().toString().replace("-", "").take(64)
                    }
                }
            }

            call.respond(HttpStatusCode.OK, mapOf("message" to "Preferences updated"))
        }
    }
}

@Serializable
data class EmailPreferencesDto(
    val marketingEmails: Boolean,
    val ticketNotifications: Boolean,
    val unsubscribed: Boolean,
)

@Serializable
private data class UpdateEmailPreferencesRequest(
    val marketingEmails: Boolean,
    val ticketNotifications: Boolean,
)

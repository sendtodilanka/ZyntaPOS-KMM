package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.db.AdminUsers
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger(GoogleOAuthService::class.java)

@Serializable
private data class GoogleTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int? = null,
    val id_token: String? = null
)

@Serializable
data class GoogleUserInfo(
    val sub: String,
    val email: String,
    val name: String? = null,
    val picture: String? = null
)

class GoogleOAuthService(private val config: AppConfig) {

    // Empty string means any domain is allowed
    private val allowedDomain = config.googleAllowedDomain.trim().lowercase()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /** Returns the Google OAuth2 authorization URL with a state nonce. */
    fun buildAuthUrl(state: String): String {
        if (config.googleClientId.isBlank()) error("Google OAuth is not configured")

        val params = mapOf(
            "client_id" to config.googleClientId,
            "redirect_uri" to config.googleRedirectUri,
            "response_type" to "code",
            "scope" to "openid email profile",
            "state" to state,
            "access_type" to "online",
            "prompt" to "select_account"
        )
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
               params.entries.joinToString("&") { "${it.key}=${it.value.encodeURLParameter()}" }
    }

    /** Exchanges an auth code for an access token, then fetches the user's Google profile. */
    suspend fun exchangeCodeForUser(code: String): GoogleUserInfo? {
        val tokenResponse = try {
            val resp: HttpResponse = httpClient.submitForm(
                url = "https://oauth2.googleapis.com/token",
                formParameters = Parameters.build {
                    append("client_id", config.googleClientId)
                    append("client_secret", config.googleClientSecret)
                    append("code", code)
                    append("redirect_uri", config.googleRedirectUri)
                    append("grant_type", "authorization_code")
                }
            )
            val body = resp.bodyAsText()
            Json { ignoreUnknownKeys = true }.decodeFromString<GoogleTokenResponse>(body)
        } catch (e: Exception) {
            logger.warn("Google token exchange failed: ${e.message}")
            return null
        }

        return try {
            val resp: HttpResponse = httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
                header(HttpHeaders.Authorization, "Bearer ${tokenResponse.access_token}")
            }
            val body = resp.bodyAsText()
            Json { ignoreUnknownKeys = true }.decodeFromString<GoogleUserInfo>(body)
        } catch (e: Exception) {
            logger.warn("Google userinfo fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Finds or creates an admin user from Google user info.
     * Only allows @zyntapos.com email addresses.
     * Returns null if the domain is not allowed.
     */
    suspend fun findOrCreateUser(userInfo: GoogleUserInfo): AdminUserRow? {
        val email = userInfo.email.lowercase().trim()
        if (allowedDomain.isNotEmpty() && !email.endsWith("@$allowedDomain")) {
            logger.warn("Google OAuth rejected: email domain not in allowlist: $email (allowed: $allowedDomain)")
            return null
        }

        return newSuspendedTransaction {
            val now = System.currentTimeMillis()

            // 1. Try to find by google_sub (linked account)
            val byGoogleSub = AdminUsers.selectAll()
                .where { AdminUsers.googleSub eq userInfo.sub }
                .singleOrNull()?.toAdminUserRow()
            if (byGoogleSub != null) {
                AdminUsers.update({ AdminUsers.id eq byGoogleSub.id }) {
                    it[AdminUsers.lastLoginAt] = now
                }
                return@newSuspendedTransaction byGoogleSub
            }

            // 2. Try to find by email (link accounts)
            val byEmail = AdminUsers.selectAll()
                .where { AdminUsers.email eq email }
                .singleOrNull()
            if (byEmail != null) {
                // Link google_sub to existing account
                AdminUsers.update({ AdminUsers.id eq byEmail[AdminUsers.id] }) {
                    it[AdminUsers.googleSub] = userInfo.sub
                    it[AdminUsers.lastLoginAt] = now
                }
                return@newSuspendedTransaction byEmail.toAdminUserRow()
            }

            // 3. Create new OPERATOR account for @zyntapos.com
            AdminUsers.insert {
                it[AdminUsers.email] = email
                it[AdminUsers.name] = userInfo.name ?: email.substringBefore("@")
                it[AdminUsers.role] = AdminRole.OPERATOR.name
                it[AdminUsers.googleSub] = userInfo.sub
                it[AdminUsers.passwordHash] = null
                it[AdminUsers.mfaEnabled] = false
                it[AdminUsers.failedAttempts] = 0
                it[AdminUsers.isActive] = true
                it[AdminUsers.createdAt] = now
                it[AdminUsers.lastLoginAt] = now
            }

            AdminUsers.selectAll()
                .where { AdminUsers.email eq email }
                .single().toAdminUserRow()
        }
    }

    private fun String.encodeURLParameter(): String =
        java.net.URLEncoder.encode(this, "UTF-8")

    private fun org.jetbrains.exposed.sql.ResultRow.toAdminUserRow() = AdminUserRow(
        id           = this[AdminUsers.id],
        email        = this[AdminUsers.email],
        name         = this[AdminUsers.name],
        role         = AdminRole.fromString(this[AdminUsers.role]) ?: AdminRole.AUDITOR,
        passwordHash = this[AdminUsers.passwordHash],
        mfaEnabled   = this[AdminUsers.mfaEnabled],
        isActive     = this[AdminUsers.isActive],
        lastLoginAt  = this[AdminUsers.lastLoginAt],
        createdAt    = this[AdminUsers.createdAt]
    )
}

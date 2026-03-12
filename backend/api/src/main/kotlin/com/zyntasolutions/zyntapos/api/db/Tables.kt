package com.zyntasolutions.zyntapos.api.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Centralized Exposed table definitions for the API service (S2-5).
 *
 * Mirrors the Flyway migrations V1–V12. All services reference these
 * objects for type-safe queries — no inline table objects in services.
 *
 * License service has its own centralized tables in `license/db/`.
 */

// ── V1: Core POS tables ─────────────────────────────────────────────────

object Stores : Table("stores") {
    val id         = text("id")
    val licenseKey = text("license_key")
    val isActive   = bool("is_active")
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id             = text("id")
    val storeId        = text("store_id")
    val username       = text("username")
    val email          = text("email").nullable()
    val name           = text("name").nullable()
    val passwordHash   = text("password_hash")
    val role           = text("role")
    val isActive       = bool("is_active")
    val failedAttempts = integer("failed_attempts")
    val lockedUntil    = timestampWithTimeZone("locked_until").nullable()
    val createdAt      = timestampWithTimeZone("created_at")
    val updatedAt      = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

// ── V2: Admin auth tables ────────────────────────────────────────────────

object AdminUsers : Table("admin_users") {
    val id             = uuid("id")
    val email          = text("email")
    val name           = text("name")
    val role           = text("role")
    val passwordHash   = text("password_hash").nullable()
    val googleSub      = text("google_sub").nullable()
    val mfaSecret      = text("mfa_secret").nullable()
    val mfaEnabled     = bool("mfa_enabled")
    val failedAttempts = integer("failed_attempts")
    val lockedUntil    = long("locked_until").nullable()   // epoch-ms
    val lastLoginAt    = long("last_login_at").nullable()  // epoch-ms
    val lastLoginIp    = text("last_login_ip").nullable()
    val isActive       = bool("is_active")
    val createdAt      = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AdminSessions : Table("admin_sessions") {
    val id        = uuid("id")
    val userId    = uuid("user_id")
    val tokenHash = text("token_hash")
    val userAgent = text("user_agent").nullable()
    val ipAddress = text("ip_address").nullable()
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")   // epoch-ms
    val revokedAt = long("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

// ── V7: Password reset tokens ────────────────────────────────────────────

object PasswordResetTokens : Table("password_reset_tokens") {
    val id          = uuid("id")
    val adminUserId = uuid("admin_user_id")
    val tokenHash   = varchar("token_hash", 64)
    val expiresAt   = long("expires_at")   // epoch-ms
    val usedAt      = long("used_at").nullable()  // epoch-ms
    val createdAt   = long("created_at")   // epoch-ms
    override val primaryKey = PrimaryKey(id)
}

// ── V10: Token revocation ────────────────────────────────────────────────

object RevokedTokens : Table("revoked_tokens") {
    val id        = uuid("id")
    val jti       = text("jti")
    val reason    = text("reason").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at")
    override val primaryKey = PrimaryKey(id)
}

// ── V11: POS sessions ────────────────────────────────────────────────────

object PosSessions : Table("pos_sessions") {
    val id        = uuid("id")
    val userId    = text("user_id")
    val storeId   = text("store_id")
    val tokenHash = text("token_hash")
    val deviceId  = text("device_id").nullable()
    val userAgent = text("user_agent").nullable()
    val ipAddress = text("ip_address").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

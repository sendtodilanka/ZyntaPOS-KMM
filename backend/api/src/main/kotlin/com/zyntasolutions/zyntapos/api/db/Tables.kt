package com.zyntasolutions.zyntapos.api.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Centralized Exposed table definitions for the API service (S2-5, S3-15).
 *
 * Mirrors the Flyway migrations V1–V16. All services reference these
 * objects for type-safe queries — no inline table objects in services.
 *
 * License service has its own centralized tables in `license/db/`.
 */

// ── V1: Core POS tables ─────────────────────────────────────────────────

object Stores : Table("stores") {
    val id         = text("id")
    val name       = text("name")
    val licenseKey = text("license_key")
    val timezone   = text("timezone")
    val currency   = text("currency")
    val isActive   = bool("is_active")
    val createdAt  = timestampWithTimeZone("created_at")
    val updatedAt  = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

// ── V4: Sync queue ────────────────────────────────────────────────────────

object SyncQueue : Table("sync_queue") {
    val id          = text("id")
    val storeId     = text("store_id")
    val deviceId    = text("device_id")
    val entityType  = text("entity_type")
    val entityId    = text("entity_id")
    val operation   = text("operation")
    val payload     = text("payload")        // JSONB stored as text
    val vectorClock = long("vector_clock")
    val clientTs    = timestampWithTimeZone("client_ts")
    val serverTs    = timestampWithTimeZone("server_ts")
    val isProcessed = bool("is_processed")
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

// ── V14: Admin audit log (S3-15) ─────────────────────────────────────────

object AdminAuditLog : Table("admin_audit_log") {
    val id             = uuid("id")
    val eventType      = text("event_type")
    val category       = text("category")
    val adminId        = uuid("admin_id").nullable()
    val adminName      = text("admin_name").nullable()
    val storeId        = text("store_id").nullable()
    val storeName      = text("store_name").nullable()
    val entityType     = text("entity_type").nullable()
    val entityId       = text("entity_id").nullable()
    val previousValues = text("previous_values").nullable()  // JSONB stored as text
    val newValues      = text("new_values").nullable()
    val ipAddress      = text("ip_address").nullable()
    val userAgent      = text("user_agent").nullable()
    val success        = bool("success")
    val errorMessage   = text("error_message").nullable()
    val hashChain      = text("hash_chain")
    val createdAt      = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ── V5: Helpdesk tickets (S3-15) ─────────────────────────────────────────

object SupportTickets : Table("support_tickets") {
    val id             = uuid("id")
    val ticketNumber   = text("ticket_number")
    val storeId        = text("store_id").nullable()
    val licenseId      = text("license_id").nullable()
    val createdBy      = uuid("created_by")
    val customerName   = text("customer_name")
    val customerEmail  = text("customer_email").nullable()
    val customerPhone  = text("customer_phone").nullable()
    val assignedTo     = uuid("assigned_to").nullable()
    val assignedAt     = long("assigned_at").nullable()
    val title          = text("title")
    val description    = text("description")
    val category       = text("category")
    val priority       = text("priority")
    val status         = text("status")
    val resolvedBy     = uuid("resolved_by").nullable()
    val resolvedAt     = long("resolved_at").nullable()
    val resolutionNote = text("resolution_note").nullable()
    val timeSpentMin   = integer("time_spent_min").nullable()
    val slaDueAt       = long("sla_due_at").nullable()
    val slaBreached    = bool("sla_breached")
    val createdAt      = long("created_at")
    val updatedAt      = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object TicketComments : Table("ticket_comments") {
    val id         = uuid("id")
    val ticketId   = uuid("ticket_id")
    val authorId   = uuid("author_id")
    val body       = text("body")
    val isInternal = bool("is_internal")
    val createdAt  = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

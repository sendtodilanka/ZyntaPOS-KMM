package com.zyntasolutions.zyntapos.license.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Exposed table definitions matching V1–V3 license schema migrations */

object Licenses : Table("licenses") {
    val key = text("key")
    val customerId = text("customer_id")
    val customerName = text("customer_name").nullable()
    val edition = text("edition")
    val maxDevices = integer("max_devices").default(1)
    val status = text("status").default("ACTIVE")
    val issuedAt = timestampWithTimeZone("issued_at")
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val lastHeartbeatAt = timestampWithTimeZone("last_heartbeat_at").nullable()
    val forceSyncRequested = bool("force_sync_requested").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(key)
}

object DeviceRegistrations : Table("device_registrations") {
    val id = text("id")
    val licenseKey = text("license_key").references(Licenses.key)
    val deviceId = text("device_id")
    val deviceName = text("device_name").nullable()
    val appVersion = text("app_version").nullable()
    val osVersion = text("os_version").nullable()
    val lastSeenAt = timestampWithTimeZone("last_seen_at")
    val registeredAt = timestampWithTimeZone("registered_at")

    override val primaryKey = PrimaryKey(id)
}

object AdminAuditLog : Table("admin_audit_log") {
    val id = text("id")
    val adminId = text("admin_id")
    val action = text("action")
    val licenseKey = text("license_key").nullable()
    val details = text("details").nullable()
    val performedAt = timestampWithTimeZone("performed_at")

    override val primaryKey = PrimaryKey(id)
}

package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Immutable record of a security-relevant event within ZyntaPOS.
 *
 * Audit entries are **append-only** — they are never updated or deleted after
 * persisting. Each entry captures the who, what, when, and where of the event
 * so that a complete chain of custody is always available.
 *
 * @property id         UUID v4 — unique entry identifier.
 * @property eventType  Discriminator that classifies the entry (e.g., LOGIN_SUCCESS).
 * @property userId     The actor who triggered the event. May be "SYSTEM" for automated events.
 * @property deviceId   Hardware or installation identifier for the device involved.
 * @property payload    Event-specific JSON payload (orderId, reason, permission denied, etc.).
 * @property success    Whether the event represented a successful operation.
 * @property createdAt  UTC timestamp of the event.
 */
data class AuditEntry(
    val id: String,
    val eventType: AuditEventType,
    val userId: String,
    val deviceId: String,
    val payload: String,
    val success: Boolean,
    val createdAt: Instant,
)

/**
 * Enumerates all auditable event categories tracked by [SecurityAuditLogger].
 */
enum class AuditEventType {
    LOGIN_ATTEMPT,
    LOGOUT,
    PERMISSION_DENIED,
    ORDER_VOID,
    STOCK_ADJUSTMENT,
    USER_CREATED,
    USER_DEACTIVATED,
    SETTINGS_CHANGED,
    REGISTER_OPENED,
    REGISTER_CLOSED,
    DATA_EXPORT,
}

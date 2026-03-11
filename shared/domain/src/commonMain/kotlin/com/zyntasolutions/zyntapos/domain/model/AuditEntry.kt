package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Immutable record of a business-critical or security-sensitive event within ZyntaPOS.
 *
 * Audit entries are **append-only** — they are never updated or deleted after
 * persisting. Each entry captures the who, what, when, where, and what-changed of the
 * event so that a complete chain of custody is always available.
 *
 * ## Hash chain
 * Each entry stores `hash = SHA-256(id + eventType + userId + entityId + details + previousHash)`
 * and links to `previousHash` of the immediately preceding entry (genesis entry uses "GENESIS").
 * Chain integrity is verified by [AuditIntegrityVerifier] on a scheduled basis.
 *
 * ## Before/After values
 * [previousValue] and [newValue] capture the serialised JSON representation of the
 * affected entity's state before and after the change — required by SLA compliance.
 * Example: "Tax rate changed from 12% to 15% by admin@store.com".
 *
 * @property id            UUID v4 — unique entry identifier.
 * @property eventType     Discriminator that classifies the entry.
 * @property userId        Actor who triggered the event. "SYSTEM" for automated events.
 * @property userName      Actor's display name (denormalized for log readability without joins).
 * @property userRole      Actor's [Role] at time of action. Null for system-generated events.
 * @property deviceId      Hardware or installation identifier for the device involved.
 * @property entityType    Affected resource type: "ORDER", "PRODUCT", "USER", etc.
 * @property entityId      Primary key of the affected entity.
 * @property payload       Event-specific JSON payload (amounts, reasons, identifiers, etc.).
 * @property previousValue Serialised before-state JSON (for change-tracking events).
 * @property newValue      Serialised after-state JSON (for change-tracking events).
 * @property success       Whether the event represented a successful operation.
 * @property ipAddress     Source IP address for remote sessions (null for local terminal).
 * @property hash          SHA-256 chain link for tamper detection.
 * @property previousHash  Hash of the immediately preceding audit entry.
 * @property createdAt     UTC timestamp of the event (millisecond precision).
 */
data class AuditEntry(
    val id: String,
    val eventType: AuditEventType,
    val userId: String,
    val userName: String,
    val userRole: Role?,
    val deviceId: String,
    val entityType: String?,
    val entityId: String?,
    val payload: String,
    val previousValue: String?,
    val newValue: String?,
    val success: Boolean,
    val ipAddress: String?,
    val hash: String,
    val previousHash: String,
    val createdAt: Instant,
)

/**
 * Enumerates all auditable event categories in ZyntaPOS (~40 types covering every
 * business-critical operation for SLA compliance).
 *
 * Group comments reflect the logical section within the audit log viewer filters.
 */
enum class AuditEventType {

    // ── Authentication (5) ────────────────────────────────────────────────────
    /** Login attempt — success or failure; payload includes source (password/pin/biometric). */
    LOGIN_ATTEMPT,
    /** Explicit user logout. */
    LOGOUT,
    /** Idle timeout triggered the PIN-lock screen. */
    SESSION_TIMEOUT,
    /** User's PIN was created or changed. */
    PIN_CHANGE,
    /** User's password was changed. */
    PASSWORD_CHANGE,

    // ── Authorization (2) ─────────────────────────────────────────────────────
    /** RBAC guard denied access; payload includes permission and screen context. */
    PERMISSION_DENIED,
    /** A user's role was modified by an admin. */
    ROLE_CHANGED,

    // ── POS Operations (8) ────────────────────────────────────────────────────
    /** New order was finalised at the POS. */
    ORDER_CREATED,
    /** Order was voided; payload includes reason. */
    ORDER_VOIDED,
    /** Refund was processed against a previous order. */
    ORDER_REFUNDED,
    /** Manual discount applied; payload includes who, amount, and type. */
    DISCOUNT_APPLIED,
    /** Payment received; payload includes method and amount. */
    PAYMENT_PROCESSED,
    /** Order placed on hold for later retrieval. */
    ORDER_HELD,
    /** A held order was resumed and returned to the active cart. */
    ORDER_RESUMED,
    /** Cashier manually overrode a line-item price at the POS. */
    PRICE_OVERRIDE,

    // ── Inventory (5) ─────────────────────────────────────────────────────────
    /** Manual stock adjustment; payload includes reason (mandatory). */
    STOCK_ADJUSTED,
    /** New product added to the catalog. */
    PRODUCT_CREATED,
    /** Product details modified; previousValue/newValue capture the diff. */
    PRODUCT_MODIFIED,
    /** Product soft-deleted from the catalog. */
    PRODUCT_DELETED,
    /** Stocktake submission recorded. */
    STOCKTAKE_COMPLETED,

    // ── Cash Register (4) ─────────────────────────────────────────────────────
    /** Cash session opened; payload includes opening float amount. */
    REGISTER_OPENED,
    /** Cash session closed; payload includes expected vs actual and variance. */
    REGISTER_CLOSED,
    /** Cash added to the register mid-session. */
    CASH_IN,
    /** Cash removed from the register mid-session. */
    CASH_OUT,

    // ── User Management (4) ───────────────────────────────────────────────────
    /** New user account created. */
    USER_CREATED,
    /** User account disabled. */
    USER_DEACTIVATED,
    /** Previously disabled user account re-enabled. */
    USER_REACTIVATED,
    /** Custom RBAC role permissions were changed. */
    CUSTOM_ROLE_MODIFIED,

    // ── Financial (3) ─────────────────────────────────────────────────────────
    /** Expense record approved through the approval workflow. */
    EXPENSE_APPROVED,
    /** Accounting journal entry posted to the ledger. */
    JOURNAL_POSTED,
    /** Tax rate or tax group configuration changed. */
    TAX_CONFIG_CHANGED,

    // ── System (8) ────────────────────────────────────────────────────────────
    /** Application configuration or settings changed. */
    SETTINGS_CHANGED,
    /** Database backup successfully created. */
    BACKUP_CREATED,
    /** Database restored from a backup — critical event. */
    BACKUP_RESTORED,
    /** Data exported to CSV or PDF; payload includes data type and requester. */
    DATA_EXPORTED,
    /** Technician diagnostic session created — awaiting store-side consent. */
    DIAGNOSTIC_SESSION,
    /** Store operator accepted a remote diagnostic session request. */
    DIAGNOSTIC_SESSION_CONSENT_GRANTED,
    /** Remote diagnostic session revoked by admin or store operator before expiry. */
    DIAGNOSTIC_SESSION_REVOKED,
    /** Remote diagnostic session expired due to 15-minute TTL with no consent. */
    DIAGNOSTIC_SESSION_EXPIRED,

    // ── Data (3) ──────────────────────────────────────────────────────────────
    /** Background sync cycle completed successfully. */
    SYNC_COMPLETED,
    /** Background sync cycle failed; payload includes error details. */
    SYNC_FAILED,
    /** Old data purged by the automated retention policy. */
    DATA_PURGED,
}

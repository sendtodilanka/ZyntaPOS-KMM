package com.zyntasolutions.zyntapos.security.audit

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ZyntaPOS — Security Audit Logger
 *
 * Append-only audit trail for security-sensitive POS events. All log functions
 * are **suspend fire-and-forget** — they catch all exceptions internally to prevent
 * audit logging from blocking a POS transaction.
 *
 * Entries are written via [AuditRepository] to the `audit_log` SQLDelight table and
 * synced to the server. Entries are retained locally for 90 days before automated purge.
 *
 * ### Events Covered
 * - Authentication attempts (login / PIN)
 * - Permission denials (RBAC guard)
 * - Order voiding
 * - Stock adjustments
 * - Register open / close
 * - Discount authorisations
 * - Receipt print / email
 *
 * @param auditRepository The [AuditRepository] that persists [AuditEntry] records.
 * @param deviceId        Hardware/installation ID injected at startup.
 */
@OptIn(ExperimentalUuidApi::class)
class SecurityAuditLogger(
    private val auditRepository: AuditRepository,
    private val deviceId: String,
) {

    // ─── Authentication ───────────────────────────────────────────────────────

    /**
     * Records a login attempt (successful or failed).
     *
     * @param success  `true` = authenticated; `false` = rejected.
     * @param userId   The user ID that attempted login (or attempted email).
     * @param deviceId Override device ID (for cases where caller has a better value).
     */
    suspend fun logLoginAttempt(success: Boolean, userId: String, deviceId: String = this.deviceId) {
        emit(
            eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = userId,
            deviceId = deviceId,
            success = success,
            payload = """{"source":"password"}""",
        )
    }

    /**
     * Records a PIN entry attempt.
     *
     * @param success `true` = correct PIN; `false` = rejected.
     * @param userId  The user whose PIN was challenged.
     */
    suspend fun logPinAttempt(success: Boolean, userId: String) {
        emit(
            eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = userId,
            deviceId = deviceId,
            success = success,
            payload = """{"source":"pin"}""",
        )
    }

    // ─── RBAC ─────────────────────────────────────────────────────────────────

    /**
     * Records a permission denial — used to detect privilege escalation attempts.
     *
     * @param userId     The authenticated user who was denied.
     * @param permission The [Permission] that was required.
     * @param screen     Human-readable screen / feature context (e.g., "CartPanel").
     */
    suspend fun logPermissionDenied(userId: String, permission: Permission, screen: String) {
        emit(
            eventType = AuditEventType.PERMISSION_DENIED,
            userId = userId,
            deviceId = deviceId,
            success = false,
            payload = """{"permission":"${permission.name}","screen":"$screen"}""",
        )
    }

    // ─── POS Operations ───────────────────────────────────────────────────────

    /**
     * Records an order void event — mandatory for cash register reconciliation.
     *
     * @param userId  Cashier / manager who authorised the void.
     * @param orderId The voided order reference.
     * @param reason  Free-text reason provided by the operator.
     */
    suspend fun logOrderVoid(userId: String, orderId: String, reason: String) {
        emit(
            eventType = AuditEventType.ORDER_VOID,
            userId = userId,
            deviceId = deviceId,
            success = true,
            payload = """{"orderId":"$orderId","reason":"${reason.escapeJson()}"}""",
        )
    }

    /**
     * Records a stock adjustment for inventory audit purposes.
     *
     * @param userId     The operator who performed the adjustment.
     * @param productId  Adjusted product.
     * @param qty        Quantity changed (positive = increase, negative = decrease).
     * @param reason     Adjustment reason code or free text.
     */
    suspend fun logStockAdjustment(userId: String, productId: String, qty: Double, reason: String) {
        emit(
            eventType = AuditEventType.STOCK_ADJUSTMENT,
            userId = userId,
            deviceId = deviceId,
            success = true,
            payload = """{"productId":"$productId","qty":$qty,"reason":"${reason.escapeJson()}"}""",
        )
    }

    /**
     * Records a receipt print event for regulatory / refund audit compliance.
     */
    suspend fun logReceiptPrint(orderId: String, userId: String) {
        emit(
            eventType = AuditEventType.DATA_EXPORT,
            userId = userId,
            deviceId = deviceId,
            success = true,
            payload = """{"action":"RECEIPT_PRINT","orderId":"$orderId"}""",
        )
    }

    /**
     * Records an order-level or item-level discount authorisation.
     */
    suspend fun logDiscountApplied(userId: String, orderId: String, amount: Double, isPercent: Boolean) {
        emit(
            eventType = AuditEventType.SETTINGS_CHANGED,
            userId = userId,
            deviceId = deviceId,
            success = true,
            payload = """{"action":"DISCOUNT","orderId":"$orderId","amount":$amount,"type":"${if (isPercent) "PERCENT" else "FIXED"}"}""",
        )
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    /**
     * Records a register session open event.
     */
    suspend fun logRegisterOpen(userId: String, registerId: String, openingBalance: Double) {
        emit(
            eventType = AuditEventType.REGISTER_OPENED,
            userId = userId,
            deviceId = deviceId,
            success = true,
            payload = """{"registerId":"$registerId","openingBalance":$openingBalance}""",
        )
    }

    /**
     * Records a register session close event.
     */
    suspend fun logRegisterClose(userId: String, registerId: String, variance: Double) {
        emit(
            eventType = AuditEventType.REGISTER_CLOSED,
            userId = userId,
            deviceId = deviceId,
            success = true,
            payload = """{"registerId":"$registerId","variance":$variance}""",
        )
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    /**
     * Builds and persists an [AuditEntry]. Exceptions are swallowed — audit logging
     * must never block or crash a POS transaction.
     */
    private suspend fun emit(
        eventType: AuditEventType,
        userId: String,
        deviceId: String,
        success: Boolean,
        payload: String,
    ) {
        runCatching {
            auditRepository.insert(
                AuditEntry(
                    id = Uuid.random().toString(),
                    eventType = eventType,
                    userId = userId,
                    deviceId = deviceId,
                    payload = payload,
                    success = success,
                    createdAt = Clock.System.now(),
                ),
            )
        }
    }

    /** Minimal JSON escaping for freeform text fields. */
    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}

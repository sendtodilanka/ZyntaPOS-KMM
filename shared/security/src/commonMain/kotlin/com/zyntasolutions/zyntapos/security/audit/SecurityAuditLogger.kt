package com.zyntasolutions.zyntapos.security.audit

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ZyntaPOS — Security Audit Logger
 *
 * Append-only audit trail for security-sensitive and business-critical POS events.
 * All log functions are **suspend fire-and-forget** — they catch all exceptions internally
 * to prevent audit logging from ever blocking or crashing a POS transaction.
 *
 * Entries are written via [AuditRepository] to the `audit_entries` SQLDelight table.
 * Entries are retained locally (legal minimum 7 years for Tier 1 business events);
 * automated purge is handled by [LogRetentionJob] (Phase 2).
 *
 * ### Events Covered
 * - Authentication: login, logout, PIN attempts, session timeout, PIN/password change
 * - RBAC: permission denials, role changes
 * - POS: order create/void/refund, discount, payment, hold/resume, price override
 * - Inventory: stock adjustments, product CRUD, stocktake
 * - Register: open/close, cash in/out
 * - User management: create, deactivate, reactivate
 * - System: settings change, backup, data export, diagnostic session
 *
 * @param auditRepository  The [AuditRepository] that persists [AuditEntry] records.
 * @param deviceId         Hardware/installation ID injected at startup.
 */
@OptIn(ExperimentalUuidApi::class)
class SecurityAuditLogger(
    private val auditRepository: AuditRepository,
    private val deviceId: String,
) {
    private val json = Json { encodeDefaults = false }

    // ─── Authentication ───────────────────────────────────────────────────────

    /**
     * Records a login attempt (successful or failed).
     *
     * @param success  `true` = authenticated; `false` = rejected.
     * @param userId   The user ID that attempted login (or attempted email).
     * @param userName Display name of the user (empty string if unknown at attempt time).
     * @param userRole The user's role (null if login failed and role is unknown).
     */
    suspend fun logLoginAttempt(
        success: Boolean,
        userId: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = success,
            payload = json.encodeToString(LoginPayload(source = "password")),
        )
    }

    /**
     * Records a PIN entry attempt.
     *
     * @param success  `true` = correct PIN; `false` = rejected.
     * @param userId   The user whose PIN was challenged.
     * @param userName Display name of the user.
     * @param userRole The user's role.
     */
    suspend fun logPinAttempt(
        success: Boolean,
        userId: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = success,
            payload = json.encodeToString(LoginPayload(source = "pin")),
        )
    }

    /**
     * Records an explicit logout event.
     */
    suspend fun logLogout(userId: String, userName: String = "", userRole: Role? = null) {
        emit(
            eventType = AuditEventType.LOGOUT,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = true,
            payload = json.encodeToString(LogoutPayload()),
        )
    }

    /**
     * Records an idle-timeout PIN lock event.
     */
    suspend fun logSessionTimeout(userId: String, userName: String = "", userRole: Role? = null) {
        emit(
            eventType = AuditEventType.SESSION_TIMEOUT,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = true,
            payload = json.encodeToString(LogoutPayload(reason = "idle_timeout")),
        )
    }

    // ─── RBAC ─────────────────────────────────────────────────────────────────

    /**
     * Records a permission denial — used to detect privilege escalation attempts.
     *
     * @param userId     The authenticated user who was denied.
     * @param userName   Display name of the denied user.
     * @param userRole   Role of the denied user.
     * @param permission The [Permission] that was required.
     * @param screen     Human-readable screen / feature context (e.g., "CartPanel").
     */
    suspend fun logPermissionDenied(
        userId: String,
        permission: Permission,
        screen: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.PERMISSION_DENIED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = false,
            payload = json.encodeToString(PermissionDeniedPayload(permission = permission.name, screen = screen)),
        )
    }

    // ─── POS Operations ───────────────────────────────────────────────────────

    /**
     * Records an order creation event.
     */
    suspend fun logOrderCreated(
        userId: String,
        orderId: String,
        totalAmount: Double,
        itemCount: Int,
        paymentMethod: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.ORDER_CREATED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "ORDER",
            entityId = orderId,
            success = true,
            payload = json.encodeToString(OrderCreatedPayload(orderId, totalAmount, itemCount, paymentMethod)),
        )
    }

    /**
     * Records an order void event — mandatory for cash register reconciliation.
     *
     * @param userId   Cashier / manager who authorised the void.
     * @param orderId  The voided order reference.
     * @param reason   Free-text reason provided by the operator.
     */
    suspend fun logOrderVoided(
        userId: String,
        orderId: String,
        reason: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.ORDER_VOIDED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "ORDER",
            entityId = orderId,
            success = true,
            payload = json.encodeToString(OrderVoidedPayload(orderId, reason)),
        )
    }

    /**
     * Records a payment processed event.
     */
    suspend fun logPaymentProcessed(
        userId: String,
        orderId: String,
        amount: Double,
        method: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.PAYMENT_PROCESSED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "ORDER",
            entityId = orderId,
            success = true,
            payload = json.encodeToString(PaymentProcessedPayload(orderId, amount, method)),
        )
    }

    /**
     * Records an order-level or item-level discount authorisation.
     */
    suspend fun logDiscountApplied(
        userId: String,
        orderId: String,
        amount: Double,
        isPercent: Boolean,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.DISCOUNT_APPLIED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "ORDER",
            entityId = orderId,
            success = true,
            payload = json.encodeToString(DiscountAppliedPayload(orderId, amount, if (isPercent) "PERCENT" else "FIXED")),
        )
    }

    // ─── Inventory ────────────────────────────────────────────────────────────

    /**
     * Records a stock adjustment for inventory audit purposes.
     *
     * @param userId     The operator who performed the adjustment.
     * @param productId  Adjusted product.
     * @param qty        Quantity changed (positive = increase, negative = decrease).
     * @param reason     Adjustment reason code or free text.
     * @param previousQty  Stock level before the adjustment (for previousValue field).
     * @param newQty       Stock level after the adjustment (for newValue field).
     */
    suspend fun logStockAdjusted(
        userId: String,
        productId: String,
        qty: Double,
        reason: String,
        previousQty: Double? = null,
        newQty: Double? = null,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.STOCK_ADJUSTED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "PRODUCT",
            entityId = productId,
            previousValue = previousQty?.let { json.encodeToString(StockQtySnapshot(it)) },
            newValue = newQty?.let { json.encodeToString(StockQtySnapshot(it)) },
            success = true,
            payload = json.encodeToString(StockAdjustedPayload(productId, qty, reason)),
        )
    }

    /**
     * Records a product created event.
     */
    suspend fun logProductCreated(
        userId: String,
        productId: String,
        productName: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.PRODUCT_CREATED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "PRODUCT",
            entityId = productId,
            success = true,
            payload = json.encodeToString(ProductCreatedPayload(productId, productName)),
        )
    }

    /**
     * Records a product modification event.
     *
     * @param previousValue JSON snapshot of the product state before the change.
     * @param newValue      JSON snapshot of the product state after the change.
     */
    suspend fun logProductModified(
        userId: String,
        productId: String,
        previousValue: String? = null,
        newValue: String? = null,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.PRODUCT_MODIFIED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "PRODUCT",
            entityId = productId,
            previousValue = previousValue,
            newValue = newValue,
            success = true,
            payload = json.encodeToString(ProductModifiedPayload(productId)),
        )
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    /**
     * Records a register session open event.
     */
    suspend fun logRegisterOpen(
        userId: String,
        registerId: String,
        openingBalance: Double,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.REGISTER_OPENED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "REGISTER",
            entityId = registerId,
            success = true,
            payload = json.encodeToString(RegisterOpenPayload(registerId, openingBalance)),
        )
    }

    /**
     * Records a register session close event.
     */
    suspend fun logRegisterClose(
        userId: String,
        registerId: String,
        variance: Double,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.REGISTER_CLOSED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "REGISTER",
            entityId = registerId,
            success = true,
            payload = json.encodeToString(RegisterClosePayload(registerId, variance)),
        )
    }

    /**
     * Records a cash-in event (cash added mid-session).
     */
    suspend fun logCashIn(
        userId: String,
        registerId: String,
        amount: Double,
        reason: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.CASH_IN,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "REGISTER",
            entityId = registerId,
            success = true,
            payload = json.encodeToString(CashMovementPayload(registerId, amount, reason)),
        )
    }

    /**
     * Records a cash-out event (cash removed mid-session).
     */
    suspend fun logCashOut(
        userId: String,
        registerId: String,
        amount: Double,
        reason: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.CASH_OUT,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "REGISTER",
            entityId = registerId,
            success = true,
            payload = json.encodeToString(CashMovementPayload(registerId, amount, reason)),
        )
    }

    // ─── Data / System ─────────────────────────────────────────────────────────

    /**
     * Records a receipt print or data export event for regulatory / refund audit compliance.
     */
    suspend fun logDataExported(
        userId: String,
        action: String,
        entityId: String? = null,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.DATA_EXPORTED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = if (entityId != null) "ORDER" else null,
            entityId = entityId,
            success = true,
            payload = json.encodeToString(DataExportedPayload(action, entityId ?: "")),
        )
    }

    /**
     * Records a settings change.
     *
     * @param key           The settings key that changed.
     * @param previousValue Serialised previous value.
     * @param newValue      Serialised new value.
     */
    suspend fun logSettingsChanged(
        userId: String,
        key: String,
        previousValue: String? = null,
        newValue: String? = null,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.SETTINGS_CHANGED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            previousValue = previousValue,
            newValue = newValue,
            success = true,
            payload = json.encodeToString(SettingsChangedPayload(key)),
        )
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    /**
     * Builds and persists an [AuditEntry]. Exceptions are swallowed — audit logging
     * must never block or crash a POS transaction.
     *
     * NOTE (Phase 1): `hash` and `previousHash` are stored as empty strings.
     * Proper SHA-256 chain computation will be added in Phase 2 when
     * [AuditIntegrityVerifier] is wired to the scheduled verification job.
     */
    private suspend fun emit(
        eventType: AuditEventType,
        userId: String,
        userName: String = "",
        userRole: Role? = null,
        entityType: String? = null,
        entityId: String? = null,
        previousValue: String? = null,
        newValue: String? = null,
        success: Boolean,
        payload: String,
        ipAddress: String? = null,
    ) {
        runCatching {
            auditRepository.insert(
                AuditEntry(
                    id = Uuid.random().toString(),
                    eventType = eventType,
                    userId = userId,
                    userName = userName,
                    userRole = userRole,
                    deviceId = deviceId,
                    entityType = entityType,
                    entityId = entityId,
                    payload = payload,
                    previousValue = previousValue,
                    newValue = newValue,
                    success = success,
                    ipAddress = ipAddress,
                    hash = "",         // Phase 2: compute SHA-256 chain
                    previousHash = "", // Phase 2: link to previous entry hash
                    createdAt = Clock.System.now(),
                ),
            )
        }
    }

}

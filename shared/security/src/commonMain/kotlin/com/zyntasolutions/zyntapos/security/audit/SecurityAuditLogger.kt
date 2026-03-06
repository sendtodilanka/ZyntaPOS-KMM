package com.zyntasolutions.zyntapos.security.audit

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.security.auth.sha256
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
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

    // ─── Brute-force detection ────────────────────────────────────────────────

    /**
     * Returns `true` if [userId] has exceeded [threshold] failed login attempts
     * within the last [windowMinutes] minutes.
     *
     * Designed for use immediately after a failed [logLoginAttempt] call:
     * ```kotlin
     * auditLogger.logLoginAttempt(false, userId)
     * if (auditLogger.isLoginBruteForced(userId)) {
     *     // show lockout message
     * }
     * ```
     *
     * Exceptions (e.g., DB unavailable) are swallowed — returns `false` on failure
     * so that a logging error never inadvertently locks a legitimate user out.
     *
     * @param userId        User ID to check (usually the email / username).
     * @param windowMinutes Time window to inspect (default: 5 minutes).
     * @param threshold     Number of failures that triggers brute-force flag (default: 5).
     */
    suspend fun isLoginBruteForced(
        userId: String,
        windowMinutes: Int = 5,
        threshold: Int = 5,
    ): Boolean {
        val sinceEpochMillis = (Clock.System.now() - windowMinutes.minutes).toEpochMilliseconds()
        return runCatching {
            auditRepository.getRecentLoginFailureCount(userId, sinceEpochMillis) >= threshold
        }.getOrDefault(false)
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

    // ─── POS (continued) ─────────────────────────────────────────────────────

    /** Records an order refund. */
    suspend fun logOrderRefunded(
        userId: String,
        orderId: String,
        amount: Double,
        reason: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.ORDER_REFUNDED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "ORDER",
            entityId = orderId,
            success = true,
            payload = json.encodeToString(OrderRefundedPayload(orderId, amount, reason)),
        )
    }

    /** Records an order being placed on hold. */
    suspend fun logOrderHeld(
        userId: String,
        orderId: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.ORDER_HELD,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "ORDER",
            entityId = orderId,
            success = true,
            payload = json.encodeToString(OrderHeldPayload(orderId)),
        )
    }

    /** Records a held order being resumed. */
    suspend fun logOrderResumed(
        userId: String,
        orderId: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.ORDER_RESUMED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "ORDER",
            entityId = orderId,
            success = true,
            payload = json.encodeToString(OrderResumedPayload(orderId)),
        )
    }

    /** Records a price override on a cart item. */
    suspend fun logPriceOverride(
        userId: String,
        productId: String,
        previousPrice: Double,
        newPrice: Double,
        orderId: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.PRICE_OVERRIDE,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "PRODUCT",
            entityId = productId,
            previousValue = json.encodeToString(PriceSnapshot(previousPrice)),
            newValue = json.encodeToString(PriceSnapshot(newPrice)),
            success = true,
            payload = json.encodeToString(PriceOverridePayload(productId, previousPrice, newPrice, orderId)),
        )
    }

    // ─── Inventory (continued) ────────────────────────────────────────────────

    /** Records a product deletion (deactivation). */
    suspend fun logProductDeleted(
        userId: String,
        productId: String,
        productName: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.PRODUCT_DELETED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "PRODUCT",
            entityId = productId,
            success = true,
            payload = json.encodeToString(ProductDeletedPayload(productId, productName)),
        )
    }

    /** Records completion of a stocktake / physical inventory count. */
    suspend fun logStocktakeCompleted(
        userId: String,
        productsChecked: Int,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.STOCKTAKE_COMPLETED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = true,
            payload = json.encodeToString(StocktakeCompletedPayload(productsChecked)),
        )
    }

    // ─── User Management ─────────────────────────────────────────────────────

    /** Records a new user account created by an admin. */
    suspend fun logUserCreated(
        userId: String,
        targetUserId: String,
        targetUserName: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.USER_CREATED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "USER",
            entityId = targetUserId,
            success = true,
            payload = json.encodeToString(UserMgmtPayload(targetUserId, targetUserName, "created")),
        )
    }

    /** Records a user account deactivation. */
    suspend fun logUserDeactivated(
        userId: String,
        targetUserId: String,
        targetUserName: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.USER_DEACTIVATED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "USER",
            entityId = targetUserId,
            success = true,
            payload = json.encodeToString(UserMgmtPayload(targetUserId, targetUserName, "deactivated")),
        )
    }

    /** Records a user account reactivation. */
    suspend fun logUserReactivated(
        userId: String,
        targetUserId: String,
        targetUserName: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.USER_REACTIVATED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "USER",
            entityId = targetUserId,
            success = true,
            payload = json.encodeToString(UserMgmtPayload(targetUserId, targetUserName, "reactivated")),
        )
    }

    /** Records modification to a custom RBAC role. */
    suspend fun logCustomRoleModified(
        userId: String,
        roleName: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.CUSTOM_ROLE_MODIFIED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = true,
            payload = json.encodeToString(RoleModifiedPayload(roleName)),
        )
    }

    /** Records a role assignment change on a user account. */
    suspend fun logRoleChanged(
        userId: String,
        targetUserId: String,
        oldRole: String,
        newRole: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.ROLE_CHANGED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "USER",
            entityId = targetUserId,
            success = true,
            payload = json.encodeToString(RoleChangedPayload(targetUserId, oldRole, newRole)),
        )
    }

    // ─── Financial ────────────────────────────────────────────────────────────

    /** Records a tax configuration change. */
    suspend fun logTaxConfigChanged(
        userId: String,
        taxGroupName: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.TAX_CONFIG_CHANGED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = true,
            payload = json.encodeToString(TaxConfigChangedPayload(taxGroupName)),
        )
    }

    /** Records an expense approval. */
    suspend fun logExpenseApproved(
        userId: String,
        expenseId: String,
        amount: Double,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.EXPENSE_APPROVED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "EXPENSE",
            entityId = expenseId,
            success = true,
            payload = json.encodeToString(ExpenseApprovedPayload(expenseId, amount)),
        )
    }

    /** Records a journal entry posting. */
    suspend fun logJournalPosted(
        userId: String,
        journalId: String,
        amount: Double,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.JOURNAL_POSTED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "JOURNAL",
            entityId = journalId,
            success = true,
            payload = json.encodeToString(JournalPostedPayload(journalId, amount)),
        )
    }

    // ─── System ───────────────────────────────────────────────────────────────

    /** Records a user PIN change. */
    suspend fun logPinChange(
        userId: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.PIN_CHANGE,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = true,
            payload = json.encodeToString(PinChangePayload(userId)),
        )
    }

    /** Records a backup file created. */
    suspend fun logBackupCreated(
        userId: String,
        backupId: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.BACKUP_CREATED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "BACKUP",
            entityId = backupId,
            success = true,
            payload = json.encodeToString(BackupPayload(backupId, "created")),
        )
    }

    /** Records a database restore from backup. */
    suspend fun logBackupRestored(
        userId: String,
        backupId: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.BACKUP_RESTORED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "BACKUP",
            entityId = backupId,
            success = true,
            payload = json.encodeToString(BackupPayload(backupId, "restored")),
        )
    }

    /** Records a data purge operation. */
    suspend fun logDataPurged(
        userId: String,
        recordsAffected: Long,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.DATA_PURGED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = true,
            payload = json.encodeToString(DataPurgedPayload(recordsAffected)),
        )
    }

    // ─── Data / Sync ──────────────────────────────────────────────────────────

    /** Records a successful sync cycle. */
    suspend fun logSyncCompleted(
        userId: String,
        syncedRecords: Int,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.SYNC_COMPLETED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = true,
            payload = json.encodeToString(SyncPayload(syncedRecords = syncedRecords)),
        )
    }

    /** Records a failed sync cycle. */
    suspend fun logSyncFailed(
        userId: String,
        error: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.SYNC_FAILED,
            userId = userId,
            userName = userName,
            userRole = userRole,
            success = false,
            payload = json.encodeToString(SyncPayload(error = error)),
        )
    }

    /** Records a remote diagnostic session event. */
    suspend fun logDiagnosticSession(
        userId: String,
        sessionId: String,
        action: String,
        userName: String = "",
        userRole: Role? = null,
    ) {
        emit(
            eventType = AuditEventType.DIAGNOSTIC_SESSION,
            userId = userId,
            userName = userName,
            userRole = userRole,
            entityType = "SESSION",
            entityId = sessionId,
            success = true,
            payload = json.encodeToString(DiagnosticSessionPayload(action, sessionId)),
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
            val id = Uuid.random().toString()
            val createdAt = Clock.System.now()
            val prevHash = auditRepository.getLatestHash() ?: "GENESIS"

            val hashInput = buildString {
                append(id)
                append(eventType.name)
                append(userId)
                append(createdAt.toString())
                append(entityType.orEmpty())
                append(entityId.orEmpty())
                append(payload)
                append(success)
                append(prevHash)
            }
            val hash = sha256(hashInput.encodeToByteArray())
                .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

            auditRepository.insert(
                AuditEntry(
                    id = id,
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
                    hash = hash,
                    previousHash = prevHash,
                    createdAt = createdAt,
                ),
            )
        }
    }

    companion object {
        /**
         * Recomputes the expected SHA-256 hash for a given [AuditEntry].
         * Used by [VerifyAuditIntegrityUseCase] to verify chain integrity.
         */
        fun computeExpectedHash(entry: AuditEntry, previousHash: String): String {
            val hashInput = buildString {
                append(entry.id)
                append(entry.eventType.name)
                append(entry.userId)
                append(entry.createdAt.toString())
                append(entry.entityType.orEmpty())
                append(entry.entityId.orEmpty())
                append(entry.payload)
                append(entry.success)
                append(previousHash)
            }
            return sha256(hashInput.encodeToByteArray())
                .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        }
    }

}

package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — SecurityAuditLoggerRemainingTest Unit Tests (commonTest)
 *
 * Extends [SecurityAuditLoggerTest] coverage to the remaining 31 log methods
 * that were not covered by the original test file.
 *
 * Coverage:
 *  N.  logLogout inserts LOGOUT with success=true
 *  O.  logSessionTimeout inserts SESSION_TIMEOUT
 *  P.  logPaymentProcessed inserts PAYMENT_PROCESSED with orderId and amount
 *  Q.  logDiscountApplied inserts DISCOUNT_APPLIED with orderId
 *  R.  logStockAdjusted inserts STOCK_ADJUSTED with productId and previousValue/newValue
 *  S.  logProductCreated inserts PRODUCT_CREATED with productId
 *  T.  logProductModified inserts PRODUCT_MODIFIED with previousValue and newValue
 *  U.  logRegisterClose inserts REGISTER_CLOSED with registerId
 *  V.  logCashIn inserts CASH_IN with registerId
 *  W.  logCashOut inserts CASH_OUT with registerId
 *  X.  logOrderRefunded inserts ORDER_REFUNDED with orderId and amount
 *  Y.  logOrderHeld inserts ORDER_HELD with orderId
 *  Z.  logOrderResumed inserts ORDER_RESUMED with orderId
 *  AA. logPriceOverride inserts PRICE_OVERRIDE with productId and previousValue/newValue
 *  AB. logProductDeleted inserts PRODUCT_DELETED with productId
 *  AC. logStocktakeCompleted inserts STOCKTAKE_COMPLETED
 *  AD. logUserCreated inserts USER_CREATED with targetUserId
 *  AE. logUserDeactivated inserts USER_DEACTIVATED with targetUserId
 *  AF. logUserReactivated inserts USER_REACTIVATED with targetUserId
 *  AG. logCustomRoleModified inserts CUSTOM_ROLE_MODIFIED
 *  AH. logRoleChanged inserts ROLE_CHANGED with targetUserId and old/new role in payload
 *  AI. logTaxConfigChanged inserts TAX_CONFIG_CHANGED
 *  AJ. logExpenseApproved inserts EXPENSE_APPROVED with expenseId
 *  AK. logJournalPosted inserts JOURNAL_POSTED with journalId
 *  AL. logPinChange inserts PIN_CHANGE
 *  AM. logBackupCreated inserts BACKUP_CREATED with backupId
 *  AN. logBackupRestored inserts BACKUP_RESTORED with backupId
 *  AO. logDataPurged inserts DATA_PURGED
 *  AP. logSyncCompleted inserts SYNC_COMPLETED with success=true
 *  AQ. logSyncFailed inserts SYNC_FAILED with success=false
 *  AR. logDiagnosticSession inserts DIAGNOSTIC_SESSION with sessionId
 *  AS. isLoginBruteForced returns false when failure count is below threshold
 *  AT. isLoginBruteForced returns true when failure count meets threshold
 */
class SecurityAuditLoggerRemainingTest {

    // ── Fake Repository ───────────────────────────────────────────────────────

    private class FakeAuditRepository(
        private val failureCount: Long = 0L,
    ) : AuditRepository {

        val inserted = mutableListOf<AuditEntry>()

        override suspend fun insert(entry: AuditEntry) {
            inserted.add(entry)
        }

        override fun observeAll(): Flow<List<AuditEntry>> = flowOf(emptyList())
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = flowOf(emptyList())
        override suspend fun getAllChronological(): List<AuditEntry> = inserted.toList()
        override suspend fun getLatestHash(): String? = null
        override suspend fun countEntries(): Long = inserted.size.toLong()
        override suspend fun getRecentLoginFailureCount(
            userId: String,
            sinceEpochMillis: Long,
        ): Long = failureCount
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun makeLogger(
        failureCount: Long = 0L,
    ): Pair<SecurityAuditLogger, FakeAuditRepository> {
        val fake = FakeAuditRepository(failureCount = failureCount)
        return SecurityAuditLogger(fake, deviceId = "test-device") to fake
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    fun `N - logLogout inserts LOGOUT with success true`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logLogout(userId = "u-1", userName = "Alice", userRole = Role.CASHIER)

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.LOGOUT, entry.eventType)
        assertEquals("u-1", entry.userId)
        assertTrue(entry.success)
    }

    @Test
    fun `O - logSessionTimeout inserts SESSION_TIMEOUT`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logSessionTimeout(userId = "u-2", userRole = Role.STORE_MANAGER)

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.SESSION_TIMEOUT, entry.eventType)
        assertEquals("u-2", entry.userId)
        assertTrue(entry.success)
    }

    // ── POS Operations ────────────────────────────────────────────────────────

    @Test
    fun `P - logPaymentProcessed inserts PAYMENT_PROCESSED with orderId and amount in payload`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logPaymentProcessed(
            userId = "cashier-1",
            orderId = "ord-42",
            amount = 150.75,
            method = "CARD",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.PAYMENT_PROCESSED, entry.eventType)
        assertEquals("ord-42", entry.entityId)
        assertEquals("ORDER", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("150"))
        assertTrue(entry.payload.contains("CARD"))
    }

    @Test
    fun `Q - logDiscountApplied inserts DISCOUNT_APPLIED with orderId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logDiscountApplied(
            userId = "cashier-1",
            orderId = "ord-10",
            amount = 20.0,
            isPercent = true,
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.DISCOUNT_APPLIED, entry.eventType)
        assertEquals("ord-10", entry.entityId)
        assertEquals("ORDER", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("PERCENT"))
    }

    @Test
    fun `Q2 - logDiscountApplied with fixed amount includes FIXED in payload`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logDiscountApplied(
            userId = "cashier-1",
            orderId = "ord-11",
            amount = 5.0,
            isPercent = false,
        )

        assertTrue(fake.inserted.first().payload.contains("FIXED"))
    }

    @Test
    fun `X - logOrderRefunded inserts ORDER_REFUNDED with orderId and amount in payload`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logOrderRefunded(
            userId = "mgr-1",
            orderId = "ord-77",
            amount = 85.0,
            reason = "Wrong item delivered",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.ORDER_REFUNDED, entry.eventType)
        assertEquals("ord-77", entry.entityId)
        assertEquals("ORDER", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("Wrong item delivered"))
    }

    @Test
    fun `Y - logOrderHeld inserts ORDER_HELD with orderId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logOrderHeld(userId = "cashier-2", orderId = "ord-hold-1")

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.ORDER_HELD, entry.eventType)
        assertEquals("ord-hold-1", entry.entityId)
        assertEquals("ORDER", entry.entityType)
        assertTrue(entry.success)
    }

    @Test
    fun `Z - logOrderResumed inserts ORDER_RESUMED with orderId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logOrderResumed(userId = "cashier-2", orderId = "ord-hold-1")

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.ORDER_RESUMED, entry.eventType)
        assertEquals("ord-hold-1", entry.entityId)
        assertEquals("ORDER", entry.entityType)
        assertTrue(entry.success)
    }

    @Test
    fun `AA - logPriceOverride inserts PRICE_OVERRIDE with productId and previousValue and newValue`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logPriceOverride(
            userId = "mgr-1",
            productId = "prod-5",
            previousPrice = 10.0,
            newPrice = 8.0,
            orderId = "ord-15",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.PRICE_OVERRIDE, entry.eventType)
        assertEquals("prod-5", entry.entityId)
        assertEquals("PRODUCT", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.previousValue != null)
        assertTrue(entry.newValue != null)
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    @Test
    fun `R - logStockAdjusted inserts STOCK_ADJUSTED with productId and prev and new qty`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logStockAdjusted(
            userId = "admin-1",
            productId = "prod-99",
            qty = -5.0,
            reason = "Damaged",
            previousQty = 20.0,
            newQty = 15.0,
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.STOCK_ADJUSTED, entry.eventType)
        assertEquals("prod-99", entry.entityId)
        assertEquals("PRODUCT", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.previousValue != null)
        assertTrue(entry.newValue != null)
        assertTrue(entry.payload.contains("Damaged"))
    }

    @Test
    fun `S - logProductCreated inserts PRODUCT_CREATED with productId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logProductCreated(
            userId = "admin-1",
            productId = "prod-new-1",
            productName = "Espresso Shot",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.PRODUCT_CREATED, entry.eventType)
        assertEquals("prod-new-1", entry.entityId)
        assertEquals("PRODUCT", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("Espresso Shot"))
    }

    @Test
    fun `T - logProductModified inserts PRODUCT_MODIFIED with previousValue and newValue`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logProductModified(
            userId = "admin-1",
            productId = "prod-5",
            previousValue = """{"name":"Old Name"}""",
            newValue = """{"name":"New Name"}""",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.PRODUCT_MODIFIED, entry.eventType)
        assertEquals("prod-5", entry.entityId)
        assertEquals("PRODUCT", entry.entityType)
        assertTrue(entry.success)
        assertEquals("""{"name":"Old Name"}""", entry.previousValue)
        assertEquals("""{"name":"New Name"}""", entry.newValue)
    }

    @Test
    fun `AB - logProductDeleted inserts PRODUCT_DELETED with productId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logProductDeleted(
            userId = "admin-1",
            productId = "prod-del-1",
            productName = "Discontinued Item",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.PRODUCT_DELETED, entry.eventType)
        assertEquals("prod-del-1", entry.entityId)
        assertEquals("PRODUCT", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("Discontinued Item"))
    }

    @Test
    fun `AC - logStocktakeCompleted inserts STOCKTAKE_COMPLETED`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logStocktakeCompleted(userId = "admin-1", productsChecked = 200)

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.STOCKTAKE_COMPLETED, entry.eventType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("200"))
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Test
    fun `U - logRegisterClose inserts REGISTER_CLOSED with registerId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logRegisterClose(
            userId = "cashier-1",
            registerId = "reg-01",
            variance = -2.5,
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.REGISTER_CLOSED, entry.eventType)
        assertEquals("reg-01", entry.entityId)
        assertEquals("REGISTER", entry.entityType)
        assertTrue(entry.success)
    }

    @Test
    fun `V - logCashIn inserts CASH_IN with registerId and amount`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logCashIn(
            userId = "mgr-1",
            registerId = "reg-01",
            amount = 200.0,
            reason = "Float top-up",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.CASH_IN, entry.eventType)
        assertEquals("reg-01", entry.entityId)
        assertEquals("REGISTER", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("Float top-up"))
    }

    @Test
    fun `W - logCashOut inserts CASH_OUT with registerId and reason`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logCashOut(
            userId = "mgr-1",
            registerId = "reg-01",
            amount = 50.0,
            reason = "Change for customer",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.CASH_OUT, entry.eventType)
        assertEquals("reg-01", entry.entityId)
        assertEquals("REGISTER", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("Change for customer"))
    }

    // ── User Management ───────────────────────────────────────────────────────

    @Test
    fun `AD - logUserCreated inserts USER_CREATED with targetUserId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logUserCreated(
            userId = "admin-1",
            targetUserId = "new-user-5",
            targetUserName = "Dave",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.USER_CREATED, entry.eventType)
        assertEquals("new-user-5", entry.entityId)
        assertEquals("USER", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("Dave"))
    }

    @Test
    fun `AE - logUserDeactivated inserts USER_DEACTIVATED with targetUserId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logUserDeactivated(
            userId = "admin-1",
            targetUserId = "ex-user-3",
            targetUserName = "Eve",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.USER_DEACTIVATED, entry.eventType)
        assertEquals("ex-user-3", entry.entityId)
        assertEquals("USER", entry.entityType)
        assertTrue(entry.success)
    }

    @Test
    fun `AF - logUserReactivated inserts USER_REACTIVATED with targetUserId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logUserReactivated(
            userId = "admin-1",
            targetUserId = "re-user-7",
            targetUserName = "Frank",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.USER_REACTIVATED, entry.eventType)
        assertEquals("re-user-7", entry.entityId)
        assertEquals("USER", entry.entityType)
        assertTrue(entry.success)
    }

    @Test
    fun `AG - logCustomRoleModified inserts CUSTOM_ROLE_MODIFIED`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logCustomRoleModified(userId = "admin-1", roleName = "SENIOR_CASHIER")

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.CUSTOM_ROLE_MODIFIED, entry.eventType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("SENIOR_CASHIER"))
    }

    @Test
    fun `AH - logRoleChanged inserts ROLE_CHANGED with targetUserId and old and new role`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logRoleChanged(
            userId = "admin-1",
            targetUserId = "u-99",
            oldRole = "CASHIER",
            newRole = "STORE_MANAGER",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.ROLE_CHANGED, entry.eventType)
        assertEquals("u-99", entry.entityId)
        assertEquals("USER", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("CASHIER"))
        assertTrue(entry.payload.contains("STORE_MANAGER"))
    }

    // ── Financial ─────────────────────────────────────────────────────────────

    @Test
    fun `AI - logTaxConfigChanged inserts TAX_CONFIG_CHANGED`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logTaxConfigChanged(userId = "admin-1", taxGroupName = "GST 9%")

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.TAX_CONFIG_CHANGED, entry.eventType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("GST 9%"))
    }

    @Test
    fun `AJ - logExpenseApproved inserts EXPENSE_APPROVED with expenseId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logExpenseApproved(
            userId = "admin-1",
            expenseId = "exp-55",
            amount = 350.0,
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.EXPENSE_APPROVED, entry.eventType)
        assertEquals("exp-55", entry.entityId)
        assertEquals("EXPENSE", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("350"))
    }

    @Test
    fun `AK - logJournalPosted inserts JOURNAL_POSTED with journalId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logJournalPosted(
            userId = "admin-1",
            journalId = "jnl-12",
            amount = 5000.0,
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.JOURNAL_POSTED, entry.eventType)
        assertEquals("jnl-12", entry.entityId)
        assertEquals("JOURNAL", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("5000"))
    }

    // ── System ────────────────────────────────────────────────────────────────

    @Test
    fun `AL - logPinChange inserts PIN_CHANGE`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logPinChange(userId = "u-1", userName = "Alice", userRole = Role.CASHIER)

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.PIN_CHANGE, entry.eventType)
        assertEquals("u-1", entry.userId)
        assertTrue(entry.success)
    }

    @Test
    fun `AM - logBackupCreated inserts BACKUP_CREATED with backupId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logBackupCreated(userId = "admin-1", backupId = "bkp-2025-01-01")

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.BACKUP_CREATED, entry.eventType)
        assertEquals("bkp-2025-01-01", entry.entityId)
        assertEquals("BACKUP", entry.entityType)
        assertTrue(entry.success)
    }

    @Test
    fun `AN - logBackupRestored inserts BACKUP_RESTORED with backupId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logBackupRestored(userId = "admin-1", backupId = "bkp-2025-01-01")

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.BACKUP_RESTORED, entry.eventType)
        assertEquals("bkp-2025-01-01", entry.entityId)
        assertEquals("BACKUP", entry.entityType)
        assertTrue(entry.success)
    }

    @Test
    fun `AO - logDataPurged inserts DATA_PURGED with records count in payload`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logDataPurged(userId = "admin-1", recordsAffected = 12_500L)

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.DATA_PURGED, entry.eventType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("12500"))
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Test
    fun `AP - logSyncCompleted inserts SYNC_COMPLETED with success true`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logSyncCompleted(userId = "sys", syncedRecords = 48)

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.SYNC_COMPLETED, entry.eventType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("48"))
    }

    @Test
    fun `AQ - logSyncFailed inserts SYNC_FAILED with success false and error in payload`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logSyncFailed(userId = "sys", error = "Connection timed out")

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.SYNC_FAILED, entry.eventType)
        assertFalse(entry.success)
        assertTrue(entry.payload.contains("Connection timed out"))
    }

    @Test
    fun `AR - logDiagnosticSession inserts DIAGNOSTIC_SESSION with sessionId and action`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logDiagnosticSession(
            userId = "tech-1",
            sessionId = "diag-sess-99",
            action = "STARTED",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.DIAGNOSTIC_SESSION, entry.eventType)
        assertEquals("diag-sess-99", entry.entityId)
        assertEquals("SESSION", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("STARTED"))
    }

    // ── Brute-force detection ─────────────────────────────────────────────────

    @Test
    fun `AS - isLoginBruteForced returns false when failure count is below threshold`() = runTest {
        val (logger, _) = makeLogger(failureCount = 4L)

        val result = logger.isLoginBruteForced(userId = "u-1", threshold = 5)

        assertFalse(result)
    }

    @Test
    fun `AT - isLoginBruteForced returns true when failure count meets threshold`() = runTest {
        val (logger, _) = makeLogger(failureCount = 5L)

        val result = logger.isLoginBruteForced(userId = "u-1", threshold = 5)

        assertTrue(result)
    }
}

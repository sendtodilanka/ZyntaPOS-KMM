package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Permission
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
 * ZyntaPOS — SecurityAuditLogger Unit Tests (commonTest)
 *
 * Validates [SecurityAuditLogger] using a hand-rolled [FakeAuditRepository].
 * All tests run in commonTest so they execute on both JVM and any other targets.
 *
 * Coverage:
 *  A. logLoginAttempt(success=true) inserts LOGIN_ATTEMPT with success=true
 *  B. logLoginAttempt(success=false) inserts entry with success=false
 *  C. logPinAttempt maps to LOGIN_ATTEMPT event type
 *  D. logPermissionDenied inserts PERMISSION_DENIED with success=false and payload
 *  E. logOrderCreated inserts ORDER_CREATED with entityId and totalAmount in payload
 *  F. logOrderVoided inserts ORDER_VOIDED with entityId
 *  G. logRegisterOpen inserts REGISTER_OPENED with registerId
 *  H. hash chain carries previousHash from getLatestHash()
 *  I. when getLatestHash() returns null, previousHash is GENESIS
 *  J. exceptions from auditRepository.insert() do not propagate (fire-and-forget)
 *  K. computeExpectedHash is deterministic and produces 64-char hex string
 *  L. logSettingsChanged inserts SETTINGS_CHANGED with key in payload and prev/new values
 *  M. logDataExported inserts DATA_EXPORTED with success=true
 */
class SecurityAuditLoggerTest {

    // ── Fake Repository ───────────────────────────────────────────────────────

    private class FakeAuditRepository(
        private val latestHash: String? = null,
        private val throwOnInsert: Boolean = false,
    ) : AuditRepository {

        val inserted = mutableListOf<AuditEntry>()

        override suspend fun insert(entry: AuditEntry) {
            if (throwOnInsert) throw RuntimeException("DB error — simulating failure")
            inserted.add(entry)
        }

        override fun observeAll(): Flow<List<AuditEntry>> = flowOf(emptyList())
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = flowOf(emptyList())
        override suspend fun getAllChronological(): List<AuditEntry> = inserted.toList()
        override suspend fun getLatestHash(): String? = latestHash
        override suspend fun countEntries(): Long = inserted.size.toLong()
        override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long =
            inserted.count { !it.success && it.userId == userId }.toLong()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeLogger(
        latestHash: String? = null,
        throwOnInsert: Boolean = false,
    ): Pair<SecurityAuditLogger, FakeAuditRepository> {
        val fake = FakeAuditRepository(latestHash = latestHash, throwOnInsert = throwOnInsert)
        return SecurityAuditLogger(fake, deviceId = "device-01") to fake
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - logLoginAttempt success true inserts LOGIN_ATTEMPT with success flag`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logLoginAttempt(
            success = true,
            userId = "user-1",
            userName = "Alice",
            userRole = Role.CASHIER,
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.LOGIN_ATTEMPT, entry.eventType)
        assertEquals("user-1", entry.userId)
        assertEquals("Alice", entry.userName)
        assertEquals(Role.CASHIER, entry.userRole)
        assertEquals("device-01", entry.deviceId)
        assertTrue(entry.success)
    }

    @Test
    fun `B - logLoginAttempt success false inserts entry with success false`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logLoginAttempt(success = false, userId = "user-1")

        assertEquals(1, fake.inserted.size)
        assertFalse(fake.inserted.first().success)
        assertEquals(AuditEventType.LOGIN_ATTEMPT, fake.inserted.first().eventType)
    }

    @Test
    fun `C - logPinAttempt maps to LOGIN_ATTEMPT event type`() = runTest {
        val (logger, fake) = makeLogger()

        // logPinAttempt uses LOGIN_ATTEMPT internally (source is "pin")
        logger.logPinAttempt(
            success = true,
            userId = "user-2",
            userName = "Bob",
            userRole = Role.STORE_MANAGER,
        )

        assertEquals(1, fake.inserted.size)
        assertEquals(AuditEventType.LOGIN_ATTEMPT, fake.inserted.first().eventType)
        assertEquals("user-2", fake.inserted.first().userId)
        assertTrue(fake.inserted.first().success)
    }

    @Test
    fun `D - logPermissionDenied inserts PERMISSION_DENIED with success false and payload`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logPermissionDenied(
            userId = "user-3",
            userName = "Carol",
            userRole = Role.CASHIER,
            permission = Permission.MANAGE_USERS,
            screen = "UserManagement",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.PERMISSION_DENIED, entry.eventType)
        assertFalse(entry.success)
        assertTrue(entry.payload.contains("MANAGE_USERS"))
        assertTrue(entry.payload.contains("UserManagement"))
    }

    @Test
    fun `E - logOrderCreated inserts ORDER_CREATED with entityId and totalAmount in payload`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logOrderCreated(
            userId = "cashier-1",
            orderId = "ord-99",
            totalAmount = 250.0,
            itemCount = 3,
            paymentMethod = "CASH",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.ORDER_CREATED, entry.eventType)
        assertEquals("ord-99", entry.entityId)
        assertEquals("ORDER", entry.entityType)
        assertTrue(entry.success)
        assertTrue(entry.payload.contains("250"))
    }

    @Test
    fun `F - logOrderVoided inserts ORDER_VOIDED with entityId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logOrderVoided(
            userId = "mgr-1",
            orderId = "ord-50",
            reason = "Customer changed mind",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.ORDER_VOIDED, entry.eventType)
        assertEquals("ord-50", entry.entityId)
        assertEquals("ORDER", entry.entityType)
        assertTrue(entry.payload.contains("Customer changed mind"))
    }

    @Test
    fun `G - logRegisterOpen inserts REGISTER_OPENED with registerId`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logRegisterOpen(
            userId = "cashier-1",
            registerId = "reg-01",
            openingBalance = 500.0,
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.REGISTER_OPENED, entry.eventType)
        assertEquals("reg-01", entry.entityId)
        assertEquals("REGISTER", entry.entityType)
        assertTrue(entry.success)
    }

    @Test
    fun `H - hash chain carries previousHash from getLatestHash`() = runTest {
        val (logger, fake) = makeLogger(latestHash = "abc123hash")

        logger.logLoginAttempt(success = true, userId = "u1")

        assertEquals(1, fake.inserted.size)
        assertEquals("abc123hash", fake.inserted.first().previousHash)
    }

    @Test
    fun `I - when no previous hash, previousHash is GENESIS`() = runTest {
        val (logger, fake) = makeLogger(latestHash = null)

        logger.logLoginAttempt(success = true, userId = "u1")

        assertEquals("GENESIS", fake.inserted.first().previousHash)
    }

    @Test
    fun `J - exception from auditRepository does not propagate`() = runTest {
        val (logger, _) = makeLogger(throwOnInsert = true)

        // Must not throw — audit logging is fire-and-forget
        logger.logLoginAttempt(success = true, userId = "u1")
        // If we get here without exception, the test passes
    }

    @Test
    fun `K - computeExpectedHash is deterministic and produces 64-char hex string`() = runTest {
        val (logger, fake) = makeLogger()
        logger.logLoginAttempt(success = true, userId = "u1", userName = "Alice")

        val entry = fake.inserted.first()
        val hash1 = SecurityAuditLogger.computeExpectedHash(entry, entry.previousHash)
        val hash2 = SecurityAuditLogger.computeExpectedHash(entry, entry.previousHash)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue(hash1.all { it.isDigit() || it in 'a'..'f' }, "Expected hex chars only, got: $hash1")
    }

    @Test
    fun `L - logSettingsChanged inserts SETTINGS_CHANGED with key in payload and prev or new values`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logSettingsChanged(
            userId = "admin-1",
            key = "pos.max_discount_pct",
            previousValue = "10",
            newValue = "20",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.SETTINGS_CHANGED, entry.eventType)
        assertEquals("10", entry.previousValue)
        assertEquals("20", entry.newValue)
        assertTrue(entry.payload.contains("pos.max_discount_pct"))
    }

    @Test
    fun `M - logDataExported inserts DATA_EXPORTED with success true`() = runTest {
        val (logger, fake) = makeLogger()

        logger.logDataExported(
            userId = "admin-1",
            action = "GDPR_EXPORT",
            entityId = "cust-99",
        )

        assertEquals(1, fake.inserted.size)
        val entry = fake.inserted.first()
        assertEquals(AuditEventType.DATA_EXPORTED, entry.eventType)
        assertEquals("cust-99", entry.entityId)
        assertTrue(entry.success)
    }
}

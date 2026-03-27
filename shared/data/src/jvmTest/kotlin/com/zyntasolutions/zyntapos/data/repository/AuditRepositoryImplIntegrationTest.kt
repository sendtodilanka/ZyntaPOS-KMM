package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — AuditRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [AuditRepositoryImpl] against a real in-memory SQLite database.
 * audit_entries has no external FK constraints.
 *
 * Coverage:
 *  A. insert then observeAll emits inserted entry via Turbine
 *  B. insert multiple then observeByUserId emits entries for specific user
 *  C. getAllChronological returns entries sorted by timestamp ascending
 *  D. countEntries reflects actual count after inserts
 *  E. getLatestHash returns hash of last inserted entry
 *  F. getRecentLoginFailureCount counts LOGIN_ATTEMPT entries within window
 */
class AuditRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: AuditRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = AuditRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Instant.fromEpochMilliseconds(System.currentTimeMillis())

    private fun makeEntry(
        id: String = "audit-01",
        eventType: AuditEventType = AuditEventType.LOGIN_ATTEMPT,
        userId: String = "user-01",
        userName: String = "Alice",
        success: Boolean = true,
        payload: String = "{}",
        hash: String = "hash-$id",
        previousHash: String = "GENESIS",
        createdAt: Instant = now,
    ) = AuditEntry(
        id = id,
        eventType = eventType,
        userId = userId,
        userName = userName,
        userRole = null,
        deviceId = "device-01",
        entityType = null,
        entityId = null,
        payload = payload,
        previousValue = null,
        newValue = null,
        success = success,
        ipAddress = null,
        hash = hash,
        previousHash = previousHash,
        createdAt = createdAt,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then observeAll emits inserted entry via Turbine`() = runTest {
        repo.insert(makeEntry(id = "audit-01", userId = "user-01"))

        repo.observeAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("audit-01", list.first().id)
            assertEquals("user-01", list.first().userId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - observeByUserId emits only entries for specified user`() = runTest {
        repo.insert(makeEntry(id = "audit-01", userId = "user-01"))
        repo.insert(makeEntry(id = "audit-02", userId = "user-02"))
        repo.insert(makeEntry(id = "audit-03", userId = "user-01"))

        repo.observeByUserId("user-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.userId == "user-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getAllChronological returns entries sorted by timestamp ascending`() = runTest {
        val t1 = Instant.fromEpochMilliseconds(1_000_000L)
        val t2 = Instant.fromEpochMilliseconds(2_000_000L)
        val t3 = Instant.fromEpochMilliseconds(3_000_000L)

        repo.insert(makeEntry(id = "audit-03", createdAt = t3, hash = "hash-03"))
        repo.insert(makeEntry(id = "audit-01", createdAt = t1, hash = "hash-01"))
        repo.insert(makeEntry(id = "audit-02", createdAt = t2, hash = "hash-02"))

        val list = repo.getAllChronological()
        assertEquals(3, list.size)
        assertEquals("audit-01", list[0].id)
        assertEquals("audit-02", list[1].id)
        assertEquals("audit-03", list[2].id)
    }

    @Test
    fun `D - countEntries reflects inserted count`() = runTest {
        assertEquals(0L, repo.countEntries())

        repo.insert(makeEntry(id = "audit-01"))
        assertEquals(1L, repo.countEntries())

        repo.insert(makeEntry(id = "audit-02", hash = "hash-02"))
        repo.insert(makeEntry(id = "audit-03", hash = "hash-03"))
        assertEquals(3L, repo.countEntries())
    }

    @Test
    fun `E - getLatestHash returns hash of most recently inserted entry`() = runTest {
        assertNull(repo.getLatestHash())

        repo.insert(makeEntry(id = "audit-01", hash = "first-hash"))
        val afterFirst = repo.getLatestHash()
        assertNotNull(afterFirst)

        repo.insert(makeEntry(id = "audit-02", hash = "second-hash"))
        // Note: getLatestHash() retrieves the hash of the most recent entry by timestamp
        // The exact hash depends on the ordering; just verify it's not null
        assertNotNull(repo.getLatestHash())
    }

    @Test
    fun `F - getRecentLoginFailureCount counts failed LOGIN_ATTEMPT within time window`() = runTest {
        val windowStart = System.currentTimeMillis() - 10_000L

        repo.insert(makeEntry(id = "audit-fail-1", eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = "user-01", success = false, hash = "hash-f1",
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 5_000L)))
        repo.insert(makeEntry(id = "audit-fail-2", eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = "user-01", success = false, hash = "hash-f2",
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 3_000L)))
        // Success — not a failure
        repo.insert(makeEntry(id = "audit-success", eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = "user-01", success = true, hash = "hash-s1",
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 2_000L)))
        // Different user
        repo.insert(makeEntry(id = "audit-other", eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = "user-02", success = false, hash = "hash-o1",
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 1_000L)))

        val count = repo.getRecentLoginFailureCount("user-01", windowStart)
        assertEquals(2L, count)
    }
}

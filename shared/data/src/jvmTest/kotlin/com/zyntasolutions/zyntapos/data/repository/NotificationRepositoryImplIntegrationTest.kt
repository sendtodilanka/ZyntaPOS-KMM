package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Notification
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — NotificationRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [NotificationRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. insert → getUnread emits unread notifications via Turbine
 *  B. getAll returns all notifications for recipient via Turbine
 *  C. getUnreadCount returns correct count
 *  D. markRead sets is_read and updates unread list
 *  E. markAllRead clears all unread for recipient
 *  F. pruneOld deletes notifications before timestamp
 *  G. insert with referenceType and referenceId round-trip
 */
class NotificationRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: NotificationRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = NotificationRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeNotification(
        id: String,
        recipientId: String = "user-01",
        type: Notification.NotificationType = Notification.NotificationType.LOW_STOCK,
        title: String = "Low Stock Alert",
        message: String = "Product X is running low",
        channel: Notification.Channel = Notification.Channel.IN_APP,
        recipientType: Notification.RecipientType = Notification.RecipientType.USER,
        isRead: Boolean = false,
        referenceType: String? = null,
        referenceId: String? = null,
        createdAt: Long = now,
    ) = Notification(
        id = id,
        type = type,
        title = title,
        message = message,
        channel = channel,
        recipientType = recipientType,
        recipientId = recipientId,
        isRead = isRead,
        referenceType = referenceType,
        referenceId = referenceId,
        createdAt = createdAt,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getUnread emits unread notifications via Turbine`() = runTest {
        repo.insert(makeNotification(id = "notif-01", isRead = false))
        repo.insert(makeNotification(id = "notif-02", isRead = true))

        repo.getUnread("user-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("notif-01", list.first().id)
            assertTrue(!list.first().isRead)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - getAll returns all notifications for recipient via Turbine`() = runTest {
        repo.insert(makeNotification(id = "notif-01", recipientId = "user-01", isRead = false))
        repo.insert(makeNotification(id = "notif-02", recipientId = "user-01", isRead = true))
        repo.insert(makeNotification(id = "notif-03", recipientId = "user-02", isRead = false))

        repo.getAll("user-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.id == "notif-01" })
            assertTrue(list.any { it.id == "notif-02" })
            assertTrue(list.none { it.recipientId == "user-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getUnreadCount returns correct count`() = runTest {
        repo.insert(makeNotification(id = "notif-01", isRead = false))
        repo.insert(makeNotification(id = "notif-02", isRead = false))
        repo.insert(makeNotification(id = "notif-03", isRead = true))

        val countResult = repo.getUnreadCount("user-01")
        assertIs<Result.Success<Int>>(countResult)
        assertEquals(2, countResult.data)
    }

    @Test
    fun `D - markRead sets is_read and removes from unread list`() = runTest {
        repo.insert(makeNotification(id = "notif-01", isRead = false))
        repo.insert(makeNotification(id = "notif-02", isRead = false))

        val markResult = repo.markRead("notif-01")
        assertIs<Result.Success<Unit>>(markResult)

        repo.getUnread("user-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("notif-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }

        val countResult = repo.getUnreadCount("user-01")
        assertIs<Result.Success<Int>>(countResult)
        assertEquals(1, countResult.data)
    }

    @Test
    fun `E - markAllRead clears all unread for recipient`() = runTest {
        repo.insert(makeNotification(id = "notif-01", recipientId = "user-01", isRead = false))
        repo.insert(makeNotification(id = "notif-02", recipientId = "user-01", isRead = false))
        repo.insert(makeNotification(id = "notif-03", recipientId = "user-02", isRead = false))

        val markAllResult = repo.markAllRead("user-01")
        assertIs<Result.Success<Unit>>(markAllResult)

        val countUser01 = (repo.getUnreadCount("user-01") as Result.Success).data
        assertEquals(0, countUser01)

        // user-02 unread should be untouched
        val countUser02 = (repo.getUnreadCount("user-02") as Result.Success).data
        assertEquals(1, countUser02)
    }

    @Test
    fun `F - pruneOld deletes only read notifications before timestamp`() = runTest {
        val cutoff = now
        val old = cutoff - 100_000L
        val recent = cutoff + 100_000L
        // Read + old → will be pruned
        repo.insert(makeNotification(id = "notif-old-read", isRead = true, createdAt = old))
        // Unread + old → NOT pruned (pruneOld only deletes is_read=1)
        repo.insert(makeNotification(id = "notif-old-unread", isRead = false, createdAt = old))
        // Read + recent → NOT pruned (created_at >= cutoff)
        repo.insert(makeNotification(id = "notif-recent-read", isRead = true, createdAt = recent))

        val pruneResult = repo.pruneOld(cutoff)
        assertIs<Result.Success<Unit>>(pruneResult)

        repo.getAll("user-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.none { it.id == "notif-old-read" })
            assertTrue(list.any { it.id == "notif-old-unread" })
            assertTrue(list.any { it.id == "notif-recent-read" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - insert with referenceType and referenceId round-trip`() = runTest {
        repo.insert(
            makeNotification(
                id = "notif-01",
                type = Notification.NotificationType.SYNC_CONFLICT,
                referenceType = "PRODUCT",
                referenceId = "prod-001",
            )
        )

        repo.getAll("user-01").test {
            val list = awaitItem()
            val fetched = list.first()
            assertNotNull(fetched)
            assertEquals(Notification.NotificationType.SYNC_CONFLICT, fetched.type)
            assertEquals("PRODUCT", fetched.referenceType)
            assertEquals("prod-001", fetched.referenceId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

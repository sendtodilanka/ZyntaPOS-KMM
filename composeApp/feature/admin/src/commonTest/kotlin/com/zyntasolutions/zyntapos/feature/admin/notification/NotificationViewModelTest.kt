package com.zyntasolutions.zyntapos.feature.admin.notification

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Notification
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [NotificationViewModel].
 *
 * Uses hand-rolled fakes for [NotificationRepository] and [AuthRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockUser = User(
        id = "user-001",
        name = "Admin User",
        email = "admin@zyntapos.com",
        role = Role.ADMIN,
        storeId = "store-001",
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    private val unreadNotification = Notification(
        id = "notif-001",
        type = Notification.NotificationType.LOW_STOCK,
        title = "Low Stock Alert",
        message = "Product X has 2 units remaining",
        recipientId = "user-001",
        isRead = false,
        createdAt = 1_700_000_000_000L,
    )

    private val readNotification = Notification(
        id = "notif-002",
        type = Notification.NotificationType.SYSTEM,
        title = "System Update",
        message = "Version 2.0 available",
        recipientId = "user-001",
        isRead = true,
        createdAt = 1_700_000_001_000L,
    )

    // Fake repository state
    private val notificationsFlow = MutableStateFlow(listOf(unreadNotification, readNotification))
    private var markReadResult: Result<Unit> = Result.Success(Unit)
    private var markAllReadResult: Result<Unit> = Result.Success(Unit)

    private val fakeNotificationRepo = object : NotificationRepository {
        override fun getUnread(recipientId: String): Flow<List<Notification>> =
            MutableStateFlow(notificationsFlow.value.filter { !it.isRead })

        override fun getAll(recipientId: String): Flow<List<Notification>> = notificationsFlow

        override suspend fun getUnreadCount(recipientId: String): Result<Int> =
            Result.Success(notificationsFlow.value.count { !it.isRead })

        override suspend fun insert(notification: Notification): Result<Unit> = Result.Success(Unit)

        override suspend fun markRead(id: String): Result<Unit> = markReadResult

        override suspend fun markAllRead(recipientId: String): Result<Unit> = markAllReadResult

        override suspend fun pruneOld(beforeEpochMillis: Long): Result<Unit> = Result.Success(Unit)
    }

    private val sessionFlow = MutableStateFlow<User?>(mockUser)

    private val fakeAuthRepo = object : AuthRepository {
        override suspend fun login(email: String, password: String): Result<User> =
            Result.Success(mockUser)
        override suspend fun logout() { sessionFlow.value = null }
        override fun getSession(): Flow<User?> = sessionFlow
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> = Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> = Result.Success(true)
    }

    private lateinit var viewModel: NotificationViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = NotificationViewModel(
            notificationRepository = fakeNotificationRepo,
            authRepository = fakeAuthRepo,
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty notifications and no error`() {
        val state = viewModel.currentState
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.showUnreadOnly)
    }

    // ── Notification loading ───────────────────────────────────────────────────

    @Test
    fun `notifications are loaded from repository after init`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.currentState
        assertEquals(2, state.notifications.size)
    }

    @Test
    fun `unreadCount reflects number of unread notifications`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.currentState.unreadCount)
    }

    // ── ToggleUnreadFilter ─────────────────────────────────────────────────────

    @Test
    fun `ToggleUnreadFilter switches showUnreadOnly flag`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.currentState.showUnreadOnly)

        viewModel.state.test {
            awaitItem() // current
            viewModel.handleIntentForTest(NotificationIntent.ToggleUnreadFilter)
            val toggled = awaitItem()
            assertTrue(toggled.showUnreadOnly)
        }
    }

    @Test
    fun `ToggleUnreadFilter twice restores original flag`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(NotificationIntent.ToggleUnreadFilter)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(NotificationIntent.ToggleUnreadFilter)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.currentState.showUnreadOnly)
    }

    @Test
    fun `visibleNotifications filters to unread when showUnreadOnly is true`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(NotificationIntent.ToggleUnreadFilter)
        testDispatcher.scheduler.advanceUntilIdle()

        val visible = viewModel.currentState.visibleNotifications
        assertTrue(visible.all { !it.isRead })
        assertEquals(1, visible.size)
    }

    @Test
    fun `visibleNotifications returns all when showUnreadOnly is false`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val visible = viewModel.currentState.visibleNotifications
        assertEquals(2, visible.size)
    }

    // ── MarkRead ───────────────────────────────────────────────────────────────

    @Test
    fun `MarkRead success emits no effect`() = runTest {
        markReadResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(NotificationIntent.MarkRead("notif-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `MarkRead failure emits ShowSnackbar effect`() = runTest {
        markReadResult = Result.Error(Exception("Network error"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(NotificationIntent.MarkRead("notif-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is NotificationEffect.ShowSnackbar)
            assertTrue((effect as NotificationEffect.ShowSnackbar).message.contains("Network error"))
        }
    }

    // ── MarkAllRead ────────────────────────────────────────────────────────────

    @Test
    fun `MarkAllRead success emits ShowSnackbar with success message`() = runTest {
        markAllReadResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(NotificationIntent.MarkAllRead)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is NotificationEffect.ShowSnackbar)
            assertTrue((effect as NotificationEffect.ShowSnackbar).message.contains("marked as read", ignoreCase = true))
        }
    }

    @Test
    fun `MarkAllRead failure emits ShowSnackbar with error message`() = runTest {
        markAllReadResult = Result.Error(Exception("Server unavailable"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(NotificationIntent.MarkAllRead)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is NotificationEffect.ShowSnackbar)
            assertTrue((effect as NotificationEffect.ShowSnackbar).message.contains("Server unavailable"))
        }
    }

    // ── DismissError ───────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(NotificationIntent.DismissError)
            val s = awaitItem()
            assertNull(s.error)
        }
    }

    // ── LoadNotifications ──────────────────────────────────────────────────────

    @Test
    fun `LoadNotifications sets isLoading true`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.handleIntentForTest(NotificationIntent.LoadNotifications)
            val loading = awaitItem()
            assertTrue(loading.isLoading)
        }
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private suspend fun NotificationViewModel.handleIntentForTest(intent: NotificationIntent) =
    handleIntent(intent)

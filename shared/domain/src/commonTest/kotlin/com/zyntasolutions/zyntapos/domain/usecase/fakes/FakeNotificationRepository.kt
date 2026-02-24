package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Notification
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Builds a [Notification] test fixture with sensible defaults. */
fun buildNotification(
    id: String = "notif-1",
    type: Notification.NotificationType = Notification.NotificationType.SYSTEM,
    title: String = "Test Notification",
    message: String = "This is a test notification.",
    recipientId: String = "user-1",
    isRead: Boolean = false,
    referenceType: String? = null,
    referenceId: String? = null,
    createdAt: Long = 1_700_000_000_000L,
): Notification = Notification(
    id = id,
    type = type,
    title = title,
    message = message,
    recipientId = recipientId,
    isRead = isRead,
    referenceType = referenceType,
    referenceId = referenceId,
    createdAt = createdAt,
    readAt = if (isRead) createdAt + 60_000L else null,
)

/**
 * In-memory [NotificationRepository] for domain layer unit tests.
 *
 * @param shouldFail If true every mutating operation returns [Result.Error].
 */
class FakeNotificationRepository(
    private val shouldFail: Boolean = false,
) : NotificationRepository {

    private val _store = MutableStateFlow<List<Notification>>(emptyList())

    fun seed(vararg notifications: Notification) {
        _store.value = notifications.toList()
    }

    override fun getUnread(recipientId: String): Flow<List<Notification>> =
        _store.map { list -> list.filter { it.recipientId == recipientId && !it.isRead } }

    override fun getAll(recipientId: String): Flow<List<Notification>> =
        _store.map { list -> list.filter { it.recipientId == recipientId } }

    override suspend fun getUnreadCount(recipientId: String): Result<Int> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(_store.value.count { it.recipientId == recipientId && !it.isRead })
    }

    override suspend fun insert(notification: Notification): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        _store.value = _store.value + notification
        return Result.Success(Unit)
    }

    override suspend fun markRead(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        _store.value = _store.value.map { n ->
            if (n.id == id) n.copy(isRead = true, readAt = System.currentTimeMillis()) else n
        }
        return Result.Success(Unit)
    }

    override suspend fun markAllRead(recipientId: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        _store.value = _store.value.map { n ->
            if (n.recipientId == recipientId && !n.isRead) {
                n.copy(isRead = true, readAt = System.currentTimeMillis())
            } else n
        }
        return Result.Success(Unit)
    }

    override suspend fun pruneOld(beforeEpochMillis: Long): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        _store.value = _store.value.filter { n ->
            !(n.isRead && n.readAt != null && n.readAt < beforeEpochMillis)
        }
        return Result.Success(Unit)
    }
}

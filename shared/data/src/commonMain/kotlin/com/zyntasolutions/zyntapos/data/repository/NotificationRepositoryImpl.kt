package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.Notifications
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Notification
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class NotificationRepositoryImpl(
    private val db: ZyntaDatabase,
) : NotificationRepository {

    private val nq get() = db.notificationsQueries

    override fun getUnread(recipientId: String): Flow<List<Notification>> =
        nq.getUnreadNotifications(recipientId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getAll(recipientId: String): Flow<List<Notification>> =
        nq.getAllNotificationsForRecipient(recipientId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getUnreadCount(recipientId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            nq.getUnreadCountForRecipient(recipientId).executeAsOne().toInt()
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(notification: Notification): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            nq.insertNotification(
                id = notification.id,
                type = notification.type.name,
                title = notification.title,
                message = notification.message,
                channel = notification.channel.name,
                recipient_type = notification.recipientType.name,
                recipient_id = notification.recipientId,
                is_read = if (notification.isRead) 1L else 0L,
                reference_type = notification.referenceType,
                reference_id = notification.referenceId,
                created_at = notification.createdAt,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun markRead(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            nq.markNotificationRead(read_at = now, id = id)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Mark read failed", cause = t)) },
        )
    }

    override suspend fun markAllRead(recipientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            nq.markAllRead(read_at = now, recipient_id = recipientId)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Mark all read failed", cause = t)) },
        )
    }

    override suspend fun pruneOld(beforeEpochMillis: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            nq.deleteOldNotifications(created_at = beforeEpochMillis)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Prune failed", cause = t)) },
        )
    }

    private fun toDomain(row: Notifications) = Notification(
        id = row.id,
        type = runCatching { Notification.NotificationType.valueOf(row.type) }
            .getOrDefault(Notification.NotificationType.SYSTEM),
        title = row.title,
        message = row.message,
        channel = runCatching { Notification.Channel.valueOf(row.channel) }
            .getOrDefault(Notification.Channel.IN_APP),
        recipientType = runCatching { Notification.RecipientType.valueOf(row.recipient_type) }
            .getOrDefault(Notification.RecipientType.ROLE),
        recipientId = row.recipient_id,
        isRead = row.is_read == 1L,
        referenceType = row.reference_type,
        referenceId = row.reference_id,
        createdAt = row.created_at,
        readAt = row.read_at,
    )
}

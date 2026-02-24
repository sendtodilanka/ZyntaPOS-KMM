package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Notification
import kotlinx.coroutines.flow.Flow

/**
 * Contract for in-app notification delivery and management.
 */
interface NotificationRepository {

    /** Emits unread notifications for [recipientId], most recent first. */
    fun getUnread(recipientId: String): Flow<List<Notification>>

    /** Emits all notifications (read + unread) for [recipientId], up to 50 entries. */
    fun getAll(recipientId: String): Flow<List<Notification>>

    /** Returns the count of unread notifications for [recipientId]. */
    suspend fun getUnreadCount(recipientId: String): Result<Int>

    /** Inserts a new notification. */
    suspend fun insert(notification: Notification): Result<Unit>

    /** Marks a single notification as read. */
    suspend fun markRead(id: String): Result<Unit>

    /** Marks all unread notifications for [recipientId] as read. */
    suspend fun markAllRead(recipientId: String): Result<Unit>

    /** Deletes notifications that are read and older than [beforeEpochMillis]. */
    suspend fun pruneOld(beforeEpochMillis: Long): Result<Unit>
}

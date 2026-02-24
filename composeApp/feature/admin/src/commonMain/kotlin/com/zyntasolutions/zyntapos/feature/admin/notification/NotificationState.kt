package com.zyntasolutions.zyntapos.feature.admin.notification

import com.zyntasolutions.zyntapos.domain.model.Notification

/**
 * MVI — UI state for the notification inbox screen.
 *
 * @property notifications  Full list of notifications (read + unread) for the current user.
 * @property unreadCount    Count of unread notifications — drives bell badge in TopAppBar.
 * @property showUnreadOnly If true, the inbox filters to unread items only.
 * @property isLoading      True while the initial list is being fetched.
 * @property error          Non-null when a repository error occurred.
 */
data class NotificationState(
    val notifications: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val showUnreadOnly: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** Filtered view respecting [showUnreadOnly]. */
    val visibleNotifications: List<Notification>
        get() = if (showUnreadOnly) notifications.filter { !it.isRead } else notifications
}

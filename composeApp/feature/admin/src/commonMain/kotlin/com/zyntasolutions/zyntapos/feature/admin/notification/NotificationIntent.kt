package com.zyntasolutions.zyntapos.feature.admin.notification

/**
 * MVI — user intents for the notification inbox.
 */
sealed interface NotificationIntent {
    /** Load or refresh the notification list for the current user. */
    data object LoadNotifications : NotificationIntent

    /** Mark a single notification as read. */
    data class MarkRead(val notificationId: String) : NotificationIntent

    /** Mark all unread notifications as read. */
    data object MarkAllRead : NotificationIntent

    /** Toggle between showing all notifications and unread-only. */
    data object ToggleUnreadFilter : NotificationIntent

    /** Dismiss the current error banner. */
    data object DismissError : NotificationIntent
}

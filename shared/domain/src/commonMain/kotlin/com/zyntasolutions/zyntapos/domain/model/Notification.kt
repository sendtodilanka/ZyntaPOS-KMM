package com.zyntasolutions.zyntapos.domain.model

/**
 * An in-app notification delivered to a specific user or role.
 *
 * @property id Unique identifier (UUID v4).
 * @property type The notification category — determines icon and routing.
 * @property title Short notification title.
 * @property message Full notification body text.
 * @property channel Delivery channel.
 * @property recipientType Whether this targets a specific user or all users of a role.
 * @property recipientId The user ID or role name.
 * @property isRead Whether the recipient has acknowledged this notification.
 * @property referenceType The type of related entity, if any.
 * @property referenceId The ID of the related entity.
 * @property createdAt Epoch millis when the notification was created.
 * @property readAt Epoch millis when the notification was read.
 */
data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val channel: Channel = Channel.IN_APP,
    val recipientType: RecipientType = RecipientType.ROLE,
    val recipientId: String,
    val isRead: Boolean = false,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val createdAt: Long,
    val readAt: Long? = null,
) {
    enum class NotificationType {
        LOW_STOCK,
        PAYMENT_DUE,
        EXPIRY,
        SYNC_CONFLICT,
        SYSTEM,
    }

    enum class Channel { IN_APP, EMAIL, PUSH }

    enum class RecipientType { ROLE, USER }
}

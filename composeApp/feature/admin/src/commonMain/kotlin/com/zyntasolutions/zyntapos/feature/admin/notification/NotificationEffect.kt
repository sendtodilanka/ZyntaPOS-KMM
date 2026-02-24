package com.zyntasolutions.zyntapos.feature.admin.notification

/**
 * MVI — one-shot side effects for the notification inbox.
 */
sealed interface NotificationEffect {
    /** Navigate to the entity referenced by the tapped notification. */
    data class NavigateToReference(val referenceType: String, val referenceId: String) : NotificationEffect

    /** Show a snackbar feedback message. */
    data class ShowSnackbar(val message: String) : NotificationEffect
}

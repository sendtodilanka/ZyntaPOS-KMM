package com.zyntasolutions.zyntapos.feature.admin.notification

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.catch
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * MVI ViewModel for the notification inbox feature.
 *
 * Observes the notification stream for the current authenticated user,
 * provides mark-as-read and mark-all-read operations, and maintains the
 * unread badge count consumed by the top-app-bar bell icon.
 *
 * @param notificationRepository Source of notification records.
 * @param currentUserId          Authenticated user ID resolved at DI construction time.
 */
class NotificationViewModel(
    private val notificationRepository: NotificationRepository,
    private val currentUserId: String,
) : BaseViewModel<NotificationState, NotificationIntent, NotificationEffect>(NotificationState()) {

    init {
        observeNotifications()
    }

    private fun observeNotifications() {
        notificationRepository.getAll(currentUserId)
            .catch { e -> updateState { copy(isLoading = false, error = e.message) } }
            .onEach { notifications ->
                val unread = notifications.count { !it.isRead }
                updateState {
                    copy(
                        notifications = notifications,
                        unreadCount = unread,
                        isLoading = false,
                        error = null,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: NotificationIntent) {
        when (intent) {
            NotificationIntent.LoadNotifications  -> updateState { copy(isLoading = true) }
            is NotificationIntent.MarkRead        -> onMarkRead(intent.notificationId)
            NotificationIntent.MarkAllRead        -> onMarkAllRead()
            NotificationIntent.ToggleUnreadFilter -> updateState { copy(showUnreadOnly = !showUnreadOnly) }
            NotificationIntent.DismissError       -> updateState { copy(error = null) }
        }
    }

    private suspend fun onMarkRead(notificationId: String) {
        when (val result = notificationRepository.markRead(notificationId)) {
            is Result.Success -> { /* observer will update state */ }
            is Result.Error   -> sendEffect(NotificationEffect.ShowSnackbar(
                result.exception.message ?: "Failed to mark notification as read"
            ))
            is Result.Loading -> {}
        }
    }

    private suspend fun onMarkAllRead() {
        when (val result = notificationRepository.markAllRead(currentUserId)) {
            is Result.Success -> sendEffect(NotificationEffect.ShowSnackbar("All notifications marked as read"))
            is Result.Error   -> sendEffect(NotificationEffect.ShowSnackbar(
                result.exception.message ?: "Failed to mark all as read"
            ))
            is Result.Loading -> {}
        }
    }
}

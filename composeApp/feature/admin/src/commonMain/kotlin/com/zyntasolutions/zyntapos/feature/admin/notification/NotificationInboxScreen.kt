package com.zyntasolutions.zyntapos.feature.admin.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Notification
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Notification inbox screen — shows all in-app notifications for the current user.
 *
 * Features:
 * - All / Unread filter chip
 * - Mark All Read action in TopAppBar
 * - Notification rows with type icon, title, message, and relative timestamp
 * - Unread notifications have a colour-coded left indicator
 * - Tap to mark as read and navigate to the referenced entity (if any)
 *
 * @param onNavigateUp Back navigation handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(
    onNavigateUp: () -> Unit,
    viewModel: NotificationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is NotificationEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
                is NotificationEffect.NavigateToReference -> {
                    // Host navigation is handled by the navigation layer based on referenceType
                }
            }
        }
    }

    ZyntaPageScaffold(
        title = "Notifications",
        onNavigateBack = onNavigateUp,
        actions = {
            if (state.unreadCount > 0) {
                IconButton(onClick = { viewModel.dispatch(NotificationIntent.MarkAllRead) }) {
                    Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
                }
            }
        },
        snackbarHostState = snackbarHostState,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // ── Filter chips ─────────────────────────────────────────────
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    item {
                        FilterChip(
                            selected = !state.showUnreadOnly,
                            onClick = {
                                if (state.showUnreadOnly) {
                                    viewModel.dispatch(NotificationIntent.ToggleUnreadFilter)
                                }
                            },
                            label = { Text("All (${state.notifications.size})") },
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.showUnreadOnly,
                            onClick = {
                                if (!state.showUnreadOnly) {
                                    viewModel.dispatch(NotificationIntent.ToggleUnreadFilter)
                                }
                            },
                            label = { Text("Unread (${state.unreadCount})") },
                        )
                    }
                }

                HorizontalDivider()

                // ── List ─────────────────────────────────────────────────────
                when {
                    state.visibleNotifications.isEmpty() && !state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ZyntaEmptyState(
                                icon = Icons.Default.Notifications,
                                title = if (state.showUnreadOnly) "No Unread Notifications" else "No Notifications",
                                subtitle = if (state.showUnreadOnly) {
                                    "All caught up! Switch to 'All' to see past notifications."
                                } else {
                                    "You have no notifications."
                                },
                                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                            )
                        }
                    }

                    state.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.xl),
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = ZyntaSpacing.xs),
                        ) {
                            items(
                                items = state.visibleNotifications,
                                key = { it.id },
                            ) { notification ->
                                NotificationRow(
                                    notification = notification,
                                    onTap = {
                                        viewModel.dispatch(NotificationIntent.MarkRead(notification.id))
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            if (state.isLoading) ZyntaLoadingOverlay(isLoading = true)
        }
    }
}

// ─── Notification row composable ─────────────────────────────────────────────

@Composable
private fun NotificationRow(
    notification: Notification,
    onTap: () -> Unit,
) {
    val containerColor = if (!notification.isRead) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .clickable(onClick = onTap)
            .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        // Type icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = notificationIcon(notification.type),
                contentDescription = notification.type.name,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        Spacer(Modifier.width(ZyntaSpacing.sm))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Normal,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(ZyntaSpacing.xs))
                Text(
                    text = formatTimestamp(notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Unread indicator
        if (!notification.isRead) {
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun notificationIcon(type: Notification.NotificationType): ImageVector = when (type) {
    Notification.NotificationType.LOW_STOCK      -> Icons.Default.Inventory2
    Notification.NotificationType.PAYMENT_DUE    -> Icons.Default.Payment
    Notification.NotificationType.EXPIRY         -> Icons.Default.Schedule
    Notification.NotificationType.SYNC_CONFLICT  -> Icons.Default.Sync
    Notification.NotificationType.SYSTEM         -> Icons.Default.Settings
}

private fun formatTimestamp(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

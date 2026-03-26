package com.zyntasolutions.zyntapos.feature.pos.fulfillment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.FulfillmentStatus
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentOrder
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

// ─────────────────────────────────────────────────────────────────────────────
// FulfillmentQueueScreen — C4.4: Click & Collect pickup queue
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Click & Collect pickup queue screen.
 *
 * Displays all active BOPIS fulfillment orders for the current store, grouped by
 * status (RECEIVED → PREPARING → READY_FOR_PICKUP). Staff can advance each order
 * through the lifecycle or cancel it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FulfillmentQueueScreen(
    onNavigateUp: () -> Unit,
    viewModel: FulfillmentViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.dispatch(FulfillmentIntent.DismissError)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.POS_PICKUP_QUEUE]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.dispatch(FulfillmentIntent.CheckExpiry) }) {
                        Icon(Icons.Default.Schedule, contentDescription = s[StringResource.POS_CHECK_EXPIRY])
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        FulfillmentQueueContent(
            state = state,
            onMarkPreparing = { viewModel.dispatch(FulfillmentIntent.MarkPreparing(it)) },
            onMarkReady = { viewModel.dispatch(FulfillmentIntent.MarkReady(it)) },
            onMarkPickedUp = { viewModel.dispatch(FulfillmentIntent.MarkPickedUp(it)) },
            onCancelOrder = { viewModel.dispatch(FulfillmentIntent.CancelOrder(it)) },
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun FulfillmentQueueContent(
    state: FulfillmentState,
    onMarkPreparing: (String) -> Unit,
    onMarkReady: (String) -> Unit,
    onMarkPickedUp: (String) -> Unit,
    onCancelOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.pickups.isEmpty() -> {
                val s = LocalStrings.current
                ZyntaEmptyState(
                    icon = Icons.Default.CheckCircle,
                    title = s[StringResource.POS_NO_PENDING_PICKUPS],
                    subtitle = s[StringResource.POS_ALL_PICKUPS_PROCESSED],
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.pickups, key = { it.orderId }) { order ->
                        FulfillmentOrderCard(
                            order = order,
                            isUpdating = state.updatingOrderId == order.orderId,
                            onMarkPreparing = onMarkPreparing,
                            onMarkReady = onMarkReady,
                            onMarkPickedUp = onMarkPickedUp,
                            onCancelOrder = onCancelOrder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FulfillmentOrderCard(
    order: FulfillmentOrder,
    isUpdating: Boolean,
    onMarkPreparing: (String) -> Unit,
    onMarkReady: (String) -> Unit,
    onMarkPickedUp: (String) -> Unit,
    onCancelOrder: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Order #${order.orderId.takeLast(8).uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatDeadline(order.pickupDeadline),
                        style = MaterialTheme.typography.bodySmall,
                        color = deadlineColor(order),
                    )
                }
                StatusBadge(status = order.status)
            }

            Spacer(Modifier.height(12.dp))

            if (isUpdating) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator()
                }
            } else {
                ActionButtons(
                    status = order.status,
                    orderId = order.orderId,
                    onMarkPreparing = onMarkPreparing,
                    onMarkReady = onMarkReady,
                    onMarkPickedUp = onMarkPickedUp,
                    onCancelOrder = onCancelOrder,
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    status: FulfillmentStatus,
    orderId: String,
    onMarkPreparing: (String) -> Unit,
    onMarkReady: (String) -> Unit,
    onMarkPickedUp: (String) -> Unit,
    onCancelOrder: (String) -> Unit,
) {
    val s = LocalStrings.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (status) {
            FulfillmentStatus.RECEIVED -> {
                FilledTonalButton(
                    onClick = { onMarkPreparing(orderId) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(s[StringResource.POS_START_PREPARING])
                }
                OutlinedButton(
                    onClick = { onCancelOrder(orderId) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(s[StringResource.COMMON_CANCEL])
                }
            }
            FulfillmentStatus.PREPARING -> {
                Button(
                    onClick = { onMarkReady(orderId) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(s[StringResource.POS_MARK_READY])
                }
                OutlinedButton(
                    onClick = { onCancelOrder(orderId) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(s[StringResource.COMMON_CANCEL])
                }
            }
            FulfillmentStatus.READY_FOR_PICKUP -> {
                Button(
                    onClick = { onMarkPickedUp(orderId) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(s[StringResource.POS_CONFIRM_PICKUP])
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun StatusBadge(status: FulfillmentStatus) {
    val s = LocalStrings.current
    val (label, color) = when (status) {
        FulfillmentStatus.RECEIVED       -> s[StringResource.POS_STATUS_RECEIVED] to MaterialTheme.colorScheme.tertiary
        FulfillmentStatus.PREPARING      -> s[StringResource.POS_STATUS_PREPARING] to MaterialTheme.colorScheme.primary
        FulfillmentStatus.READY_FOR_PICKUP -> s[StringResource.POS_STATUS_READY] to MaterialTheme.colorScheme.secondary
        else                             -> status.name to MaterialTheme.colorScheme.outline
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun deadlineColor(order: FulfillmentOrder): androidx.compose.ui.graphics.Color {
    val now = System.currentTimeMillis()
    val remaining = order.pickupDeadline - now
    return when {
        remaining < 0            -> MaterialTheme.colorScheme.error
        remaining < 30 * 60_000L -> MaterialTheme.colorScheme.error
        remaining < 60 * 60_000L -> MaterialTheme.colorScheme.tertiary
        else                     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun formatDeadline(epochMillis: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "Deadline: ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}" // Non-composable, cannot use LocalStrings
    } catch (_: Exception) {
        "Deadline: --:--"
    }
}

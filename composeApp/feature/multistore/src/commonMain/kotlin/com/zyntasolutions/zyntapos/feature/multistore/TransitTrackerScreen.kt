package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.TransitEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Timeline screen for a single IN_TRANSIT stock transfer (C1.4).
 *
 * Shows all recorded [TransitEvent]s chronologically and provides a form
 * to log new CHECKPOINT / DELAY_ALERT / LOCATION_UPDATE events.
 *
 * @param transferId        The transfer whose history is displayed.
 * @param onNavigateUp      Back-navigation handler.
 * @param viewModel         Injected via Koin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitTrackerScreen(
    transferId: String,
    onNavigateUp: () -> Unit,
    viewModel: WarehouseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(transferId) {
        viewModel.dispatch(WarehouseIntent.LoadTransitHistory(transferId))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WarehouseEffect.TransitEventAdded ->
                    snackbar.showSnackbar("Transit event logged")
                is WarehouseEffect.ShowError ->
                    snackbar.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Transit Tracker")
                        Text(
                            "Transfer #${transferId.take(8)}…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            val transfer = state.selectedTransfer
            if (transfer?.status == StockTransfer.Status.IN_TRANSIT) {
                FloatingActionButton(
                    onClick = { viewModel.dispatch(WarehouseIntent.OpenTransitEventForm(transferId)) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Log transit event")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // ── In-Transit Count Widget ────────────────────────────────────
            InTransitCountBanner(count = state.inTransitCount)

            Spacer(modifier = Modifier.height(8.dp))

            // ── Event Log Form ─────────────────────────────────────────────
            val form = state.transitEventForm
            if (form.isExpanded && form.transferId == transferId) {
                TransitEventForm(
                    form = form,
                    isLoading = state.isLoading,
                    onFieldChange = { field, value ->
                        viewModel.dispatch(WarehouseIntent.UpdateTransitEventField(field, value))
                    },
                    onSubmit = { viewModel.dispatch(WarehouseIntent.SubmitTransitEvent) },
                    onDismiss = { viewModel.dispatch(WarehouseIntent.DismissTransitEventForm) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Timeline ──────────────────────────────────────────────────
            if (state.transitHistory.isEmpty()) {
                ZyntaEmptyState(
                    title = "No transit events recorded yet",
                    icon = Icons.Default.LocationOn,
                    subtitle = "Tap + to log the first update.",
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.transitHistory, key = { it.id }) { event ->
                        TransitEventRow(event = event)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InTransitCountBanner(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count transfer${if (count != 1) "s" else ""} currently in transit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitEventForm(
    form: TransitEventFormState,
    isLoading: Boolean,
    onFieldChange: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val manualTypes = TransitEvent.EventType.entries.filter { !it.isAutoGenerated }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Log Transit Event",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            // Event type dropdown
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = form.eventType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Event Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    manualTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                onFieldChange("eventType", type.name)
                                dropdownExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.location,
                onValueChange = { onFieldChange("location", it) },
                label = { Text("Location (optional)") },
                placeholder = { Text("e.g. City, Warehouse B, Dock 3") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.note,
                onValueChange = { onFieldChange("note", it) },
                label = { Text("Note") },
                placeholder = { Text("e.g. Slight delay due to traffic") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                isError = form.validationErrors.containsKey("note"),
                supportingText = form.validationErrors["note"]?.let { { Text(it) } },
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !isLoading) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSubmit, enabled = !isLoading) {
                    Text(if (isLoading) "Saving…" else "Log Event")
                }
            }
        }
    }
}

@Composable
private fun TransitEventRow(event: TransitEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Timeline icon column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = eventIconColor(event.eventType),
                modifier = Modifier.size(32.dp),
                contentColor = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = eventIcon(event.eventType),
                        contentDescription = event.eventType.displayName,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Event content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    event.eventType.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatEpoch(event.recordedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            event.location?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            event.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun eventIconColor(type: TransitEvent.EventType) = when (type) {
    TransitEvent.EventType.DISPATCHED      -> MaterialTheme.colorScheme.primary
    TransitEvent.EventType.RECEIVED        -> MaterialTheme.colorScheme.tertiary
    TransitEvent.EventType.DELAY_ALERT     -> MaterialTheme.colorScheme.error
    TransitEvent.EventType.CHECKPOINT      -> MaterialTheme.colorScheme.secondary
    TransitEvent.EventType.LOCATION_UPDATE -> MaterialTheme.colorScheme.outline
}

private fun eventIcon(type: TransitEvent.EventType): ImageVector = when (type) {
    TransitEvent.EventType.DISPATCHED      -> Icons.Default.CheckCircle
    TransitEvent.EventType.RECEIVED        -> Icons.Default.CheckCircle
    TransitEvent.EventType.DELAY_ALERT     -> Icons.Default.Warning
    TransitEvent.EventType.CHECKPOINT      -> Icons.Default.LocationOn
    TransitEvent.EventType.LOCATION_UPDATE -> Icons.Default.LocationOn
}

private fun formatEpoch(epochMillis: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    } catch (_: Exception) {
        epochMillis.toString()
    }
}

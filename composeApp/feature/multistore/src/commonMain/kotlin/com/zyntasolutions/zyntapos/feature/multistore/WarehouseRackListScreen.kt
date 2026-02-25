package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack

/**
 * Warehouse rack list screen (Sprint 18).
 *
 * Displays all racks for a given warehouse with name, description, and
 * optional capacity. A FAB opens the rack create/edit screen.
 *
 * @param state        Current [WarehouseState].
 * @param onIntent     Dispatches intents to [WarehouseViewModel].
 * @param warehouseId  ID of the parent warehouse.
 * @param modifier     Optional modifier.
 */
@Composable
fun WarehouseRackListScreen(
    state: WarehouseState,
    onIntent: (WarehouseIntent) -> Unit,
    warehouseId: String,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(warehouseId) {
        onIntent(WarehouseIntent.LoadRacks(warehouseId))
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(WarehouseIntent.SelectRack(null, warehouseId)) },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rack")
            }
        },
    ) { innerPadding ->
        if (state.isLoading && state.racks.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.racks.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No racks defined.\nTap + to add a rack.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = ZyntaSpacing.md,
                    vertical = ZyntaSpacing.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                items(state.racks, key = { it.id }) { rack ->
                    RackListItem(
                        rack = rack,
                        onClick = { onIntent(WarehouseIntent.SelectRack(rack.id, warehouseId)) },
                        onDelete = { onIntent(WarehouseIntent.RequestDeleteRack(rack)) },
                    )
                }
            }
        }

        // Delete confirmation dialog
        state.showDeleteRackConfirm?.let { rack ->
            AlertDialog(
                onDismissRequest = { onIntent(WarehouseIntent.CancelDeleteRack) },
                title = { Text("Delete Rack") },
                text = { Text("Delete \"${rack.name}\"? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = { onIntent(WarehouseIntent.ConfirmDeleteRack) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(WarehouseIntent.CancelDeleteRack) }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun RackListItem(
    rack: WarehouseRack,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rack.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                rack.description?.let { desc ->
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                rack.capacity?.let { cap ->
                    Text(
                        "Capacity: $cap units",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

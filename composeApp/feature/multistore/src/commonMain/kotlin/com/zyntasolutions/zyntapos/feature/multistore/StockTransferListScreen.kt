package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import org.koin.compose.viewmodel.koinViewModel

/**
 * Lists all stock transfers for a given warehouse with commit/cancel actions
 * for PENDING transfers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferListScreen(
    onNavigateToNewTransfer: (sourceWarehouseId: String?) -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: WarehouseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var pendingCommitId by remember { mutableStateOf<String?>(null) }
    var pendingCancelId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Transfers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToNewTransfer(null) }) {
                Icon(Icons.Default.Add, contentDescription = "New Transfer")
            }
        },
    ) { padding ->
        // MS-2: Build warehouse lookup for name resolution
        val warehouseMap = remember(state.warehouses) { state.warehouses.associateBy { it.id } }

        if (state.transfers.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No transfers found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.transfers, key = { it.id }) { transfer ->
                    StockTransferCard(
                        transfer = transfer,
                        warehouseMap = warehouseMap,
                        onCommit = { pendingCommitId = transfer.id },
                        onCancel = { pendingCancelId = transfer.id },
                    )
                }
            }
        }
    }

    pendingCommitId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingCommitId = null },
            title = { Text("Commit Transfer") },
            text = { Text("Confirm stock transfer? This will adjust product stock quantities.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingCommitId = null
                    viewModel.dispatch(WarehouseIntent.CommitTransfer(id))
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommitId = null }) { Text("Cancel") }
            },
        )
    }

    pendingCancelId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingCancelId = null },
            title = { Text("Cancel Transfer") },
            text = { Text("Cancel this stock transfer? No stock will be moved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCancelId = null
                        viewModel.dispatch(WarehouseIntent.CancelTransfer(id))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Cancel Transfer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancelId = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun StockTransferCard(
    transfer: StockTransfer,
    warehouseMap: Map<String, Warehouse> = emptyMap(),
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    // MS-2: Resolve warehouse names, fall back to truncated ID
    val sourceName = warehouseMap[transfer.sourceWarehouseId]?.name
        ?: transfer.sourceWarehouseId.take(8) + "..."
    val destName = warehouseMap[transfer.destWarehouseId]?.name
        ?: transfer.destWarehouseId.take(8) + "..."

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Qty: ${transfer.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = transfer.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (transfer.status) {
                        StockTransfer.Status.PENDING    -> MaterialTheme.colorScheme.tertiary
                        StockTransfer.Status.APPROVED   -> MaterialTheme.colorScheme.secondary
                        StockTransfer.Status.IN_TRANSIT -> MaterialTheme.colorScheme.primary
                        StockTransfer.Status.RECEIVED   -> MaterialTheme.colorScheme.primary
                        StockTransfer.Status.COMMITTED  -> MaterialTheme.colorScheme.primary
                        StockTransfer.Status.CANCELLED  -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = "$sourceName → $destName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            transfer.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (transfer.status == StockTransfer.Status.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Cancel") }
                    TextButton(onClick = onCommit) { Text("Commit") }
                }
            }
        }
    }
}

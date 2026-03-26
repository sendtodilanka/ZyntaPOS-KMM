package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import org.koin.compose.viewmodel.koinViewModel

// ── Data structures ───────────────────────────────────────────────────────────

/** Identifies a pair of stores involved in inter-store transfers. */
private data class StorePair(
    val sourceStoreId: String,
    val destStoreId: String,
)

/** Transfers for a single store-pair grouped by status. */
private data class StorePairGroup(
    val storePair: StorePair,
    val sourceStoreName: String,
    val destStoreName: String,
    val pending: List<StockTransfer>,
    val approved: List<StockTransfer>,
    val inTransit: List<StockTransfer>,
    val received: List<StockTransfer>,
    val cancelled: List<StockTransfer>,
) {
    val activeCount: Int get() = pending.size + approved.size + inTransit.size
    val allTransfers: List<StockTransfer>
        get() = pending + approved + inTransit + received + cancelled
}

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Store-level transfer dashboard (C1.3).
 *
 * Groups all warehouse stock transfers for the current store by store pair
 * (source store → destination store), showing live status counts for the
 * PENDING → APPROVED → IN_TRANSIT → RECEIVED multi-step IST workflow.
 *
 * Entry point from [WarehouseListScreen] topbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreTransferDashboardScreen(
    onNavigateToNewTransfer: (sourceWarehouseId: String?) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToPickList: (transferId: String) -> Unit = {},
    viewModel: WarehouseViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()

    // Load all transfers when screen is shown
    LaunchedEffect(Unit) {
        viewModel.dispatch(WarehouseIntent.LoadTransfersByStatus(StockTransfer.Status.PENDING))
        viewModel.dispatch(WarehouseIntent.LoadTransfersByStatus(StockTransfer.Status.APPROVED))
        viewModel.dispatch(WarehouseIntent.LoadTransfersByStatus(StockTransfer.Status.IN_TRANSIT))
    }

    // Navigate to pick list screen when generated (P3-B1)
    LaunchedEffect(state.pickList) {
        state.pickList?.let { pickList ->
            onNavigateToPickList(pickList.transferId)
        }
    }

    // Status filter chip selection
    var activeFilter by remember { mutableStateOf<StockTransfer.Status?>(null) }

    // Build warehouse → store lookup
    val warehouseMap: Map<String, Warehouse> = remember(state.warehouses) {
        state.warehouses.associateBy { it.id }
    }

    // Derive storeId from warehouseId; fall back to warehouseId as store key
    fun storeIdFor(warehouseId: String): String =
        warehouseMap[warehouseId]?.storeId ?: warehouseId

    fun storeNameFor(storeId: String): String {
        val warehouse = warehouseMap.values.firstOrNull { it.storeId == storeId }
        return warehouse?.name?.let { "$it (store)" } ?: storeId.take(8) + "…"
    }

    // Aggregate all IST transfers from state
    val allTransfers: List<StockTransfer> = remember(
        state.pendingTransfers, state.approvedTransfers, state.inTransitTransfers,
    ) {
        (state.pendingTransfers + state.approvedTransfers + state.inTransitTransfers).distinctBy { it.id }
    }

    // Group by store pair
    val groups: List<StorePairGroup> = remember(allTransfers, warehouseMap) {
        allTransfers
            .groupBy { t ->
                StorePair(
                    sourceStoreId = storeIdFor(t.sourceWarehouseId),
                    destStoreId   = storeIdFor(t.destWarehouseId),
                )
            }
            .map { (pair, transfers) ->
                StorePairGroup(
                    storePair       = pair,
                    sourceStoreName = storeNameFor(pair.sourceStoreId),
                    destStoreName   = storeNameFor(pair.destStoreId),
                    pending         = transfers.filter { it.status == StockTransfer.Status.PENDING },
                    approved        = transfers.filter { it.status == StockTransfer.Status.APPROVED },
                    inTransit       = transfers.filter { it.status == StockTransfer.Status.IN_TRANSIT },
                    received        = transfers.filter { it.status == StockTransfer.Status.RECEIVED },
                    cancelled       = transfers.filter { it.status == StockTransfer.Status.CANCELLED },
                )
            }
            .sortedByDescending { it.activeCount }
    }

    // Apply status filter
    val visibleGroups: List<StorePairGroup> = remember(groups, activeFilter) {
        if (activeFilter == null) groups
        else groups.filter { g ->
            when (activeFilter) {
                StockTransfer.Status.PENDING    -> g.pending.isNotEmpty()
                StockTransfer.Status.APPROVED   -> g.approved.isNotEmpty()
                StockTransfer.Status.IN_TRANSIT -> g.inTransit.isNotEmpty()
                StockTransfer.Status.RECEIVED   -> g.received.isNotEmpty()
                else                            -> true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.MULTISTORE_TRANSFER_OVERVIEW]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToNewTransfer(null) }) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.MULTISTORE_NEW_TRANSFER])
            }
        },
    ) { padding ->
        if (groups.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    s[StringResource.MULTISTORE_NO_ACTIVE_TRANSFERS],
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    s[StringResource.MULTISTORE_CREATE_TRANSFER_HINT],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // Status filter chips
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = activeFilter == null,
                                onClick = { activeFilter = null },
                                label = { Text(s[StringResource.COMMON_ALL]) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeFilter == StockTransfer.Status.PENDING,
                                onClick = {
                                    activeFilter = if (activeFilter == StockTransfer.Status.PENDING) null
                                    else StockTransfer.Status.PENDING
                                },
                                label = { Text(s[StringResource.MULTISTORE_PENDING]) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeFilter == StockTransfer.Status.APPROVED,
                                onClick = {
                                    activeFilter = if (activeFilter == StockTransfer.Status.APPROVED) null
                                    else StockTransfer.Status.APPROVED
                                },
                                label = { Text(s[StringResource.MULTISTORE_APPROVED]) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeFilter == StockTransfer.Status.IN_TRANSIT,
                                onClick = {
                                    activeFilter = if (activeFilter == StockTransfer.Status.IN_TRANSIT) null
                                    else StockTransfer.Status.IN_TRANSIT
                                },
                                label = { Text(s[StringResource.MULTISTORE_IN_TRANSIT]) },
                            )
                        }
                    }
                }

                // Group summary cards
                items(visibleGroups, key = { "${it.storePair.sourceStoreId}→${it.storePair.destStoreId}" }) { group ->
                    StorePairGroupCard(
                        group = group,
                        onApprove = { id -> viewModel.dispatch(WarehouseIntent.ApproveTransfer(id)) },
                        onDispatch = { id -> viewModel.dispatch(WarehouseIntent.DispatchTransfer(id)) },
                        onReceive  = { id -> viewModel.dispatch(WarehouseIntent.ReceiveTransfer(id)) },
                        onGeneratePickList = { id -> viewModel.dispatch(WarehouseIntent.GeneratePickList(id)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }

                item { Spacer(Modifier.height(88.dp)) } // FAB clearance
            }
        }
    }
}

// ── Group card ────────────────────────────────────────────────────────────────

@Composable
private fun StorePairGroupCard(
    group: StorePairGroup,
    onApprove: (String) -> Unit,
    onDispatch: (String) -> Unit,
    onReceive:  (String) -> Unit,
    onGeneratePickList: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Store pair header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = group.sourceStoreName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = group.destStoreName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
                if (group.activeCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("${group.activeCount}")
                    }
                }
            }

            // Status count row
            if (group.allTransfers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (group.pending.isNotEmpty()) {
                        StatusCount("Pending", group.pending.size, MaterialTheme.colorScheme.tertiary)
                    }
                    if (group.approved.isNotEmpty()) {
                        StatusCount("Approved", group.approved.size, MaterialTheme.colorScheme.secondary)
                    }
                    if (group.inTransit.isNotEmpty()) {
                        StatusCount("In Transit", group.inTransit.size, MaterialTheme.colorScheme.primary)
                    }
                    if (group.received.isNotEmpty()) {
                        StatusCount("Received", group.received.size, MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Individual transfer rows requiring action
            val actionableTransfers = group.pending + group.approved + group.inTransit
            if (actionableTransfers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                actionableTransfers.take(3).forEach { transfer ->
                    TransferActionRow(
                        transfer = transfer,
                        onApprove = { onApprove(transfer.id) },
                        onDispatch = { onDispatch(transfer.id) },
                        onReceive  = { onReceive(transfer.id) },
                        onGeneratePickList = { onGeneratePickList(transfer.id) },
                    )
                }

                if (actionableTransfers.size > 3) {
                    Text(
                        text = "+${actionableTransfers.size - 3} more transfers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ── Transfer action row ───────────────────────────────────────────────────────

@Composable
private fun TransferActionRow(
    transfer: StockTransfer,
    onApprove: () -> Unit,
    onDispatch: () -> Unit,
    onReceive:  () -> Unit,
    onGeneratePickList: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Qty: ${transfer.quantity}",
                style = MaterialTheme.typography.bodySmall,
            )
            transfer.notes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // Pick List action — only for APPROVED transfers (P3-B1)
        if (transfer.status == StockTransfer.Status.APPROVED) {
            Text(
                text = "Pick List",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onGeneratePickList() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        val (actionLabel, actionColor) = when (transfer.status) {
            StockTransfer.Status.PENDING    -> "Approve"  to MaterialTheme.colorScheme.secondary
            StockTransfer.Status.APPROVED   -> "Dispatch" to MaterialTheme.colorScheme.primary
            StockTransfer.Status.IN_TRANSIT -> "Receive"  to MaterialTheme.colorScheme.tertiary
            else                            -> null
        } ?: return

        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = actionColor,
            modifier = Modifier
                .clickable {
                    when (transfer.status) {
                        StockTransfer.Status.PENDING    -> onApprove()
                        StockTransfer.Status.APPROVED   -> onDispatch()
                        StockTransfer.Status.IN_TRANSIT -> onReceive()
                        else                            -> Unit
                    }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── Helper ────────────────────────────────────────────────────────────────────

@Composable
private fun StatusCount(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

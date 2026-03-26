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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import org.koin.compose.viewmodel.koinViewModel

/**
 * Lists all warehouses for the current store with quick navigation to
 * transfers and warehouse detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseListScreen(
    onNavigateToDetail: (warehouseId: String?) -> Unit,
    onNavigateToTransfers: () -> Unit,
    viewModel: WarehouseViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.dispatch(WarehouseIntent.LoadWarehouses)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.MULTISTORE_WAREHOUSES]) },
                actions = {
                    BadgedBox(
                        badge = {
                            if (state.pendingTransfers.isNotEmpty()) {
                                Badge { Text("${state.pendingTransfers.size}") }
                            }
                        },
                    ) {
                        IconButton(onClick = onNavigateToTransfers) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = s[StringResource.MULTISTORE_TRANSFERS])
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToDetail(null) }) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.MULTISTORE_NEW_WAREHOUSE])
            }
        },
    ) { padding ->
        if (state.warehouses.isEmpty() && !state.isLoading) {
            ZyntaEmptyState(
                title = s[StringResource.MULTISTORE_NO_WAREHOUSES],
                icon = Icons.Default.Inventory2,
                subtitle = s[StringResource.MULTISTORE_TAP_ADD_WAREHOUSE],
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.warehouses, key = { it.id }) { warehouse ->
                    WarehouseCard(
                        warehouse = warehouse,
                        onClick = { onNavigateToDetail(warehouse.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarehouseCard(
    warehouse: Warehouse,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(warehouse.name, style = MaterialTheme.typography.titleMedium)
                    if (warehouse.isDefault) {
                        Badge { Text(s[StringResource.COMMON_DEFAULT]) }
                    }
                    if (!warehouse.isActive) {
                        Text(
                            s[StringResource.COMMON_INACTIVE],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                warehouse.address?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

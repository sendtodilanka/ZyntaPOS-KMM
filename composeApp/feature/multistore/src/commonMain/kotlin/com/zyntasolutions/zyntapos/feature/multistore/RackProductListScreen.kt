package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.RackProduct

/**
 * Rack-product management screen — lists products stored in a specific rack (C1.2).
 *
 * Displays each [RackProduct] with its bin location and quantity. A FAB opens
 * the [RackProductDetailScreen] to add a product. Row delete icon removes the mapping.
 *
 * @param state     Current [WarehouseState].
 * @param onIntent  Dispatches intents to [WarehouseViewModel].
 * @param rackId    ID of the rack being managed.
 * @param modifier  Optional modifier.
 */
@Composable
fun RackProductListScreen(
    state: WarehouseState,
    onIntent: (WarehouseIntent) -> Unit,
    rackId: String,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    LaunchedEffect(rackId) {
        onIntent(WarehouseIntent.LoadRackProducts(rackId))
    }

    // Confirm-delete dialog
    state.showDeleteRackProductConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { onIntent(WarehouseIntent.CancelDeleteRackProduct) },
            title = { Text(s[StringResource.MULTISTORE_REMOVE_FROM_RACK_TITLE]) },
            text = {
                Text(s[StringResource.MULTISTORE_REMOVE_FROM_RACK_MSG_FORMAT, entry.productName ?: entry.productId])
            },
            confirmButton = {
                TextButton(onClick = { onIntent(WarehouseIntent.ConfirmDeleteRackProduct) }) {
                    Text(s[StringResource.COMMON_REMOVE], color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(WarehouseIntent.CancelDeleteRackProduct) }) {
                    Text(s[StringResource.COMMON_CANCEL])
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(WarehouseIntent.OpenRackProductEntry(rackId, null)) },
            ) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.MULTISTORE_ADD_PRODUCT_CD])
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading && state.rackProducts.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.rackProducts.isEmpty() -> ZyntaEmptyState(
                title = s[StringResource.MULTISTORE_NO_PRODUCTS_IN_RACK],
                icon = Icons.Default.Inventory2,
                subtitle = s[StringResource.MULTISTORE_TAP_ASSIGN_PRODUCT],
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(state.rackProducts, key = { it.id }) { entry ->
                    RackProductRow(
                        entry = entry,
                        onClick = { onIntent(WarehouseIntent.OpenRackProductEntry(rackId, entry.productId)) },
                        onDelete = { onIntent(WarehouseIntent.RequestDeleteRackProduct(entry)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RackProductRow(
    entry: RackProduct,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.productName ?: entry.productId,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (!entry.productSku.isNullOrBlank()) {
                Text(
                    text = s[StringResource.MULTISTORE_SKU_FORMAT, entry.productSku!!],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!entry.binLocation.isNullOrBlank()) {
                Text(
                    text = s[StringResource.MULTISTORE_BIN_FORMAT, entry.binLocation!!],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(ZyntaSpacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            val qtyText = if (entry.quantity == entry.quantity.toLong().toDouble()) {
                entry.quantity.toLong().toString()
            } else entry.quantity.toString()
            Text(
                text = qtyText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = s[StringResource.MULTISTORE_UNITS],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = s[StringResource.MULTISTORE_REMOVE_FROM_RACK_CD],
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.PickList
import com.zyntasolutions.zyntapos.domain.model.PickListItem
import org.koin.compose.viewmodel.koinViewModel

/**
 * Displays a formatted pick list for an approved inter-store transfer (P3-B1).
 *
 * Shows transfer details (source/destination warehouses), a table of items to
 * pick sorted by rack location, and a "Print" action in the top bar to send
 * the pick list to a connected ESC/POS thermal printer.
 *
 * Entry point: "Generate Pick List" button on transfer action rows when
 * transfer status = APPROVED.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickListScreen(
    transferId: String? = null,
    onNavigateUp: () -> Unit,
    viewModel: WarehouseViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val pickList = state.pickList

    // Re-generate pick list if state was lost (e.g., process death)
    LaunchedEffect(transferId) {
        if (pickList == null && transferId != null) {
            viewModel.dispatch(WarehouseIntent.GeneratePickList(transferId))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.MULTISTORE_PICK_LIST]) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.dispatch(WarehouseIntent.DismissPickList)
                        onNavigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    if (pickList != null) {
                        IconButton(
                            onClick = { viewModel.dispatch(WarehouseIntent.PrintPickList) },
                            enabled = !state.isLoading,
                        ) {
                            Icon(Icons.Default.Print, contentDescription = s[StringResource.MULTISTORE_PRINT_PICK_LIST_CD])
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (pickList == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.isLoading) {
                    Text(
                        s[StringResource.MULTISTORE_GENERATING_PICK_LIST],
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ZyntaEmptyState(
                        title = s[StringResource.MULTISTORE_NO_PICK_LIST],
                        icon = Icons.Default.Print,
                    )
                }
            }
        } else {
            PickListContent(
                pickList = pickList,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun PickListContent(
    pickList: PickList,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Transfer header card
        item {
            Spacer(Modifier.height(8.dp))
            PickListHeaderCard(pickList)
            Spacer(Modifier.height(16.dp))
        }

        // Column header
        item {
            PickListColumnHeader()
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        // Item rows
        itemsIndexed(pickList.items, key = { _, item -> item.productId }) { index, item ->
            PickListItemRow(index = index + 1, item = item)
            if (index < pickList.items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }

        // Footer
        item {
            val s = LocalStrings.current
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    s[StringResource.MULTISTORE_TOTAL_ITEMS],
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${pickList.items.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(88.dp)) // bottom clearance
        }
    }
}

@Composable
private fun PickListHeaderCard(pickList: PickList) {
    val s = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = s[StringResource.MULTISTORE_PICK_LIST_HEADER],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            PickListHeaderRow(s[StringResource.MULTISTORE_TRANSFER_LABEL], pickList.transferId.take(20))
            PickListHeaderRow(s[StringResource.MULTISTORE_FROM_LABEL], pickList.sourceStoreName)
            PickListHeaderRow(s[StringResource.MULTISTORE_TO_LABEL], pickList.destinationStoreName)
            PickListHeaderRow(s[StringResource.COMMON_DATE], formatPickListDate(pickList.generatedAt))

            pickList.notes?.let { notes ->
                PickListHeaderRow(s[StringResource.MULTISTORE_NOTES], notes)
            }
        }
    }
}

@Composable
private fun PickListHeaderRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PickListColumnHeader() {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = s[StringResource.COMMON_NUMBER_SYMBOL],
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = s[StringResource.MULTISTORE_PRODUCT],
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = s[StringResource.MULTISTORE_QTY],
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(50.dp),
            textAlign = TextAlign.End,
        )
        Text(
            text = s[StringResource.MULTISTORE_RACK_LABEL],
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
        )
        Text(
            text = s[StringResource.MULTISTORE_BIN_LABEL],
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PickListItemRow(index: Int, item: PickListItem) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.productName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (item.sku.isNotBlank()) {
                Text(
                    text = s[StringResource.MULTISTORE_SKU_FORMAT, item.sku],
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = formatPickListQuantity(item.quantity),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(50.dp),
            textAlign = TextAlign.End,
        )
        Text(
            text = item.rackLocation ?: "-",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
        )
        Text(
            text = item.binLocation ?: "-",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatPickListQuantity(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString()
    else "%.1f".format(qty)

private fun formatPickListDate(instant: kotlinx.datetime.Instant): String =
    instant.toString().take(16).replace('T', ' ')

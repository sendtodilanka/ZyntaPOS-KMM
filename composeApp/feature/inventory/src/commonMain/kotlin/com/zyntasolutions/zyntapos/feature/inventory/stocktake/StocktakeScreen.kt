package com.zyntasolutions.zyntapos.feature.inventory.stocktake

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSnackbarHost
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.domain.model.StocktakeCount
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stocktake (physical inventory count) screen.
 *
 * Allows staff to start a session, scan or manually enter product counts,
 * view variances vs. system quantities, and complete or cancel the session.
 *
 * @param onNavigateBack Callback to dismiss/navigate away from this screen.
 * @param viewModel      Koin-provided [StocktakeViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocktakeScreen(
    onNavigateBack: () -> Unit,
    viewModel: StocktakeViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCompleteDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is StocktakeEffect.ScanSuccess   ->
                    snackbarHostState.showSnackbar(s[StringResource.INVENTORY_SCAN_SUCCESS_MSG, effect.productName, effect.qty])
                is StocktakeEffect.ScanNotFound  ->
                    snackbarHostState.showSnackbar(s[StringResource.INVENTORY_SCAN_NOT_FOUND_MSG, effect.barcode])
                is StocktakeEffect.StocktakeCompleted -> {
                    val msg = if (effect.varianceCount == 0)
                        s[StringResource.INVENTORY_STOCKTAKE_COMPLETE_NO_VARIANCES]
                    else
                        s[StringResource.INVENTORY_STOCKTAKE_COMPLETE_VARIANCES, effect.varianceCount]
                    snackbarHostState.showSnackbar(msg)
                    onNavigateBack()
                }
                is StocktakeEffect.SessionCancelled -> onNavigateBack()
                is StocktakeEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.INVENTORY_STOCKTAKE]) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isInProgress) showCancelDialog = true else onNavigateBack()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = s[StringResource.COMMON_CLOSE])
                    }
                },
                actions = {
                    // Scanner toggle
                    if (state.isInProgress) {
                        IconToggleButton(
                            checked = state.isScanning,
                            onCheckedChange = { viewModel.dispatch(StocktakeIntent.SetScannerActive(it)) },
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.isScanning) Badge()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = if (state.isScanning) s[StringResource.INVENTORY_SCANNER_ACTIVE] else s[StringResource.INVENTORY_ACTIVATE_SCANNER],
                                    tint = if (state.isScanning)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { ZyntaSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (!state.isInProgress) {
                // ── Pre-session: Start prompt ──────────────────────────────────
                StartSessionPanel(
                    isStarting = state.isStarting,
                    onStart = { viewModel.dispatch(StocktakeIntent.StartSession) },
                )
            } else {
                // ── Active session ─────────────────────────────────────────────
                Spacer(Modifier.height(8.dp))

                // Session summary header
                SessionSummaryRow(counts = state.counts)

                Spacer(Modifier.height(8.dp))

                // Last scanned feedback chip
                state.lastScannedBarcode?.let { barcode ->
                    LastScannedChip(barcode = barcode)
                    Spacer(Modifier.height(8.dp))
                }

                // Count list
                if (state.counts.isEmpty()) {
                    EmptyCountsPlaceholder()
                } else {
                    CountList(
                        counts = state.counts,
                        modifier = Modifier.weight(1f),
                        onAdjust = { productId, qty ->
                            viewModel.dispatch(StocktakeIntent.ManualAdjustCount(productId, qty))
                        },
                        onRemove = { productId ->
                            viewModel.dispatch(StocktakeIntent.RemoveCount(productId))
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Complete / Cancel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(s[StringResource.COMMON_CANCEL])
                    }
                    Button(
                        onClick = { showCompleteDialog = true },
                        modifier = Modifier.weight(2f),
                        enabled = !state.isCompleting && state.counts.isNotEmpty(),
                    ) {
                        if (state.isCompleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isCompleting) s[StringResource.INVENTORY_COMPLETING] else s[StringResource.INVENTORY_COMPLETE_STOCKTAKE])
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Error banner
                state.error?.let { errorMsg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { viewModel.dispatch(StocktakeIntent.DismissError) },
                            ) {
                                Text(s[StringResource.COMMON_DISMISS])
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // ── Complete confirmation dialog ──────────────────────────────────────────
    if (showCompleteDialog) {
        val varianceCount = state.counts.count { (it.computedVariance ?: 0) != 0 }
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text(s[StringResource.INVENTORY_COMPLETE_STOCKTAKE_TITLE]) },
            text = {
                Column {
                    Text(s[StringResource.INVENTORY_COMPLETE_STOCKTAKE_MESSAGE])
                    if (varianceCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = s[StringResource.INVENTORY_STOCKTAKE_VARIANCE_DIALOG_MSG, varianceCount],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCompleteDialog = false
                    viewModel.dispatch(StocktakeIntent.CompleteStocktake)
                }) { Text(s[StringResource.INVENTORY_COMPLETE]) }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) { Text(s[StringResource.INVENTORY_REVIEW]) }
            },
        )
    }

    // ── Cancel confirmation dialog ────────────────────────────────────────────
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(s[StringResource.INVENTORY_CANCEL_STOCKTAKE_TITLE]) },
            text = { Text(s[StringResource.INVENTORY_CANCEL_STOCKTAKE_MESSAGE]) },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        viewModel.dispatch(StocktakeIntent.CancelStocktake)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(s[StringResource.INVENTORY_DISCARD_AND_CANCEL]) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text(s[StringResource.INVENTORY_KEEP_COUNTING]) }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StartSessionPanel(
    isStarting: Boolean,
    onStart: () -> Unit,
) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Inventory,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = s[StringResource.INVENTORY_START_STOCKTAKE],
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = s[StringResource.INVENTORY_START_STOCKTAKE_SUBTITLE],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onStart,
                enabled = !isStarting,
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(s[StringResource.INVENTORY_STARTING])
                } else {
                    Text(s[StringResource.INVENTORY_START_NEW_SESSION])
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryRow(counts: List<StocktakeCount>) {
    val s = LocalStrings.current
    val totalCounted = counts.size
    val withVariance = counts.count { (it.computedVariance ?: 0) != 0 }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryChip(label = s[StringResource.INVENTORY_SCANNED], value = "$totalCounted", modifier = Modifier.weight(1f))
        SummaryChip(
            label = s[StringResource.INVENTORY_VARIANCES],
            value = "$withVariance",
            isWarning = withVariance > 0,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isWarning)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LastScannedChip(barcode: String) {
    val s = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = s[StringResource.INVENTORY_LAST_SCAN, barcode],
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyCountsPlaceholder() {
    val s = LocalStrings.current
    ZyntaEmptyState(
        title = s[StringResource.INVENTORY_SCAN_TO_BEGIN],
        icon = Icons.Default.QrCodeScanner,
        modifier = Modifier.fillMaxWidth().height(200.dp),
    )
}

@Composable
private fun CountList(
    counts: List<StocktakeCount>,
    modifier: Modifier = Modifier,
    onAdjust: (productId: String, qty: Int) -> Unit,
    onRemove: (productId: String) -> Unit,
) {
    val s = LocalStrings.current
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = s[StringResource.INVENTORY_PRODUCT],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(3f),
                )
                Text(
                    text = s[StringResource.INVENTORY_SYSTEM],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = s[StringResource.INVENTORY_COUNTED],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.5f),
                )
                Text(
                    text = s[StringResource.INVENTORY_VAR],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.8f),
                )
                Spacer(Modifier.width(40.dp))
            }
            HorizontalDivider()
        }

        items(counts, key = { it.productId }) { count ->
            CountRow(
                count = count,
                onAdjust = { qty -> onAdjust(count.productId, qty) },
                onRemove = { onRemove(count.productId) },
            )
        }
    }
}

@Composable
private fun CountRow(
    count: StocktakeCount,
    onAdjust: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var editingQty by remember(count.productId) { mutableStateOf(count.countedQty?.toString() ?: "") }
    val variance = count.computedVariance

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                variance != null && variance > 0 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                variance != null && variance < 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else                             -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Product name
            Column(modifier = Modifier.weight(3f)) {
                Text(
                    text = count.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = count.barcode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // System qty
            Text(
                text = "${count.systemQty}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )

            // Counted qty — editable field
            OutlinedTextField(
                value = editingQty,
                onValueChange = { v ->
                    editingQty = v
                    v.toIntOrNull()?.let { qty -> if (qty >= 0) onAdjust(qty) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1.5f),
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            // Variance badge
            val varianceText = variance?.let { v ->
                when {
                    v > 0 -> "+$v"
                    v < 0 -> "$v"
                    else  -> "0"
                }
            } ?: "—"
            val varianceColor = when {
                variance == null     -> MaterialTheme.colorScheme.onSurfaceVariant
                variance > 0         -> MaterialTheme.colorScheme.tertiary
                variance < 0         -> MaterialTheme.colorScheme.error
                else                 -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = varianceText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (variance != null && variance != 0) FontWeight.Bold else FontWeight.Normal,
                color = varianceColor,
                modifier = Modifier.weight(0.8f),
            )

            // Remove button
            val s = LocalStrings.current
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = s[StringResource.COMMON_REMOVE],
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

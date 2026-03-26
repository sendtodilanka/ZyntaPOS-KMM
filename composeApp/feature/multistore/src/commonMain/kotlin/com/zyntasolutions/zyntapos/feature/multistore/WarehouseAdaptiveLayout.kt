package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize

/**
 * 3-pane responsive layout for warehouse tablet UI.
 *
 * - **COMPACT** (phone): Single pane — shows [listPane] only.
 * - **MEDIUM** (tablet portrait): 2-pane master/detail — [listPane] (40%) + [detailPane] (60%).
 * - **EXPANDED** (tablet landscape / desktop): 3-pane — [listPane] (30%) + [detailPane] (40%) + [actionPane] (30%).
 *
 * Each pane is a composable lambda provided by the caller, allowing full flexibility
 * in which warehouse screens are displayed.
 *
 * @param listPane Left pane — typically [WarehouseListScreen] or [WarehouseStockScreen].
 * @param detailPane Center pane — detail view for selected warehouse/product.
 * @param actionPane Right pane — action/form pane (stock entry, transfer creation).
 * @param hasDetail Whether a detail item is selected (controls 2-pane display on MEDIUM).
 * @param hasAction Whether an action is active (controls 3rd pane display on EXPANDED).
 */
@Composable
fun WarehouseAdaptiveLayout(
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    actionPane: @Composable () -> Unit,
    hasDetail: Boolean = false,
    hasAction: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val windowSize = currentWindowSize()

    when (windowSize) {
        WindowSize.COMPACT -> {
            // Single pane: show detail if selected, otherwise list
            Box(modifier = modifier.fillMaxSize()) {
                if (hasDetail) {
                    detailPane()
                } else {
                    listPane()
                }
            }
        }

        WindowSize.MEDIUM -> {
            // 2-pane: list (40%) + detail (60%)
            Row(modifier = modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    listPane()
                }
                VerticalDivider()
                Surface(
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    if (hasDetail) {
                        detailPane()
                    } else {
                        WarehouseEmptyDetailPlaceholder()
                    }
                }
            }
        }

        WindowSize.EXPANDED -> {
            // 3-pane: list (30%) + detail (40%) + actions (30%)
            Row(modifier = modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.weight(0.3f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    listPane()
                }
                VerticalDivider()
                Surface(
                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    if (hasDetail) {
                        detailPane()
                    } else {
                        WarehouseEmptyDetailPlaceholder()
                    }
                }
                VerticalDivider()
                Surface(
                    modifier = Modifier.weight(0.3f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    if (hasAction) {
                        actionPane()
                    } else {
                        WarehouseEmptyActionPlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
private fun WarehouseEmptyDetailPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = "Select a warehouse or product to view details",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WarehouseEmptyActionPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = "Actions will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

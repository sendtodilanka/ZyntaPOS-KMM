package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

/**
 * Persistent low-stock alert banner for the Inventory home screen.
 * Sprint 19, Step 10.1.13.
 *
 * Displays a prominent warning banner when one or more products have a stock
 * quantity below their configured `minStockQty`. The banner:
 * - Shows the total count of low-stock products via a [Badge].
 * - Provides a clickable "View" action that invokes [onNavigateToLowStockList].
 * - Can be dismissed for the current session (re-appears on next app launch or
 *   when the composable re-enters composition).
 * - Animates in/out with [AnimatedVisibility].
 *
 * ### Usage
 * Place this composable at the **top** of the Inventory home screen, above
 * the product list/grid:
 * ```kotlin
 * LowStockAlertBanner(
 *     lowStockCount = state.lowStockCount,
 *     onNavigateToLowStockList = { onIntent(InventoryIntent.SetStockFilter(StockFilter.LOW_STOCK)) }
 * )
 * ```
 *
 * @param lowStockCount             Number of products below their minimum stock threshold.
 *                                  Pass 0 to hide the banner.
 * @param onNavigateToLowStockList  Called when the user taps the banner or the
 *                                  "View" action. Typically applies [StockFilter.LOW_STOCK].
 * @param modifier                  Optional root modifier.
 */
@Composable
fun LowStockAlertBanner(
    lowStockCount: Int,
    onNavigateToLowStockList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dismissed state: banner can be dismissed per composition lifetime
    var dismissed by remember { mutableStateOf(false) }

    // Re-surface banner whenever count changes from 0 to non-zero
    val isVisible = lowStockCount > 0 && !dismissed
    LaunchedEffect(lowStockCount) {
        if (lowStockCount > 0) dismissed = false
    }

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToLowStockList() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Low stock warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(ZyntaSpacing.sm))

                // Message + count badge
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Low Stock Alert",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(ZyntaSpacing.xs))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ) {
                            Text(
                                text = lowStockCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        text = if (lowStockCount == 1)
                            "1 product is below its minimum stock level"
                        else
                            "$lowStockCount products are below their minimum stock levels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                // "View" action
                TextButton(
                    onClick = onNavigateToLowStockList,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("View", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }

                // Dismiss icon
                IconButton(
                    onClick = { dismissed = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss alert",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

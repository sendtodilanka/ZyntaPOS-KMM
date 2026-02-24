package com.zyntasolutions.zyntapos.feature.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Reports home screen — tile grid navigating to all report screens.
 *
 * Each tile shows:
 * - Icon
 * - Title
 * - Last-generated timestamp (or "Not yet generated")
 *
 * @param onNavigateToSalesReport    Sales Report tile tap handler.
 * @param onNavigateToStockReport    Stock Report tile tap handler.
 * @param onNavigateToCustomerReport Customer Report tile tap handler.
 * @param onNavigateToExpenseReport  Expense Report tile tap handler.
 * @param onNavigateUp               Back navigation handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHomeScreen(
    onNavigateToSalesReport: () -> Unit,
    onNavigateToStockReport: () -> Unit,
    onNavigateToCustomerReport: () -> Unit,
    onNavigateToExpenseReport: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val homeState = state.reportsHome

    ZyntaPageScaffold(
        title = "Reports",
        onNavigateBack = onNavigateUp,
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ReportTile(
                    icon = Icons.Default.Assessment,
                    title = "Sales Report",
                    lastGeneratedAt = homeState.lastSalesReportAt,
                    onClick = onNavigateToSalesReport,
                )
            }
            item {
                ReportTile(
                    icon = Icons.Default.Inventory2,
                    title = "Stock Report",
                    lastGeneratedAt = homeState.lastStockReportAt,
                    onClick = onNavigateToStockReport,
                )
            }
            item {
                ReportTile(
                    icon = Icons.Default.Group,
                    title = "Customer Report",
                    lastGeneratedAt = homeState.lastCustomerReportAt,
                    onClick = onNavigateToCustomerReport,
                )
            }
            item {
                ReportTile(
                    icon = Icons.Default.Receipt,
                    title = "Expense Report",
                    lastGeneratedAt = homeState.lastExpenseReportAt,
                    onClick = onNavigateToExpenseReport,
                )
            }
        }
    }
}

// ─── Internal Composables ────────────────────────────────────────────────────

/**
 * Single report tile card.
 *
 * @param icon            Material icon for the report type.
 * @param title           Report display name.
 * @param lastGeneratedAt UTC timestamp of the last successful report load, or null.
 * @param onClick         Tap handler.
 */
@Composable
private fun ReportTile(
    icon: ImageVector,
    title: String,
    lastGeneratedAt: Instant?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (lastGeneratedAt != null) {
                    val local = lastGeneratedAt.toLocalDateTime(TimeZone.currentSystemDefault())
                    "Last: ${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
                } else {
                    "Not yet generated"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

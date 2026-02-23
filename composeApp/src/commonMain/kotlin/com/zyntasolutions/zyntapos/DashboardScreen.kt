package com.zyntasolutions.zyntapos

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/**
 * Dashboard screen matching the UI design — shows summary stats,
 * recent orders, and quick navigation actions.
 */
@Composable
fun DashboardScreen(
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
) {
    val db: ZyntaDatabase = koinInject()
    val authRepository: AuthRepository = koinInject()
    val currencyFormatter: CurrencyFormatter = koinInject()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var todaysSales by remember { mutableStateOf(0.0) }
    var totalOrders by remember { mutableStateOf(0L) }
    var lowStockCount by remember { mutableStateOf(0L) }
    var activeRegisters by remember { mutableStateOf(0L) }
    var recentOrders by remember { mutableStateOf<List<RecentOrder>>(emptyList()) }

    LaunchedEffect(Unit) {
        currentUser = authRepository.getSession().first()
        withContext(Dispatchers.Default) {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val todayStart = now.toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()

            // Today's sales
            val allOrders = db.ordersQueries.getAllOrders().executeAsList()
            val todayOrders = allOrders.filter {
                it.status == "COMPLETED" && it.created_at >= todayStart
            }
            todaysSales = todayOrders.sumOf { it.total }
            totalOrders = todayOrders.size.toLong()

            // Low stock
            lowStockCount = db.productsQueries.getLowStockProducts().executeAsList().size.toLong()

            // Active registers
            activeRegisters = db.registersQueries.getActiveSession().executeAsList().size.toLong()

            // Recent orders (last 10)
            recentOrders = allOrders
                .filter { it.status == "COMPLETED" }
                .sortedByDescending { it.created_at }
                .take(10)
                .map { order ->
                    RecentOrder(
                        orderNumber = order.order_number,
                        total = order.total,
                        method = order.payment_method,
                        timestamp = order.created_at,
                    )
                }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Welcome Header ──────────────────────────────────────
        item {
            Column {
                Text(
                    text = "Welcome back,",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = currentUser?.name ?: "Manager",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ── Stat Cards Row ──────────────────────────────────────
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    StatCard(
                        icon = Icons.Default.AttachMoney,
                        label = "Today's Sales",
                        value = currencyFormatter.format(todaysSales),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Default.Receipt,
                        label = "Total Orders",
                        value = totalOrders.toString(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Default.Warning,
                        label = "Low Stock",
                        value = "$lowStockCount items",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Default.PointOfSale,
                        label = "Active Registers",
                        value = activeRegisters.toString(),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        // ── Quick Actions ───────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickActionCard(
                    label = "Open POS",
                    icon = Icons.Default.PointOfSale,
                    onClick = onNavigateToPos,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    label = "Register",
                    icon = Icons.Default.Inventory2,
                    onClick = onNavigateToRegister,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    label = "Reports",
                    icon = Icons.Default.Receipt,
                    onClick = onNavigateToReports,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Recent Activity ─────────────────────────────────────
        item {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (recentOrders.isEmpty()) {
            item {
                Text(
                    text = "No recent orders yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(recentOrders, key = { it.orderNumber }) { order ->
                RecentOrderRow(order = order, currencyFormatter = currencyFormatter)
                HorizontalDivider()
            }
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(160.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RecentOrderRow(
    order: RecentOrder,
    currencyFormatter: CurrencyFormatter,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = order.orderNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = order.method,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = currencyFormatter.format(order.total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class RecentOrder(
    val orderNumber: String,
    val total: Double,
    val method: String,
    val timestamp: Long,
)

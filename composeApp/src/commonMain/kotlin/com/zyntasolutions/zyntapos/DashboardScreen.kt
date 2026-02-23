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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/**
 * Home dashboard screen — KPI stat cards, quick actions, alert cards,
 * and recent orders list. Adaptive layout per UI/UX Master Blueprint:
 *
 * - **Expanded**: Two-column (60% KPIs/Orders, 40% Quick Actions/Alerts)
 * - **Medium/Compact**: Single-column scrollable
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
    val windowSize = currentWindowSize()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var todaysSales by remember { mutableStateOf(0.0) }
    var totalOrders by remember { mutableStateOf(0L) }
    var lowStockCount by remember { mutableStateOf(0L) }
    var lowStockNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeRegisters by remember { mutableStateOf(0L) }
    var recentOrders by remember { mutableStateOf<List<RecentOrder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        currentUser = authRepository.getSession().first()
        withContext(Dispatchers.Default) {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val todayStart = now.toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()

            val allOrders = db.ordersQueries.getAllOrders().executeAsList()
            val todayOrders = allOrders.filter {
                it.status == "COMPLETED" && it.created_at >= todayStart
            }
            todaysSales = todayOrders.sumOf { it.total }
            totalOrders = todayOrders.size.toLong()

            val lowStockProducts = db.productsQueries.getLowStockProducts().executeAsList()
            lowStockCount = lowStockProducts.size.toLong()
            lowStockNames = lowStockProducts.take(5).map { it.name }

            activeRegisters = db.registersQueries.getActiveSession().executeAsList().size.toLong()

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
            isLoading = false
        }
    }

    if (isLoading) {
        ZyntaLoadingOverlay(isLoading = true)
        return
    }

    if (windowSize == WindowSize.EXPANDED) {
        // ── Expanded: two-column layout ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxSize().padding(ZyntaSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
        ) {
            // Left pane (60%): KPIs + Recent Orders
            LazyColumn(
                modifier = Modifier.weight(0.6f),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                item { WelcomeHeader(currentUser) }
                item { KpiCardGrid(todaysSales, totalOrders, lowStockCount, activeRegisters, currencyFormatter) }
                item { SectionTitle("Recent Orders") }
                if (recentOrders.isEmpty()) {
                    item { EmptyRecentOrders() }
                } else {
                    items(recentOrders, key = { it.orderNumber }) { order ->
                        RecentOrderRow(order = order, currencyFormatter = currencyFormatter)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // Right pane (40%): Quick Actions + Alerts
            LazyColumn(
                modifier = Modifier.weight(0.4f),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                item { SectionTitle("Quick Actions") }
                item {
                    QuickActionGrid(
                        onNavigateToPos = onNavigateToPos,
                        onNavigateToRegister = onNavigateToRegister,
                        onNavigateToReports = onNavigateToReports,
                    )
                }
                if (lowStockCount > 0) {
                    item { SectionTitle("Alerts") }
                    item { LowStockAlertCard(lowStockCount, lowStockNames) }
                }
                if (activeRegisters == 0L) {
                    if (lowStockCount == 0L) item { SectionTitle("Alerts") }
                    item { NoRegisterAlertCard() }
                }
            }
        }
    } else {
        // ── Compact/Medium: single-column layout ────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            item { WelcomeHeader(currentUser) }

            // Stat cards (horizontal scroll on compact)
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

            // Quick actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                    QuickActionCard("Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                    QuickActionCard("Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
                }
            }

            // Alerts
            if (lowStockCount > 0) {
                item { LowStockAlertCard(lowStockCount, lowStockNames) }
            }
            if (activeRegisters == 0L) {
                item { NoRegisterAlertCard() }
            }

            // Recent activity
            item { SectionTitle("Recent Activity") }
            if (recentOrders.isEmpty()) {
                item { EmptyRecentOrders() }
            } else {
                items(recentOrders, key = { it.orderNumber }) { order ->
                    RecentOrderRow(order = order, currencyFormatter = currencyFormatter)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun WelcomeHeader(currentUser: User?) {
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

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyRecentOrders() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Text(
                text = "No orders yet today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Complete your first sale to see activity here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun KpiCardGrid(
    todaysSales: Double,
    totalOrders: Long,
    lowStockCount: Long,
    activeRegisters: Long,
    currencyFormatter: CurrencyFormatter,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        item {
            StatCard(
                icon = Icons.Default.AttachMoney,
                label = "Today's Sales",
                value = currencyFormatter.format(todaysSales),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StatCard(
                icon = Icons.Default.Receipt,
                label = "Orders",
                value = totalOrders.toString(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StatCard(
                icon = Icons.Default.Warning,
                label = "Low Stock",
                value = "$lowStockCount items",
                color = if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StatCard(
                icon = Icons.Default.PointOfSale,
                label = "Registers",
                value = activeRegisters.toString(),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.let { if (it == Modifier) it.width(160.dp) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
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
private fun QuickActionGrid(
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
        QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.fillMaxWidth())
        QuickActionCard("Open Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.fillMaxWidth())
        QuickActionCard("View Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.fillMaxWidth())
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun LowStockAlertCard(count: Long, productNames: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "$count items are running low",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (productNames.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = productNames.joinToString(", ") + if (count > 5) ", ..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoRegisterAlertCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PointOfSale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "No register is open",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Open a register to start processing sales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun RecentOrderRow(
    order: RecentOrder,
    currencyFormatter: CurrencyFormatter,
) {
    val tz = TimeZone.currentSystemDefault()
    val orderTime = Instant.fromEpochMilliseconds(order.timestamp)
        .toLocalDateTime(tz)
    val timeStr = "${orderTime.hour.toString().padStart(2, '0')}:${orderTime.minute.toString().padStart(2, '0')}"

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = order.orderNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                Text(
                    text = order.method,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Text(
            text = currencyFormatter.format(order.total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private data class RecentOrder(
    val orderNumber: String,
    val total: Double,
    val method: String,
    val timestamp: Long,
)

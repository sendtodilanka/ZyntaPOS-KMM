package com.zyntasolutions.zyntapos.feature.dashboard.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.ChartSeries
import com.zyntasolutions.zyntapos.designsystem.components.InfoCardVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaActivityItem
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaCompactStatCard
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaInfoCard
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLineChart
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSectionHeader
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaStatCard
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.feature.dashboard.DashboardViewModel
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardIntent
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardState
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.RecentOrderItem
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home dashboard screen — Professional KPI cards, weekly sales chart,
 * recent activity log, quick actions, alerts, profile avatar, and logout.
 *
 * **Navigation chrome** is provided by [ZyntaScaffold] (via [MainScaffoldShell]) —
 * this screen does not render its own nav shell.
 *
 * **Content layout** adapts to the available content area:
 * - **EXPANDED** (Desktop ≥840 dp): Two-column (KPIs + Chart | Activity + Alerts)
 * - **MEDIUM** (Tablet 600–840 dp): Single-column with chart below KPIs
 * - **COMPACT** (Mobile <600 dp): Single-column scrollable with horizontal stat cards
 *
 * All business logic is delegated to [DashboardViewModel] via MVI intents.
 *
 * @param onNavigateToPos       Called when the user taps "New Sale".
 * @param onNavigateToRegister  Called when the user taps "Register".
 * @param onNavigateToReports   Called when the user taps "Reports" or "See All".
 * @param onNavigateToSettings  Called when the user taps the settings icon.
 */
@Composable
fun DashboardScreen(
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currencyFormatter: CurrencyFormatter = koinInject()
    val windowSize = currentWindowSize()

    LaunchedEffect(Unit) {
        viewModel.dispatch(DashboardIntent.LoadDashboard)
    }

    if (state.isLoading) {
        ZyntaLoadingOverlay(isLoading = true)
        return
    }

    val onLogout: () -> Unit = { viewModel.dispatch(DashboardIntent.Logout) }

    when (windowSize) {
        WindowSize.EXPANDED -> ExpandedDashboard(
            state, currencyFormatter,
            onNavigateToPos, onNavigateToRegister, onNavigateToReports, onNavigateToSettings,
            onNavigateToNotifications, onLogout,
        )
        WindowSize.MEDIUM -> MediumDashboard(
            state, currencyFormatter,
            onNavigateToPos, onNavigateToRegister, onNavigateToReports, onNavigateToSettings,
            onNavigateToNotifications, onLogout,
        )
        WindowSize.COMPACT -> CompactDashboard(
            state, currencyFormatter,
            onNavigateToPos, onNavigateToRegister, onNavigateToReports, onNavigateToSettings,
            onNavigateToNotifications, onLogout,
        )
    }
}

// ── EXPANDED LAYOUT (Desktop ≥840 dp) ─────────────────────────────────────

@Composable
private fun ExpandedDashboard(
    state: DashboardState, currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit, onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit, onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit, onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(ZyntaSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
    ) {
        // Left column (65%): Header + KPIs + Chart + Quick Actions
        LazyColumn(
            modifier = Modifier.weight(0.65f),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            item { ProfileHeader(state.currentUser, onNavigateToSettings, onNavigateToNotifications, onLogout) }

            // 4 KPI cards in 2×2 grid using Row (avoids nested-scrollable crashes)
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {
                    ZyntaStatCard(
                        icon = Icons.Default.AttachMoney, label = "Today's Sales",
                        value = currencyFormatter.format(state.todaysSales),
                        accentColor = MaterialTheme.colorScheme.primary,
                        sparklineData = state.todaySparkline, modifier = Modifier.weight(1f),
                    )
                    ZyntaStatCard(
                        icon = Icons.Default.Receipt, label = "Total Orders",
                        value = state.totalOrders.toString(),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        subtitle = "Completed today", modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {
                    ZyntaStatCard(
                        icon = Icons.Default.Warning, label = "Low Stock",
                        value = "${state.lowStockCount} items",
                        accentColor = if (state.lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        subtitle = if (state.lowStockCount > 0) "Requires attention" else "All stocked",
                        modifier = Modifier.weight(1f),
                    )
                    ZyntaStatCard(
                        icon = Icons.Default.PointOfSale, label = "Active Registers",
                        value = state.activeRegisters.toString(),
                        accentColor = if (state.activeRegisters > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                        subtitle = if (state.activeRegisters > 0) "Ready for sales" else "No register open",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                ZyntaLineChart(
                    title = "Weekly Sales Trend",
                    series = listOf(ChartSeries("Sales", state.weeklySalesData, MaterialTheme.colorScheme.primary)),
                    chartHeight = 220, showLegend = false,
                )
            }

            item { ZyntaSectionHeader(title = "Quick Actions") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                    QuickActionCard("Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                    QuickActionCard("Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
                }
            }
        }

        // Right column (35%): Activity + Alerts
        LazyColumn(
            modifier = Modifier.weight(0.35f),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            item { ZyntaSectionHeader("Recent Activity", actionLabel = "See All", onAction = onNavigateToReports) }
            if (state.recentOrders.isEmpty()) {
                item { EmptyActivityCard() }
            } else {
                item { RecentOrdersCard(state.recentOrders.take(8), currencyFormatter) }
            }
            if (state.lowStockCount > 0) {
                item {
                    ZyntaInfoCard(
                        Icons.Default.Warning, "${state.lowStockCount} items running low",
                        description = state.lowStockNames.joinToString(", ") + if (state.lowStockCount > 5) ", ..." else "",
                        variant = InfoCardVariant.Warning,
                    )
                }
            }
            if (state.activeRegisters == 0L) {
                item {
                    ZyntaInfoCard(
                        Icons.Default.PointOfSale, "No register is open",
                        description = "Open a register to start processing sales",
                        variant = InfoCardVariant.Info, onClick = onNavigateToRegister,
                    )
                }
            }
        }
    }
}

// ── MEDIUM LAYOUT (Tablet 600–840 dp) ────────────────────────────────────

@Composable
private fun MediumDashboard(
    state: DashboardState, currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit, onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit, onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit, onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item { ProfileHeader(state.currentUser, onNavigateToSettings, onNavigateToNotifications, onLogout) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaStatCard(Icons.Default.AttachMoney, "Today's Sales", currencyFormatter.format(state.todaysSales),
                    MaterialTheme.colorScheme.primary, sparklineData = state.todaySparkline, modifier = Modifier.weight(1f))
                ZyntaStatCard(Icons.Default.Receipt, "Total Orders", state.totalOrders.toString(),
                    MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaStatCard(Icons.Default.Warning, "Low Stock", "${state.lowStockCount} items",
                    if (state.lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f))
                ZyntaStatCard(Icons.Default.PointOfSale, "Registers", state.activeRegisters.toString(),
                    if (state.activeRegisters > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                QuickActionCard("Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                QuickActionCard("Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
            }
        }

        item {
            ZyntaLineChart("Weekly Sales Trend",
                listOf(ChartSeries("Sales", state.weeklySalesData, MaterialTheme.colorScheme.primary)),
                chartHeight = 180, showLegend = false)
        }

        if (state.lowStockCount > 0) {
            item { ZyntaInfoCard(Icons.Default.Warning, "${state.lowStockCount} items running low",
                description = state.lowStockNames.joinToString(", "), variant = InfoCardVariant.Warning) }
        }
        if (state.activeRegisters == 0L) {
            item { ZyntaInfoCard(Icons.Default.PointOfSale, "No register is open",
                description = "Open a register to start", variant = InfoCardVariant.Info, onClick = onNavigateToRegister) }
        }

        item { ZyntaSectionHeader("Recent Activity", actionLabel = "See All", onAction = onNavigateToReports) }
        if (state.recentOrders.isEmpty()) {
            item { EmptyActivityCard() }
        } else {
            item { RecentOrdersCard(state.recentOrders.take(5), currencyFormatter) }
        }
    }
}

// ── COMPACT LAYOUT (Mobile <600 dp) ──────────────────────────────────────

@Composable
private fun CompactDashboard(
    state: DashboardState, currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit, onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit, onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit, onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item { ProfileHeader(state.currentUser, onNavigateToSettings, onNavigateToNotifications, onLogout) }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                item { ZyntaCompactStatCard(Icons.Default.AttachMoney, "Today's Sales", currencyFormatter.format(state.todaysSales), MaterialTheme.colorScheme.primary) }
                item { ZyntaCompactStatCard(Icons.Default.Receipt, "Orders", state.totalOrders.toString(), MaterialTheme.colorScheme.tertiary) }
                item { ZyntaCompactStatCard(Icons.Default.Warning, "Low Stock", "${state.lowStockCount}", if (state.lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) }
                item { ZyntaCompactStatCard(Icons.Default.PointOfSale, "Registers", state.activeRegisters.toString(), if (state.activeRegisters > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary) }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                QuickActionCard("Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                QuickActionCard("Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
            }
        }

        if (state.lowStockCount > 0) {
            item { ZyntaInfoCard(Icons.Default.Warning, "${state.lowStockCount} items running low",
                description = state.lowStockNames.joinToString(", "), variant = InfoCardVariant.Warning) }
        }
        if (state.activeRegisters == 0L) {
            item { ZyntaInfoCard(Icons.Default.PointOfSale, "No register is open",
                description = "Open a register to start", variant = InfoCardVariant.Info, onClick = onNavigateToRegister) }
        }

        item {
            ZyntaLineChart("Weekly Sales Trend",
                listOf(ChartSeries("Sales", state.weeklySalesData, MaterialTheme.colorScheme.primary)),
                chartHeight = 160, showLegend = false)
        }

        item { ZyntaSectionHeader("Recent Activity") }
        if (state.recentOrders.isEmpty()) {
            item { EmptyActivityCard() }
        } else {
            items(state.recentOrders.take(5), key = { it.orderNumber }) { order ->
                val tz = TimeZone.currentSystemDefault()
                val orderTime = Instant.fromEpochMilliseconds(order.timestamp).toLocalDateTime(tz)
                val timeStr = "${orderTime.hour.toString().padStart(2, '0')}:${orderTime.minute.toString().padStart(2, '0')}"
                ZyntaActivityItem(order.orderNumber, "$timeStr  •  ${order.method}",
                    currencyFormatter.format(order.total), icon = Icons.Default.Receipt)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    currentUser: User?,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
                onClick = onNavigateToSettings,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initials = currentUser?.name
                        ?.split(" ")
                        ?.take(2)
                        ?.mapNotNull { it.firstOrNull()?.uppercase() }
                        ?.joinToString("")
                        ?: "M"
                    if (initials.isNotEmpty()) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            Icons.Default.AccountCircle, "Profile",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(ZyntaSpacing.sm))
            Column {
                Text("Welcome back,", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(currentUser?.name ?: "Manager", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
        }

        Row {
            BadgedBox(badge = { Badge() }) {
                IconButton(onClick = onNavigateToNotifications) {
                    Icon(Icons.Default.Notifications, "Notifications",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RecentOrdersCard(orders: List<RecentOrderItem>, currencyFormatter: CurrencyFormatter) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = ZyntaElevation.Level1),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            orders.forEachIndexed { index, order ->
                val tz = TimeZone.currentSystemDefault()
                val orderTime = Instant.fromEpochMilliseconds(order.timestamp).toLocalDateTime(tz)
                val timeStr = "${orderTime.hour.toString().padStart(2, '0')}:${orderTime.minute.toString().padStart(2, '0')}"
                ZyntaActivityItem(
                    title = order.orderNumber,
                    subtitle = "$timeStr  •  ${order.method}",
                    trailingText = currencyFormatter.format(order.total),
                    icon = Icons.Default.Receipt,
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                if (index < orders.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = ZyntaSpacing.md),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyActivityCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Receipt, null, Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Text("No orders yet today", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Complete your first sale to see activity here", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun QuickActionCard(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(ZyntaSpacing.xs))
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
        }
    }
}

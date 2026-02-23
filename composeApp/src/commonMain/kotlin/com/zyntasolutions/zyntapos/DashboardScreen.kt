package com.zyntasolutions.zyntapos

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
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.ChartDataPoint
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
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/**
 * Home dashboard screen — Professional KPI cards, weekly sales chart,
 * recent activity log, quick actions, alerts, profile avatar, and logout.
 *
 * Fully responsive layout:
 * - **EXPANDED** (Desktop >=840dp): Two-column (KPIs + Chart | Activity + Alerts)
 * - **MEDIUM** (Tablet 600-840dp): Single-column with chart below KPIs
 * - **COMPACT** (Mobile <600dp): Single-column scrollable with horizontal stat cards
 */
@Composable
fun DashboardScreen(
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val orderRepository: OrderRepository = koinInject()
    val productRepository: ProductRepository = koinInject()
    val registerRepository: RegisterRepository = koinInject()
    val authRepository: AuthRepository = koinInject()
    val currencyFormatter: CurrencyFormatter = koinInject()
    val windowSize = currentWindowSize()
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var todaysSales by remember { mutableStateOf(0.0) }
    var totalOrders by remember { mutableStateOf(0L) }
    var lowStockCount by remember { mutableStateOf(0L) }
    var lowStockNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeRegisters by remember { mutableStateOf(0L) }
    var recentOrders by remember { mutableStateOf<List<RecentOrder>>(emptyList()) }
    var weeklySalesData by remember { mutableStateOf<List<ChartDataPoint>>(emptyList()) }
    var todaySparkline by remember { mutableStateOf<List<Float>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val onLogout: () -> Unit = {
        scope.launch { authRepository.logout() }
    }

    LaunchedEffect(Unit) {
        currentUser = authRepository.getSession().first()

        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val todayStart = now.toLocalDateTime(tz).date.atStartOfDayIn(tz)

        // Today's orders
        val todayOrders = orderRepository.getByDateRange(todayStart, now).first()
        val completedToday = todayOrders.filter { it.status == OrderStatus.COMPLETED }
        todaysSales = completedToday.sumOf { it.total }
        totalOrders = completedToday.size.toLong()

        // Build sparkline from today's hourly sales
        val hourlyBuckets = FloatArray(24)
        completedToday.forEach { order ->
            val hour = order.createdAt.toLocalDateTime(tz).hour
            hourlyBuckets[hour] += order.total.toFloat()
        }
        val currentHour = now.toLocalDateTime(tz).hour
        todaySparkline = hourlyBuckets.take(currentHour + 1).map { it }

        // Weekly sales data (last 7 days)
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val weeklyPoints = mutableListOf<ChartDataPoint>()
        for (i in 6 downTo 0) {
            val dayDate = now.toLocalDateTime(tz).date.minus(i.toLong(), DateTimeUnit.DAY)
            val dayStart = dayDate.atStartOfDayIn(tz)
            val dayEnd = if (i == 0) {
                now
            } else {
                now.toLocalDateTime(tz).date.minus((i - 1).toLong(), DateTimeUnit.DAY)
                    .atStartOfDayIn(tz)
            }
            val dayOrders = orderRepository.getByDateRange(dayStart, dayEnd).first()
            val daySales = dayOrders
                .filter { it.status == OrderStatus.COMPLETED }
                .sumOf { it.total }
            val dayOfWeek = dayDate.dayOfWeek.ordinal // 0=Monday
            weeklyPoints.add(ChartDataPoint(label = dayNames[dayOfWeek], value = daySales.toFloat()))
        }
        weeklySalesData = weeklyPoints

        // Low stock
        val allProducts = productRepository.getAll().first()
        val lowStockProducts = allProducts.filter { it.stockQty <= it.minStockQty }
        lowStockCount = lowStockProducts.size.toLong()
        lowStockNames = lowStockProducts.take(5).map { it.name }

        // Active registers
        val activeSession = registerRepository.getActive().first()
        activeRegisters = if (activeSession != null) 1L else 0L

        // Recent completed orders (last 10)
        val allOrders = orderRepository.getAll().first()
        recentOrders = allOrders
            .filter { it.status == OrderStatus.COMPLETED }
            .sortedByDescending { it.createdAt }
            .take(10)
            .map { order ->
                RecentOrder(
                    orderNumber = order.orderNumber,
                    total = order.total,
                    method = order.paymentMethod.name,
                    timestamp = order.createdAt.toEpochMilliseconds(),
                )
            }
        isLoading = false
    }

    if (isLoading) {
        ZyntaLoadingOverlay(isLoading = true)
        return
    }

    when (windowSize) {
        WindowSize.EXPANDED -> ExpandedDashboard(
            currentUser, todaysSales, totalOrders, lowStockCount, lowStockNames,
            activeRegisters, recentOrders, weeklySalesData, todaySparkline,
            currencyFormatter, onNavigateToPos, onNavigateToRegister,
            onNavigateToReports, onNavigateToSettings, onLogout,
        )
        WindowSize.MEDIUM -> MediumDashboard(
            currentUser, todaysSales, totalOrders, lowStockCount, lowStockNames,
            activeRegisters, recentOrders, weeklySalesData, todaySparkline,
            currencyFormatter, onNavigateToPos, onNavigateToRegister,
            onNavigateToReports, onNavigateToSettings, onLogout,
        )
        WindowSize.COMPACT -> CompactDashboard(
            currentUser, todaysSales, totalOrders, lowStockCount, lowStockNames,
            activeRegisters, recentOrders, weeklySalesData, todaySparkline,
            currencyFormatter, onNavigateToPos, onNavigateToRegister,
            onNavigateToReports, onNavigateToSettings, onLogout,
        )
    }
}

// ── EXPANDED LAYOUT (Desktop >=840dp) ───────────────────────────────────────

@Composable
private fun ExpandedDashboard(
    currentUser: User?, todaysSales: Double, totalOrders: Long,
    lowStockCount: Long, lowStockNames: List<String>, activeRegisters: Long,
    recentOrders: List<RecentOrder>, weeklySalesData: List<ChartDataPoint>,
    todaySparkline: List<Float>, currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit, onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit, onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
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
            item { ProfileHeader(currentUser, onNavigateToSettings, onLogout) }

            // 4 KPI cards in 2x2 using Row (NOT LazyVerticalGrid — avoids nested scrollable crash)
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {
                    ZyntaStatCard(
                        icon = Icons.Default.AttachMoney, label = "Today's Sales",
                        value = currencyFormatter.format(todaysSales),
                        accentColor = MaterialTheme.colorScheme.primary,
                        sparklineData = todaySparkline, modifier = Modifier.weight(1f),
                    )
                    ZyntaStatCard(
                        icon = Icons.Default.Receipt, label = "Total Orders",
                        value = totalOrders.toString(),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        subtitle = "Completed today", modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {
                    ZyntaStatCard(
                        icon = Icons.Default.Warning, label = "Low Stock",
                        value = "$lowStockCount items",
                        accentColor = if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        subtitle = if (lowStockCount > 0) "Requires attention" else "All stocked",
                        modifier = Modifier.weight(1f),
                    )
                    ZyntaStatCard(
                        icon = Icons.Default.PointOfSale, label = "Active Registers",
                        value = activeRegisters.toString(),
                        accentColor = if (activeRegisters > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                        subtitle = if (activeRegisters > 0) "Ready for sales" else "No register open",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                ZyntaLineChart(
                    title = "Weekly Sales Trend",
                    series = listOf(ChartSeries("Sales", weeklySalesData, MaterialTheme.colorScheme.primary)),
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
            if (recentOrders.isEmpty()) {
                item { EmptyActivityCard() }
            } else {
                item { RecentOrdersCard(recentOrders.take(8), currencyFormatter) }
            }
            if (lowStockCount > 0) {
                item {
                    ZyntaInfoCard(
                        Icons.Default.Warning, "$lowStockCount items running low",
                        description = lowStockNames.joinToString(", ") + if (lowStockCount > 5) ", ..." else "",
                        variant = InfoCardVariant.Warning,
                    )
                }
            }
            if (activeRegisters == 0L) {
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

// ── MEDIUM LAYOUT (Tablet 600-840dp) ────────────────────────────────────────

@Composable
private fun MediumDashboard(
    currentUser: User?, todaysSales: Double, totalOrders: Long,
    lowStockCount: Long, lowStockNames: List<String>, activeRegisters: Long,
    recentOrders: List<RecentOrder>, weeklySalesData: List<ChartDataPoint>,
    todaySparkline: List<Float>, currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit, onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit, onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item { ProfileHeader(currentUser, onNavigateToSettings, onLogout) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaStatCard(Icons.Default.AttachMoney, "Today's Sales", currencyFormatter.format(todaysSales),
                    MaterialTheme.colorScheme.primary, sparklineData = todaySparkline, modifier = Modifier.weight(1f))
                ZyntaStatCard(Icons.Default.Receipt, "Total Orders", totalOrders.toString(),
                    MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaStatCard(Icons.Default.Warning, "Low Stock", "$lowStockCount items",
                    if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f))
                ZyntaStatCard(Icons.Default.PointOfSale, "Registers", activeRegisters.toString(),
                    if (activeRegisters > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
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
                listOf(ChartSeries("Sales", weeklySalesData, MaterialTheme.colorScheme.primary)),
                chartHeight = 180, showLegend = false)
        }

        if (lowStockCount > 0) {
            item { ZyntaInfoCard(Icons.Default.Warning, "$lowStockCount items running low",
                description = lowStockNames.joinToString(", "), variant = InfoCardVariant.Warning) }
        }
        if (activeRegisters == 0L) {
            item { ZyntaInfoCard(Icons.Default.PointOfSale, "No register is open",
                description = "Open a register to start", variant = InfoCardVariant.Info, onClick = onNavigateToRegister) }
        }

        item { ZyntaSectionHeader("Recent Activity", actionLabel = "See All", onAction = onNavigateToReports) }
        if (recentOrders.isEmpty()) {
            item { EmptyActivityCard() }
        } else {
            item { RecentOrdersCard(recentOrders.take(5), currencyFormatter) }
        }
    }
}

// ── COMPACT LAYOUT (Mobile <600dp) ──────────────────────────────────────────

@Composable
private fun CompactDashboard(
    currentUser: User?, todaysSales: Double, totalOrders: Long,
    lowStockCount: Long, lowStockNames: List<String>, activeRegisters: Long,
    recentOrders: List<RecentOrder>, weeklySalesData: List<ChartDataPoint>,
    todaySparkline: List<Float>, currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit, onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit, onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        item { ProfileHeader(currentUser, onNavigateToSettings, onLogout) }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                item { ZyntaCompactStatCard(Icons.Default.AttachMoney, "Today's Sales", currencyFormatter.format(todaysSales), MaterialTheme.colorScheme.primary) }
                item { ZyntaCompactStatCard(Icons.Default.Receipt, "Orders", totalOrders.toString(), MaterialTheme.colorScheme.tertiary) }
                item { ZyntaCompactStatCard(Icons.Default.Warning, "Low Stock", "$lowStockCount", if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) }
                item { ZyntaCompactStatCard(Icons.Default.PointOfSale, "Registers", activeRegisters.toString(), if (activeRegisters > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary) }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                QuickActionCard("Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                QuickActionCard("Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
            }
        }

        if (lowStockCount > 0) {
            item { ZyntaInfoCard(Icons.Default.Warning, "$lowStockCount items running low",
                description = lowStockNames.joinToString(", "), variant = InfoCardVariant.Warning) }
        }
        if (activeRegisters == 0L) {
            item { ZyntaInfoCard(Icons.Default.PointOfSale, "No register is open",
                description = "Open a register to start", variant = InfoCardVariant.Info, onClick = onNavigateToRegister) }
        }

        item {
            ZyntaLineChart("Weekly Sales Trend",
                listOf(ChartSeries("Sales", weeklySalesData, MaterialTheme.colorScheme.primary)),
                chartHeight = 160, showLegend = false)
        }

        item { ZyntaSectionHeader("Recent Activity") }
        if (recentOrders.isEmpty()) {
            item { EmptyActivityCard() }
        } else {
            items(recentOrders.take(5), key = { it.orderNumber }) { order ->
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

// ── Shared sub-composables ──────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    currentUser: User?,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Profile avatar with initials
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
private fun RecentOrdersCard(orders: List<RecentOrder>, currencyFormatter: CurrencyFormatter) {
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

private data class RecentOrder(
    val orderNumber: String,
    val total: Double,
    val method: String,
    val timestamp: Long,
)

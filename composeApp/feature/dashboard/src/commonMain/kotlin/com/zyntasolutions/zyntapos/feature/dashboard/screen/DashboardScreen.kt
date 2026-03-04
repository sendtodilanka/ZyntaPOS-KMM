package com.zyntasolutions.zyntapos.feature.dashboard.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.zyntasolutions.zyntapos.designsystem.components.ChartSeries
import com.zyntasolutions.zyntapos.designsystem.components.InfoCardVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaActivityItem
import com.zyntasolutions.zyntapos.designsystem.components.StatusChipVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaCompactStatCard
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDropdownMenu
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDropdownMenuItem
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaHeroStatCard
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaInfoCard
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLineChart
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaProgressRing
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSectionHeader
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaStatCard
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaStatusChip
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.feature.dashboard.DashboardViewModel
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardEffect
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardIntent
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardState
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.RecentOrderItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home dashboard screen — KPI cards, weekly sales chart, recent activity,
 * quick actions, alerts, and profile avatar menu.
 *
 * **Navigation chrome** is provided by [ZyntaScaffold] (via MainScaffoldShell) —
 * this screen uses [ZyntaPageScaffold] for its own top app bar and snackbar host.
 *
 * **Content layout** adapts to the available content area:
 * - **EXPANDED** (Desktop ≥840 dp): KPI strip + Quick Actions on top, Chart + Activity below
 * - **MEDIUM** (Tablet 600–840 dp): Two-column supporting pane (KPIs+Chart | Activity+Alerts)
 * - **COMPACT** (Mobile <600 dp): Single-column with 2×2 KPI grid (no nested scroll)
 *
 * All business logic is delegated to [DashboardViewModel] via MVI intents.
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.dispatch(DashboardIntent.LoadDashboard)
    }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DashboardEffect.ShowError -> scope.launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    DashboardScreenContent(
        state = state,
        currencyFormatter = currencyFormatter,
        windowSize = windowSize,
        snackbarHostState = snackbarHostState,
        onNavigateToPos = onNavigateToPos,
        onNavigateToRegister = onNavigateToRegister,
        onNavigateToReports = onNavigateToReports,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToNotifications = onNavigateToNotifications,
        onLogout = { viewModel.dispatch(DashboardIntent.Logout) },
    )
}

/**
 * Stateless content composable — extracted for Compose Desktop UI testability.
 *
 * Uses [ZyntaPageScaffold] for consistent design-system compliance:
 * - [ZyntaTopAppBar] with title "Dashboard" and profile avatar menu
 * - [ZyntaSnackbarHost] for error messages
 */
@Composable
internal fun DashboardScreenContent(
    state: DashboardState,
    currencyFormatter: CurrencyFormatter,
    windowSize: WindowSize,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
) {
    ZyntaPageScaffold(
        title = "Dashboard",
        snackbarHostState = snackbarHostState,
        actions = {
            ProfileAvatarMenu(
                currentUser = state.currentUser,
                userInitials = state.userInitials,
                greetingText = state.greetingText,
                onNavigateToNotifications = onNavigateToNotifications,
                onNavigateToSettings = onNavigateToSettings,
                onLogout = onLogout,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                ZyntaLoadingOverlay(isLoading = true)
            } else {
                when (windowSize) {
                    WindowSize.EXPANDED -> ExpandedDashboard(
                        state, currencyFormatter,
                        onNavigateToPos, onNavigateToRegister, onNavigateToReports,
                    )
                    WindowSize.MEDIUM -> MediumDashboard(
                        state, currencyFormatter,
                        onNavigateToPos, onNavigateToRegister, onNavigateToReports,
                    )
                    WindowSize.COMPACT -> CompactDashboard(
                        state, currencyFormatter,
                        onNavigateToPos, onNavigateToRegister, onNavigateToReports,
                    )
                }
            }
        }
    }
}

// ── PROFILE AVATAR MENU ──────────────────────────────────────────────────

/**
 * Avatar circle in the top app bar actions slot.
 * Opens a [ZyntaDropdownMenu] with profile actions on tap.
 */
@Composable
private fun ProfileAvatarMenu(
    currentUser: User?,
    userInitials: String,
    greetingText: String,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
            onClick = { showMenu = true },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = userInitials,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        ZyntaDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            // Welcome header (non-interactive)
            Column(
                modifier = Modifier.padding(
                    horizontal = ZyntaSpacing.md,
                    vertical = ZyntaSpacing.sm,
                ),
            ) {
                Text(
                    greetingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    currentUser?.name ?: "Manager",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider()

            ZyntaDropdownMenuItem(
                text = { Text("Notifications") },
                onClick = { showMenu = false; onNavigateToNotifications() },
                leadingIcon = {
                    BadgedBox(badge = { Badge() }) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(20.dp))
                    }
                },
            )
            ZyntaDropdownMenuItem(
                text = { Text("Settings") },
                onClick = { showMenu = false; onNavigateToSettings() },
                leadingIcon = {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                },
            )
            HorizontalDivider()
            ZyntaDropdownMenuItem(
                text = {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                },
                onClick = { showMenu = false; onLogout() },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

// ── EXPANDED LAYOUT (Desktop ≥840 dp) ─────────────────────────────────────

@Composable
private fun ExpandedDashboard(
    state: DashboardState,
    currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ZyntaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        // Row 1: Hero KPI + 3 KPI cards + Quick Actions column
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            StaggeredEntrance(modifier = Modifier.weight(1.4f), delayMs = 0) {
                ZyntaHeroStatCard(
                    icon = Icons.Default.AttachMoney,
                    label = "Today's Sales",
                    value = currencyFormatter.format(state.todaysSales),
                    subtitle = "of ${currencyFormatter.format(state.dailySalesTarget)} target",
                    modifier = Modifier.fillMaxWidth(),
                    rawValue = state.todaysSales.toFloat(),
                    rawValueFormatter = { currencyFormatter.format(it.toDouble()) },
                    rightSlot = {
                        ZyntaProgressRing(
                            progress = state.salesProgress,
                            size = 120.dp,
                            strokeWidth = 8.dp,
                            trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                            progressColor = androidx.compose.ui.graphics.Color.White,
                            centerContent = {
                                Text(
                                    text = "${(state.salesProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White,
                                )
                            },
                        )
                    },
                )
            }
            StaggeredEntrance(modifier = Modifier.weight(1f), delayMs = 50) {
                ZyntaStatCard(
                    icon = Icons.Default.Receipt, label = "Total Orders",
                    value = state.totalOrders.toString(),
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    subtitle = "Completed today", modifier = Modifier.fillMaxWidth(),
                    rawValue = state.totalOrders.toFloat(),
                    rawValueDelayMs = 0,
                )
            }
            StaggeredEntrance(modifier = Modifier.weight(1f), delayMs = 100) {
                ZyntaStatCard(
                    icon = Icons.Default.Warning, label = "Low Stock",
                    value = "${state.lowStockCount} items",
                    accentColor = if (state.lowStockCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary,
                    subtitle = if (state.lowStockCount > 0) "Requires attention" else "All stocked",
                    modifier = Modifier.fillMaxWidth(),
                    rawValue = state.lowStockCount.toFloat(),
                    rawValueFormatter = { "${it.toLong()} items" },
                    rawValueDelayMs = 0,
                )
            }
            StaggeredEntrance(modifier = Modifier.weight(1f), delayMs = 150) {
                ZyntaStatCard(
                    icon = Icons.Default.PointOfSale, label = "Active Registers",
                    value = state.activeRegisters.toString(),
                    accentColor = if (state.activeRegisters > 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.secondary,
                    subtitle = if (state.activeRegisters > 0) "Ready for sales" else "No register open",
                    modifier = Modifier.fillMaxWidth(),
                    rawValue = state.activeRegisters.toFloat(),
                    rawValueDelayMs = 0,
                )
            }

            // Quick Actions stacked vertically
            StaggeredEntrance(modifier = Modifier.weight(1f), delayMs = 200) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
                ) {
                    ZyntaSectionHeader(title = "Quick Actions")
                    QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.fillMaxWidth())
                    QuickActionCard("Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.fillMaxWidth())
                    QuickActionCard("Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.fillMaxWidth())
                }
            }
        }

        // Row 2: Chart + Alerts (left 60%) | Activity (right 40%)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
        ) {
            // Left zone: Chart + Alert cards
            StaggeredEntrance(
                modifier = Modifier.weight(0.6f).fillMaxHeight(),
                delayMs = 50,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                ) {
                    ZyntaLineChart(
                        title = "Weekly Sales Trend",
                        series = listOf(
                            ChartSeries("Sales", state.weeklySalesData, MaterialTheme.colorScheme.primary),
                        ),
                        chartHeight = 280, showLegend = false,
                    )

                    if (state.lowStockCount > 0) {
                        ZyntaInfoCard(
                            Icons.Default.Warning, "${state.lowStockCount} items running low",
                            description = state.lowStockNames.joinToString(", ") +
                                if (state.lowStockCount > 5) ", ..." else "",
                            variant = InfoCardVariant.Warning,
                        )
                    }
                    if (state.activeRegisters == 0L) {
                        ZyntaInfoCard(
                            Icons.Default.PointOfSale, "No register is open",
                            description = "Open a register to start processing sales",
                            variant = InfoCardVariant.Info, onClick = onNavigateToRegister,
                        )
                    }
                }
            }

            // Right zone: Recent Activity
            StaggeredEntrance(
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                delayMs = 100,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                ) {
                    ZyntaSectionHeader(
                        "Recent Activity",
                        actionLabel = "See All",
                        onAction = onNavigateToReports,
                    )
                    if (state.recentOrders.isEmpty()) {
                        EmptyActivityCard()
                    } else {
                        RecentOrdersCard(state.recentOrders.take(8), currencyFormatter)
                    }
                }
            }
        }
    }
}

// ── MEDIUM LAYOUT (Tablet 600–840 dp) ────────────────────────────────────

@Composable
private fun MediumDashboard(
    state: DashboardState,
    currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(ZyntaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        // Left pane (55%): Hero KPI + 3 stat cards + Chart
        Column(
            modifier = Modifier.weight(0.55f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            StaggeredEntrance(delayMs = 0) {
                ZyntaHeroStatCard(
                    icon = Icons.Default.AttachMoney,
                    label = "Today's Sales",
                    value = currencyFormatter.format(state.todaysSales),
                    subtitle = "of ${currencyFormatter.format(state.dailySalesTarget)} target",
                    modifier = Modifier.fillMaxWidth(),
                    rawValue = state.todaysSales.toFloat(),
                    rawValueFormatter = { currencyFormatter.format(it.toDouble()) },
                    rightSlot = {
                        ZyntaProgressRing(
                            progress = state.salesProgress,
                            size = 80.dp,
                            strokeWidth = 8.dp,
                            trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                            progressColor = androidx.compose.ui.graphics.Color.White,
                            centerContent = {
                                Text(
                                    text = "${(state.salesProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White,
                                )
                            },
                        )
                    },
                )
            }

            StaggeredEntrance(delayMs = 50) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    ZyntaStatCard(
                        Icons.Default.Receipt, "Total Orders",
                        state.totalOrders.toString(),
                        MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.totalOrders.toFloat(),
                        rawValueDelayMs = 0,
                    )
                    ZyntaStatCard(
                        Icons.Default.Warning, "Low Stock",
                        "${state.lowStockCount} items",
                        if (state.lowStockCount > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.lowStockCount.toFloat(),
                        rawValueFormatter = { "${it.toLong()} items" },
                        rawValueDelayMs = 50,
                    )
                    ZyntaStatCard(
                        Icons.Default.PointOfSale, "Registers",
                        state.activeRegisters.toString(),
                        if (state.activeRegisters > 0) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.activeRegisters.toFloat(),
                        rawValueDelayMs = 100,
                    )
                }
            }

            StaggeredEntrance(delayMs = 100) {
                ZyntaLineChart(
                    "Weekly Sales Trend",
                    listOf(ChartSeries("Sales", state.weeklySalesData, MaterialTheme.colorScheme.primary)),
                    chartHeight = 180, showLegend = false,
                )
            }
        }

        // Right pane (45%): Quick Actions + Activity + Alerts
        Column(
            modifier = Modifier.weight(0.45f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            StaggeredEntrance(delayMs = 50) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                    QuickActionCard("Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                    QuickActionCard("Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
                }
            }

            StaggeredEntrance(delayMs = 100) {
                Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {
                    ZyntaSectionHeader(
                        "Recent Activity",
                        actionLabel = "See All",
                        onAction = onNavigateToReports,
                    )
                    if (state.recentOrders.isEmpty()) {
                        EmptyActivityCard()
                    } else {
                        RecentOrdersCard(state.recentOrders.take(5), currencyFormatter)
                    }

                    if (state.lowStockCount > 0) {
                        ZyntaInfoCard(
                            Icons.Default.Warning, "${state.lowStockCount} items running low",
                            description = state.lowStockNames.joinToString(", "),
                            variant = InfoCardVariant.Warning,
                        )
                    }
                    if (state.activeRegisters == 0L) {
                        ZyntaInfoCard(
                            Icons.Default.PointOfSale, "No register is open",
                            description = "Open a register to start",
                            variant = InfoCardVariant.Info, onClick = onNavigateToRegister,
                        )
                    }
                }
            }
        }
    }
}

// ── COMPACT LAYOUT (Mobile <600 dp) ──────────────────────────────────────

@Composable
private fun CompactDashboard(
    state: DashboardState,
    currencyFormatter: CurrencyFormatter,
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        // Hero sales card with progress ring
        item {
            StaggeredEntrance(delayMs = 0) {
                ZyntaHeroStatCard(
                    icon = Icons.Default.AttachMoney,
                    label = "Today's Sales",
                    value = currencyFormatter.format(state.todaysSales),
                    subtitle = "of ${currencyFormatter.format(state.dailySalesTarget)} target",
                    modifier = Modifier.fillMaxWidth(),
                    rawValue = state.todaysSales.toFloat(),
                    rawValueFormatter = { currencyFormatter.format(it.toDouble()) },
                    rightSlot = {
                        ZyntaProgressRing(
                            progress = state.salesProgress,
                            size = 80.dp,
                            strokeWidth = 8.dp,
                            trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                            progressColor = androidx.compose.ui.graphics.Color.White,
                            centerContent = {
                                Text(
                                    text = "${(state.salesProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White,
                                )
                            },
                        )
                    },
                )
            }
        }

        // KPI row: Orders + Low Stock + Registers
        item {
            StaggeredEntrance(delayMs = 50) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    ZyntaCompactStatCard(
                        Icons.Default.Receipt, "Orders",
                        state.totalOrders.toString(),
                        MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.totalOrders.toFloat(),
                        rawValueDelayMs = 0,
                    )
                    ZyntaCompactStatCard(
                        Icons.Default.Warning, "Low Stock",
                        "${state.lowStockCount}",
                        if (state.lowStockCount > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.lowStockCount.toFloat(),
                        rawValueDelayMs = 50,
                    )
                    ZyntaCompactStatCard(
                        Icons.Default.PointOfSale, "Registers",
                        state.activeRegisters.toString(),
                        if (state.activeRegisters > 0) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.activeRegisters.toFloat(),
                        rawValueDelayMs = 100,
                    )
                }
            }
        }

        // Quick Actions
        item {
            StaggeredEntrance(delayMs = 100) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    QuickActionCard("New Sale", Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                    QuickActionCard("Register", Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                    QuickActionCard("Reports", Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
                }
            }
        }

        // Alerts
        if (state.lowStockCount > 0) {
            item {
                ZyntaInfoCard(
                    Icons.Default.Warning, "${state.lowStockCount} items running low",
                    description = state.lowStockNames.joinToString(", "),
                    variant = InfoCardVariant.Warning,
                )
            }
        }
        if (state.activeRegisters == 0L) {
            item {
                ZyntaInfoCard(
                    Icons.Default.PointOfSale, "No register is open",
                    description = "Open a register to start",
                    variant = InfoCardVariant.Info, onClick = onNavigateToRegister,
                )
            }
        }

        // Chart
        item {
            ZyntaLineChart(
                "Weekly Sales Trend",
                listOf(ChartSeries("Sales", state.weeklySalesData, MaterialTheme.colorScheme.primary)),
                chartHeight = 160, showLegend = false,
            )
        }

        // Recent Activity
        item { ZyntaSectionHeader("Recent Activity") }
        if (state.recentOrders.isEmpty()) {
            item { EmptyActivityCard() }
        } else {
            item { RecentOrdersCard(state.recentOrders.take(3), currencyFormatter) }
        }
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────

@Composable
private fun StaggeredEntrance(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = delayMs),
        label = "stagger_$delayMs",
    )
    LaunchedEffect(Unit) { visible = true }
    Box(
        modifier = modifier
            .alpha(animProgress)
            .offset(y = ((1f - animProgress) * 20).dp),
    ) {
        content()
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
                val chipVariant = when (order.method.uppercase()) {
                    "CASH" -> StatusChipVariant.Success
                    "CARD" -> StatusChipVariant.Info
                    else -> StatusChipVariant.Neutral
                }
                ZyntaActivityItem(
                    title = order.orderNumber,
                    subtitle = order.formattedTime,
                    trailingText = currencyFormatter.format(order.total),
                    icon = Icons.Default.Receipt,
                    iconTint = MaterialTheme.colorScheme.primary,
                    subtitleTrailing = {
                        ZyntaStatusChip(label = order.method, variant = chipVariant)
                    },
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Receipt, null, Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Text(
                "No orders yet today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Complete your first sale to see activity here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "quick_action_scale",
    )
    Card(
        onClick = onClick,
        modifier = modifier.scale(pressScale),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon, label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(ZyntaSpacing.xs))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

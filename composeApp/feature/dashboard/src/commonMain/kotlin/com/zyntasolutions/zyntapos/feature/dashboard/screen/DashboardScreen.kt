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
import androidx.compose.material.icons.filled.Store
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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ChartDataPoint
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
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingSkeleton
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
        onRefresh = { viewModel.dispatch(DashboardIntent.Refresh) },
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
    onRefresh: () -> Unit = {},
) {
    val s = LocalStrings.current
    ZyntaPageScaffold(
        title = s[StringResource.DASHBOARD_TITLE],
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.storeName.isNotEmpty()) {
                StoreNameChip(storeName = state.storeName)
                Spacer(Modifier.width(8.dp))
            }
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
                ZyntaLoadingSkeleton(modifier = Modifier.fillMaxSize().padding(16.dp))
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
    val s = LocalStrings.current
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
                    currentUser?.name ?: s[StringResource.DASHBOARD_DEFAULT_USER],
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider()

            ZyntaDropdownMenuItem(
                text = { Text(s[StringResource.DASHBOARD_NOTIFICATIONS]) },
                onClick = { showMenu = false; onNavigateToNotifications() },
                leadingIcon = {
                    BadgedBox(badge = { Badge() }) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(20.dp))
                    }
                },
            )
            ZyntaDropdownMenuItem(
                text = { Text(s[StringResource.DASHBOARD_SETTINGS]) },
                onClick = { showMenu = false; onNavigateToSettings() },
                leadingIcon = {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                },
            )
            HorizontalDivider()
            ZyntaDropdownMenuItem(
                text = {
                    Text(s[StringResource.AUTH_LOGOUT], color = MaterialTheme.colorScheme.error)
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
    val s = LocalStrings.current
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
                    label = s[StringResource.DASHBOARD_TODAYS_SALES],
                    value = currencyFormatter.format(state.todaysSales),
                    subtitle = "${s[StringResource.DASHBOARD_OF_TARGET_PREFIX]} ${currencyFormatter.format(state.dailySalesTarget)} ${s[StringResource.DASHBOARD_TARGET_SUFFIX]}",
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
                    icon = Icons.Default.Receipt, label = s[StringResource.DASHBOARD_TOTAL_ORDERS],
                    value = state.totalOrders.toString(),
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    subtitle = s[StringResource.DASHBOARD_COMPLETED_TODAY], modifier = Modifier.fillMaxWidth(),
                    sparklineData = state.todaySparkline,
                    rawValue = state.totalOrders.toFloat(),
                    rawValueDelayMs = 0,
                )
            }
            StaggeredEntrance(modifier = Modifier.weight(1f), delayMs = 100) {
                ZyntaStatCard(
                    icon = Icons.Default.Warning, label = s[StringResource.DASHBOARD_LOW_STOCK],
                    value = "${state.lowStockCount} ${s[StringResource.DASHBOARD_ITEMS]}",
                    accentColor = if (state.lowStockCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary,
                    subtitle = if (state.lowStockCount > 0) s[StringResource.DASHBOARD_REQUIRES_ATTENTION] else s[StringResource.DASHBOARD_ALL_STOCKED],
                    modifier = Modifier.fillMaxWidth(),
                    rawValue = state.lowStockCount.toFloat(),
                    rawValueFormatter = { "${it.toLong()} ${s[StringResource.DASHBOARD_ITEMS]}" },
                    rawValueDelayMs = 0,
                )
            }
            StaggeredEntrance(modifier = Modifier.weight(1f), delayMs = 150) {
                ZyntaStatCard(
                    icon = Icons.Default.PointOfSale, label = s[StringResource.DASHBOARD_ACTIVE_REGISTERS],
                    value = state.activeRegisters.toString(),
                    accentColor = if (state.activeRegisters > 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.secondary,
                    subtitle = if (state.activeRegisters > 0) s[StringResource.DASHBOARD_READY_FOR_SALES] else s[StringResource.DASHBOARD_NO_REGISTER_OPEN],
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
                    ZyntaSectionHeader(title = s[StringResource.DASHBOARD_QUICK_ACTIONS])
                    QuickActionCard(s[StringResource.DASHBOARD_NEW_SALE], Icons.Default.ShoppingCart, onNavigateToPos, Modifier.fillMaxWidth())
                    QuickActionCard(s[StringResource.DASHBOARD_REGISTER], Icons.Default.PointOfSale, onNavigateToRegister, Modifier.fillMaxWidth())
                    QuickActionCard(s[StringResource.DASHBOARD_REPORTS], Icons.Default.Assessment, onNavigateToReports, Modifier.fillMaxWidth())
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
                        title = s[StringResource.DASHBOARD_WEEKLY_TREND],
                        series = listOf(
                            ChartSeries(s[StringResource.DASHBOARD_SALES], state.weeklySalesData, MaterialTheme.colorScheme.primary),
                        ),
                        chartHeight = 280, showLegend = false,
                    )

                    if (state.lowStockCount > 0) {
                        ZyntaInfoCard(
                            Icons.Default.Warning, "${state.lowStockCount} ${s[StringResource.DASHBOARD_ITEMS_RUNNING_LOW]}",
                            description = state.lowStockNames.joinToString(", ") +
                                if (state.lowStockCount > 5) ", ..." else "",
                            variant = InfoCardVariant.Warning,
                        )
                    }
                    if (state.activeRegisters == 0L) {
                        ZyntaInfoCard(
                            Icons.Default.PointOfSale, s[StringResource.DASHBOARD_NO_REGISTER_OPEN],
                            description = s[StringResource.DASHBOARD_OPEN_REGISTER_TO_START],
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
                        s[StringResource.DASHBOARD_RECENT_ACTIVITY],
                        actionLabel = s[StringResource.DASHBOARD_SEE_ALL],
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
    val s = LocalStrings.current
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
                    label = s[StringResource.DASHBOARD_TODAYS_SALES],
                    value = currencyFormatter.format(state.todaysSales),
                    subtitle = "${s[StringResource.DASHBOARD_OF_TARGET_PREFIX]} ${currencyFormatter.format(state.dailySalesTarget)} ${s[StringResource.DASHBOARD_TARGET_SUFFIX]}",
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
                        Icons.Default.Receipt, s[StringResource.DASHBOARD_TOTAL_ORDERS],
                        state.totalOrders.toString(),
                        MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        sparklineData = state.todaySparkline,
                        rawValue = state.totalOrders.toFloat(),
                        rawValueDelayMs = 0,
                    )
                    ZyntaStatCard(
                        Icons.Default.Warning, s[StringResource.DASHBOARD_LOW_STOCK],
                        "${state.lowStockCount} ${s[StringResource.DASHBOARD_ITEMS]}",
                        if (state.lowStockCount > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.lowStockCount.toFloat(),
                        rawValueFormatter = { "${it.toLong()} ${s[StringResource.DASHBOARD_ITEMS]}" },
                        rawValueDelayMs = 50,
                    )
                    ZyntaStatCard(
                        Icons.Default.PointOfSale, s[StringResource.DASHBOARD_REGISTERS],
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
                    s[StringResource.DASHBOARD_WEEKLY_TREND],
                    listOf(ChartSeries(s[StringResource.DASHBOARD_SALES], state.weeklySalesData, MaterialTheme.colorScheme.primary)),
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
                    QuickActionCard(s[StringResource.DASHBOARD_NEW_SALE], Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                    QuickActionCard(s[StringResource.DASHBOARD_REGISTER], Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                    QuickActionCard(s[StringResource.DASHBOARD_REPORTS], Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
                }
            }

            StaggeredEntrance(delayMs = 100) {
                Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {
                    ZyntaSectionHeader(
                        s[StringResource.DASHBOARD_RECENT_ACTIVITY],
                        actionLabel = s[StringResource.DASHBOARD_SEE_ALL],
                        onAction = onNavigateToReports,
                    )
                    if (state.recentOrders.isEmpty()) {
                        EmptyActivityCard()
                    } else {
                        RecentOrdersCard(state.recentOrders.take(5), currencyFormatter)
                    }

                    if (state.lowStockCount > 0) {
                        ZyntaInfoCard(
                            Icons.Default.Warning, "${state.lowStockCount} ${s[StringResource.DASHBOARD_ITEMS_RUNNING_LOW]}",
                            description = state.lowStockNames.joinToString(", "),
                            variant = InfoCardVariant.Warning,
                        )
                    }
                    if (state.activeRegisters == 0L) {
                        ZyntaInfoCard(
                            Icons.Default.PointOfSale, s[StringResource.DASHBOARD_NO_REGISTER_OPEN],
                            description = s[StringResource.DASHBOARD_OPEN_REGISTER_TO_START],
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
    val s = LocalStrings.current
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
                    label = s[StringResource.DASHBOARD_TODAYS_SALES],
                    value = currencyFormatter.format(state.todaysSales),
                    subtitle = "${s[StringResource.DASHBOARD_OF_TARGET_PREFIX]} ${currencyFormatter.format(state.dailySalesTarget)} ${s[StringResource.DASHBOARD_TARGET_SUFFIX]}",
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
                        Icons.Default.Receipt, s[StringResource.DASHBOARD_ORDERS],
                        state.totalOrders.toString(),
                        MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.totalOrders.toFloat(),
                        rawValueDelayMs = 0,
                    )
                    ZyntaCompactStatCard(
                        Icons.Default.Warning, s[StringResource.DASHBOARD_LOW_STOCK],
                        "${state.lowStockCount}",
                        if (state.lowStockCount > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        rawValue = state.lowStockCount.toFloat(),
                        rawValueDelayMs = 50,
                    )
                    ZyntaCompactStatCard(
                        Icons.Default.PointOfSale, s[StringResource.DASHBOARD_REGISTERS],
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

        // Hourly sparkline (G7) — shows today's sales by hour for at-a-glance trend
        if (state.todaySparkline.isNotEmpty()) {
            item {
                StaggeredEntrance(delayMs = 75) {
                    ZyntaLineChart(
                        title = s[StringResource.DASHBOARD_TODAYS_HOURLY_SALES],
                        series = listOf(
                            ChartSeries(
                                s[StringResource.DASHBOARD_SALES],
                                state.todaySparkline.mapIndexed { i, v -> ChartDataPoint("${i}h", v) },
                                MaterialTheme.colorScheme.secondary,
                            )
                        ),
                        chartHeight = 120,
                        showLegend = false,
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
                    QuickActionCard(s[StringResource.DASHBOARD_NEW_SALE], Icons.Default.ShoppingCart, onNavigateToPos, Modifier.weight(1f))
                    QuickActionCard(s[StringResource.DASHBOARD_REGISTER], Icons.Default.PointOfSale, onNavigateToRegister, Modifier.weight(1f))
                    QuickActionCard(s[StringResource.DASHBOARD_REPORTS], Icons.Default.Assessment, onNavigateToReports, Modifier.weight(1f))
                }
            }
        }

        // Alerts
        if (state.lowStockCount > 0) {
            item {
                ZyntaInfoCard(
                    Icons.Default.Warning, "${state.lowStockCount} ${s[StringResource.DASHBOARD_ITEMS_RUNNING_LOW]}",
                    description = state.lowStockNames.joinToString(", "),
                    variant = InfoCardVariant.Warning,
                )
            }
        }
        if (state.activeRegisters == 0L) {
            item {
                ZyntaInfoCard(
                    Icons.Default.PointOfSale, s[StringResource.DASHBOARD_NO_REGISTER_OPEN],
                    description = s[StringResource.DASHBOARD_OPEN_REGISTER_TO_START],
                    variant = InfoCardVariant.Info, onClick = onNavigateToRegister,
                )
            }
        }

        // Chart
        item {
            ZyntaLineChart(
                s[StringResource.DASHBOARD_WEEKLY_TREND],
                listOf(ChartSeries(s[StringResource.DASHBOARD_SALES], state.weeklySalesData, MaterialTheme.colorScheme.primary)),
                chartHeight = 160, showLegend = false,
            )
        }

        // Recent Activity
        item { ZyntaSectionHeader(s[StringResource.DASHBOARD_RECENT_ACTIVITY]) }
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
            val s = LocalStrings.current
            Icon(
                Icons.Default.Receipt, null, Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Text(
                s[StringResource.DASHBOARD_NO_ORDERS_YET],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                s[StringResource.DASHBOARD_FIRST_SALE_PROMPT],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Compact chip displaying the active store name with a store icon.
 * Rendered in the top app bar actions slot to provide multi-store context.
 */
@Composable
private fun StoreNameChip(storeName: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Default.Store,
                contentDescription = LocalStrings.current[StringResource.DASHBOARD_ACTIVE_STORE],
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = storeName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
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

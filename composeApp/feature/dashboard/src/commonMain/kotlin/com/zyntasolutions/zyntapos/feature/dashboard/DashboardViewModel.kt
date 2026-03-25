package com.zyntasolutions.zyntapos.feature.dashboard

import com.zyntasolutions.zyntapos.core.analytics.AnalyticsEvents
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.designsystem.components.ChartDataPoint
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.port.SyncStatusPort
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardEffect
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardIntent
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardState
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.RecentOrderItem
import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Interval between periodic background KPI refreshes (30 seconds). */
private const val AUTO_REFRESH_INTERVAL_MS = 30_000L

/**
 * MVI ViewModel for the home dashboard screen.
 *
 * Aggregates KPI data from multiple repositories and exposes it as a single
 * [DashboardState] snapshot. All data loading happens inside [handleIntent]
 * so the composable layer remains pure UI.
 *
 * **Real-time updates:** Two mechanisms keep KPIs current without user interaction:
 * 1. [SyncStatusPort.onSyncComplete] — refresh immediately after every sync cycle.
 * 2. A 30-second periodic coroutine — fallback for low-sync-frequency scenarios.
 */
class DashboardViewModel(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val registerRepository: RegisterRepository,
    private val authRepository: AuthRepository,
    private val storeRepository: StoreRepository,
    private val settingsRepository: SettingsRepository,
    private val analytics: AnalyticsTracker,
    private val syncStatusPort: SyncStatusPort,
) : BaseViewModel<DashboardState, DashboardIntent, DashboardEffect>(DashboardState()) {

    init {
        // Refresh on every sync cycle completion (real-time KPI updates).
        viewModelScope.launch {
            syncStatusPort.onSyncComplete.collect {
                if (currentState.lastRefreshedAt > 0L) {
                    performLoad(showLoadingSpinner = false)
                }
            }
        }

        // 30-second periodic fallback refresh.
        viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (currentState.lastRefreshedAt > 0L) {
                    performLoad(showLoadingSpinner = false)
                }
            }
        }
    }

    override suspend fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.LoadDashboard -> performLoad(showLoadingSpinner = true)
            is DashboardIntent.Refresh -> {
                updateState { copy(isRefreshing = true) }
                try {
                    performLoad(showLoadingSpinner = false)
                } finally {
                    updateState { copy(isRefreshing = false) }
                }
            }
            is DashboardIntent.Logout -> onLogout()
        }
    }

    @Suppress("LongMethod")
    private suspend fun performLoad(showLoadingSpinner: Boolean) {
        if (showLoadingSpinner) {
            updateState { copy(isLoading = true) }
            analytics.logScreenView("Dashboard", "DashboardViewModel")
        }

        try {
            val user = authRepository.getSession().first()
            val activeStoreId = user?.storeId ?: ""
            val storeName = if (activeStoreId.isNotEmpty()) {
                storeRepository.getStoreName(activeStoreId) ?: ""
            } else {
                ""
            }
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val todayStart = now.toLocalDateTime(tz).date.atStartOfDayIn(tz)

            // Today's orders
            val todayOrders = orderRepository.getByDateRange(todayStart, now).first()
            val completedToday = todayOrders.filter { it.status == OrderStatus.COMPLETED }
            val sales = completedToday.sumOf { it.total }
            val orderCount = completedToday.size.toLong()

            // Hourly sparkline
            val hourlyBuckets = FloatArray(24)
            completedToday.forEach { order ->
                val hour = order.createdAt.toLocalDateTime(tz).hour
                hourlyBuckets[hour] += order.total.toFloat()
            }
            val currentHour = now.toLocalDateTime(tz).hour
            val sparkline = hourlyBuckets.take(currentHour + 1).map { it }

            // Weekly sales (last 7 days)
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
                val dayOfWeek = dayDate.dayOfWeek.ordinal
                weeklyPoints.add(ChartDataPoint(label = dayNames[dayOfWeek], value = daySales.toFloat()))
            }

            // Low stock
            val allProducts = productRepository.getAll().first()
            val lowStockProducts = allProducts.filter { it.stockQty <= it.minStockQty }

            // Active registers
            val activeSession = registerRepository.getActive().first()

            // Recent completed orders (last 10) — formattedTime pre-computed here, not in composable
            val allOrders = orderRepository.getAll().first()
            val recent = allOrders
                .filter { it.status == OrderStatus.COMPLETED }
                .sortedByDescending { it.createdAt }
                .take(10)
                .map { order ->
                    val orderTime = order.createdAt.toLocalDateTime(tz)
                    RecentOrderItem(
                        orderNumber = order.orderNumber,
                        total = order.total,
                        method = order.paymentMethod.name,
                        timestamp = order.createdAt.toEpochMilliseconds(),
                        formattedTime = "${orderTime.hour.toString().padStart(2, '0')}:${
                            orderTime.minute.toString().padStart(2, '0')
                        }",
                    )
                }

            val nowLocal = now.toLocalDateTime(tz)
            val greeting = when {
                nowLocal.hour < 12 -> "Good morning,"
                nowLocal.hour < 17 -> "Good afternoon,"
                else -> "Good evening,"
            }

            val target = settingsRepository.get("pos.daily_sales_target")?.toDoubleOrNull()
                ?: currentState.dailySalesTarget
            val computedSalesProgress = if (target > 0) (sales / target).toFloat().coerceIn(0f, 1f) else 0f
            val computedInitials = user?.name
                ?.split(" ")
                ?.take(2)
                ?.mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                ?.joinToString("")
                ?.takeIf { it.isNotEmpty() }
                ?: "M"

            updateState {
                copy(
                    currentUser = user,
                    activeStoreId = activeStoreId,
                    storeName = storeName,
                    todaysSales = sales,
                    totalOrders = orderCount,
                    lowStockCount = lowStockProducts.size.toLong(),
                    lowStockNames = lowStockProducts.take(5).map { it.name },
                    activeRegisters = if (activeSession != null) 1L else 0L,
                    recentOrders = recent,
                    weeklySalesData = weeklyPoints,
                    todaySparkline = sparkline,
                    isLoading = false,
                    greetingText = greeting,
                    salesProgress = computedSalesProgress,
                    userInitials = computedInitials,
                    dailySalesTarget = target,
                    lastRefreshedAt = now.toEpochMilliseconds(),
                )
            }
        } catch (e: Exception) {
            if (showLoadingSpinner) updateState { copy(isLoading = false) }
            sendEffect(DashboardEffect.ShowError(e.message ?: "Failed to load dashboard"))
        }
    }

    private suspend fun onLogout() {
        analytics.logEvent(AnalyticsEvents.LOGOUT)
        analytics.setUserId(null)
        authRepository.logout()
    }
}

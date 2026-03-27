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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

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

    private suspend fun performLoad(showLoadingSpinner: Boolean) {
        if (showLoadingSpinner) {
            updateState { copy(isLoading = true) }
            analytics.logScreenView("Dashboard", "DashboardViewModel")
        }
        try {
            val user = authRepository.getSession().first()
            val activeStoreId = user?.storeId ?: ""
            val storeName = if (activeStoreId.isNotEmpty()) storeRepository.getStoreName(activeStoreId) ?: "" else ""
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val todayStart = now.toLocalDateTime(tz).date.atStartOfDayIn(tz)

            val (sales, orderCount, sparkline, weeklyPoints) = loadTodayMetrics(tz, now, todayStart)
            val (yesterdaySalesTotal, yesterdayOrderCount, lastWeekSales,
                salesVsYesterday, ordersVsYesterday, salesVsLastWeek) =
                loadPeriodComparison(tz, now, todayStart, sales, orderCount)
            val recent = loadRecentOrders(tz)

            val lowStockProducts = productRepository.getAll().first().filter { it.stockQty <= it.minStockQty }
            val activeSession = registerRepository.getActive().first()

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
                ?.split(" ")?.take(2)
                ?.mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                ?.joinToString("")?.takeIf { it.isNotEmpty() } ?: "M"

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
                    yesterdaySales = yesterdaySalesTotal,
                    yesterdayOrders = yesterdayOrderCount,
                    salesChangePercent = salesVsYesterday,
                    ordersChangePercent = ordersVsYesterday,
                    lastWeekSameDaySales = lastWeekSales,
                    salesChangeVsLastWeek = salesVsLastWeek,
                )
            }
        } catch (e: Exception) {
            if (showLoadingSpinner) updateState { copy(isLoading = false) }
            sendEffect(DashboardEffect.ShowError(e.message ?: "Failed to load dashboard"))
        }
    }

    /** Loads today's sales KPIs: completed orders total, count, hourly sparkline, and 7-day chart. */
    private suspend fun loadTodayMetrics(
        tz: TimeZone,
        now: Instant,
        todayStart: Instant,
    ): TodayMetrics {
        val todayOrders = orderRepository.getByDateRange(todayStart, now).first()
        val completedToday = todayOrders.filter { it.status == OrderStatus.COMPLETED }
        val sales = completedToday.sumOf { it.total }
        val orderCount = completedToday.size.toLong()

        val hourlyBuckets = FloatArray(24)
        completedToday.forEach { order ->
            val hour = order.createdAt.toLocalDateTime(tz).hour
            hourlyBuckets[hour] += order.total.toFloat()
        }
        val sparkline = hourlyBuckets.take(now.toLocalDateTime(tz).hour + 1).map { it }

        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val weeklyPoints = mutableListOf<ChartDataPoint>()
        for (i in 6 downTo 0) {
            val dayDate = now.toLocalDateTime(tz).date.minus(i.toLong(), DateTimeUnit.DAY)
            val dayStart = dayDate.atStartOfDayIn(tz)
            val dayEnd = if (i == 0) now
            else now.toLocalDateTime(tz).date.minus((i - 1).toLong(), DateTimeUnit.DAY).atStartOfDayIn(tz)
            val daySales = orderRepository.getByDateRange(dayStart, dayEnd).first()
                .filter { it.status == OrderStatus.COMPLETED }.sumOf { it.total }
            weeklyPoints.add(ChartDataPoint(label = dayNames[dayDate.dayOfWeek.ordinal], value = daySales.toFloat()))
        }
        return TodayMetrics(sales, orderCount, sparkline, weeklyPoints)
    }

    /** Loads period-comparison figures: yesterday and last-week-same-day, and change percentages. */
    private suspend fun loadPeriodComparison(
        tz: TimeZone,
        now: Instant,
        todayStart: Instant,
        currentSales: Double,
        currentOrderCount: Long,
    ): PeriodComparison {
        val yesterdayDate = now.toLocalDateTime(tz).date.minus(1L, DateTimeUnit.DAY)
        val yesterdayCompleted = orderRepository
            .getByDateRange(yesterdayDate.atStartOfDayIn(tz), todayStart).first()
            .filter { it.status == OrderStatus.COMPLETED }
        val yesterdaySales = yesterdayCompleted.sumOf { it.total }
        val yesterdayOrders = yesterdayCompleted.size.toLong()

        val lastWeekDate = now.toLocalDateTime(tz).date.minus(7L, DateTimeUnit.DAY)
        val lastWeekSales = orderRepository
            .getByDateRange(
                lastWeekDate.atStartOfDayIn(tz),
                lastWeekDate.minus((-1).toLong(), DateTimeUnit.DAY).atStartOfDayIn(tz),
            ).first()
            .filter { it.status == OrderStatus.COMPLETED }.sumOf { it.total }

        fun changePercent(current: Double, previous: Double) =
            if (previous > 0) ((current - previous) / previous) * 100.0 else 0.0

        return PeriodComparison(
            yesterdaySales, yesterdayOrders, lastWeekSales,
            changePercent(currentSales, yesterdaySales),
            changePercent(currentOrderCount.toDouble(), yesterdayOrders.toDouble()),
            changePercent(currentSales, lastWeekSales),
        )
    }

    /** Loads the 10 most recent completed orders with pre-formatted timestamps. */
    private suspend fun loadRecentOrders(tz: TimeZone): List<RecentOrderItem> {
        return orderRepository.getAll().first()
            .filter { it.status == OrderStatus.COMPLETED }
            .sortedByDescending { it.createdAt }
            .take(10)
            .map { order ->
                val t = order.createdAt.toLocalDateTime(tz)
                RecentOrderItem(
                    orderNumber = order.orderNumber,
                    total = order.total,
                    method = order.paymentMethod.name,
                    timestamp = order.createdAt.toEpochMilliseconds(),
                    formattedTime = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}",
                )
            }
    }

    private data class TodayMetrics(
        val sales: Double,
        val orderCount: Long,
        val sparkline: List<Float>,
        val weeklyPoints: List<ChartDataPoint>,
    )

    private data class PeriodComparison(
        val yesterdaySales: Double,
        val yesterdayOrders: Long,
        val lastWeekSales: Double,
        val salesVsYesterday: Double,
        val ordersVsYesterday: Double,
        val salesVsLastWeek: Double,
    )

    private suspend fun onLogout() {
        analytics.logEvent(AnalyticsEvents.LOGOUT)
        analytics.setUserId(null)
        authRepository.logout()
    }
}

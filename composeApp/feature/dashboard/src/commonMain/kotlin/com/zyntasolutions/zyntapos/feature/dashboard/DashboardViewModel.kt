package com.zyntasolutions.zyntapos.feature.dashboard

import com.zyntasolutions.zyntapos.designsystem.components.ChartDataPoint
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardEffect
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardIntent
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.DashboardState
import com.zyntasolutions.zyntapos.feature.dashboard.mvi.RecentOrderItem
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
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
 */
class DashboardViewModel(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val registerRepository: RegisterRepository,
    private val authRepository: AuthRepository,
) : BaseViewModel<DashboardState, DashboardIntent, DashboardEffect>(DashboardState()) {

    override suspend fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.LoadDashboard -> loadDashboard()
            is DashboardIntent.Logout -> onLogout()
        }
    }

    private suspend fun loadDashboard() {
        updateState { copy(isLoading = true) }

        try {
            val user = authRepository.getSession().first()
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
                        formattedTime = "${orderTime.hour.toString().padStart(2, '0')}:${orderTime.minute.toString().padStart(2, '0')}",
                    )
                }

            val nowLocal = now.toLocalDateTime(tz)
            val greeting = when {
                nowLocal.hour < 12 -> "Good morning,"
                nowLocal.hour < 17 -> "Good afternoon,"
                else -> "Good evening,"
            }

            // Derived display values computed in VM so composables stay pure (MVI)
            val target = currentState.dailySalesTarget
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
                )
            }
        } catch (e: Exception) {
            updateState { copy(isLoading = false) }
            sendEffect(DashboardEffect.ShowError(e.message ?: "Failed to load dashboard"))
        }
    }

    private suspend fun onLogout() {
        authRepository.logout()
    }
}

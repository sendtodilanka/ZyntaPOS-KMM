package com.zyntasolutions.zyntapos.feature.dashboard.mvi

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.designsystem.components.ChartDataPoint
import com.zyntasolutions.zyntapos.domain.model.User

/**
 * Immutable UI state for the home dashboard screen.
 *
 * Holds all KPI metrics, chart data, recent activity, and alert information
 * that the dashboard displays. Updated atomically via [DashboardViewModel.updateState].
 */
@Immutable
data class DashboardState(
    val currentUser: User? = null,
    /** ID of the currently active store (from auth session). */
    val activeStoreId: String = "",
    /** Display name of the currently active store. */
    val storeName: String = "",
    val todaysSales: Double = 0.0,
    val totalOrders: Long = 0L,
    val lowStockCount: Long = 0L,
    val lowStockNames: List<String> = emptyList(),
    val activeRegisters: Long = 0L,
    val recentOrders: List<RecentOrderItem> = emptyList(),
    val weeklySalesData: List<ChartDataPoint> = emptyList(),
    val todaySparkline: List<Float> = emptyList(),
    val isLoading: Boolean = true,
    /** True during a pull-to-refresh user gesture (swipe indicator). Distinct from initial [isLoading]. */
    val isRefreshing: Boolean = false,
    /** Epoch milliseconds of the last successful KPI data load. Shown in the header subtitle. */
    val lastRefreshedAt: Long = 0L,
    /** Daily sales target in currency units. Shown in the hero KPI card progress ring. */
    val dailySalesTarget: Double = 75_000.0,
    /** Pre-computed progress ratio (0f–1f) for the sales target ring. Computed in ViewModel. */
    val salesProgress: Float = 0f,
    // ── Period comparison (G7) ───────────────────────────────────────────────
    /** Yesterday's completed sales total for period comparison. */
    val yesterdaySales: Double = 0.0,
    /** Yesterday's completed order count. */
    val yesterdayOrders: Long = 0L,
    /** Sales change % vs yesterday — positive = growth, negative = decline. */
    val salesChangePercent: Double = 0.0,
    /** Orders change % vs yesterday. */
    val ordersChangePercent: Double = 0.0,
    /** Last week same-day sales for longer-term comparison. */
    val lastWeekSameDaySales: Double = 0.0,
    /** Sales change % vs same day last week. */
    val salesChangeVsLastWeek: Double = 0.0,
    /** Pre-computed user initials (e.g. "JS") for the avatar chip. Computed in ViewModel. */
    val userInitials: String = "M",
    /** Time-aware greeting string e.g. "Good morning," / "Good afternoon,". */
    val greetingText: String = "Good morning,",
)

/**
 * Lightweight projection of a completed order for the recent activity list.
 *
 * @property formattedTime Pre-formatted "HH:mm" string computed in [DashboardViewModel].
 * @property chipVariantName Payment method display label for the status chip (e.g. "CASH", "CARD").
 */
@Immutable
data class RecentOrderItem(
    val orderNumber: String,
    val total: Double,
    val method: String,
    val timestamp: Long,
    val formattedTime: String = "",
)

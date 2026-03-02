package com.zyntasolutions.zyntapos.feature.dashboard.mvi

import com.zyntasolutions.zyntapos.designsystem.components.ChartDataPoint
import com.zyntasolutions.zyntapos.domain.model.User

/**
 * Immutable UI state for the home dashboard screen.
 *
 * Holds all KPI metrics, chart data, recent activity, and alert information
 * that the dashboard displays. Updated atomically via [DashboardViewModel.updateState].
 */
data class DashboardState(
    val currentUser: User? = null,
    val todaysSales: Double = 0.0,
    val totalOrders: Long = 0L,
    val lowStockCount: Long = 0L,
    val lowStockNames: List<String> = emptyList(),
    val activeRegisters: Long = 0L,
    val recentOrders: List<RecentOrderItem> = emptyList(),
    val weeklySalesData: List<ChartDataPoint> = emptyList(),
    val todaySparkline: List<Float> = emptyList(),
    val isLoading: Boolean = true,
    /** Daily sales target in currency units. Shown in the hero KPI card progress ring. */
    val dailySalesTarget: Double = 75_000.0,
    /** Time-aware greeting string e.g. "Good morning," / "Good afternoon,". */
    val greetingText: String = "Good morning,",
)

/**
 * Lightweight projection of a completed order for the recent activity list.
 */
data class RecentOrderItem(
    val orderNumber: String,
    val total: Double,
    val method: String,
    val timestamp: Long,
)

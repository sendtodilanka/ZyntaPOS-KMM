package com.zyntasolutions.zyntapos.feature.multistore.dashboard

import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData

/**
 * Immutable state for the multi-store global dashboard (C3.3).
 *
 * Shows aggregated KPIs across all accessible stores with per-store
 * comparison data and a store selector for context switching.
 */
data class MultiStoreDashboardState(
    /** All stores the current user has access to. */
    val stores: List<Store> = emptyList(),
    /** Currently active store (the one the user is operating as). */
    val activeStore: Store? = null,
    /** Per-store sales comparison data for the selected period. */
    val storeComparison: List<StoreSalesData> = emptyList(),
    /** Aggregated total revenue across all stores for the period. */
    val totalRevenue: Double = 0.0,
    /** Aggregated total order count across all stores for the period. */
    val totalOrders: Int = 0,
    /** Overall average order value across all stores. */
    val overallAOV: Double = 0.0,
    /** Currently selected period filter. */
    val selectedPeriod: DashboardPeriod = DashboardPeriod.TODAY,
    /** Loading indicator. */
    val isLoading: Boolean = true,
    /** Error message, if any. */
    val error: String? = null,
)

/** Period filter options for the dashboard. */
enum class DashboardPeriod(val label: String, val days: Int) {
    TODAY("Today", 1),
    WEEK("This Week", 7),
    MONTH("This Month", 30),
}

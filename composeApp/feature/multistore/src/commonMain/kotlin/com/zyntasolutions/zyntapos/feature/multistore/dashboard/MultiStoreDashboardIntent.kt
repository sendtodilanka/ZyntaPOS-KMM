package com.zyntasolutions.zyntapos.feature.multistore.dashboard

import com.zyntasolutions.zyntapos.domain.model.Store

/**
 * User actions for the multi-store global dashboard (C3.3).
 */
sealed interface MultiStoreDashboardIntent {
    /** Initial load of stores + KPIs. */
    data object LoadDashboard : MultiStoreDashboardIntent
    /** User selected a different period filter. */
    data class SelectPeriod(val period: DashboardPeriod) : MultiStoreDashboardIntent
    /** User switched the active store via the store selector. */
    data class SwitchStore(val store: Store) : MultiStoreDashboardIntent
    /** Pull-to-refresh. */
    data object Refresh : MultiStoreDashboardIntent
}

package com.zyntasolutions.zyntapos.feature.multistore.dashboard

/**
 * One-shot side effects for the multi-store dashboard (C3.3).
 */
sealed interface MultiStoreDashboardEffect {
    /** Show an error toast/snackbar. */
    data class ShowError(val message: String) : MultiStoreDashboardEffect
    /** Navigate to a specific store's single-store dashboard. */
    data class NavigateToStoreDashboard(val storeId: String) : MultiStoreDashboardEffect
    /** Store switch completed — notify parent to update session context. */
    data class StoreSwitched(val storeId: String, val storeName: String) : MultiStoreDashboardEffect
}

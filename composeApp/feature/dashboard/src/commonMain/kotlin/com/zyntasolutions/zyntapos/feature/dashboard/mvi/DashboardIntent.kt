package com.zyntasolutions.zyntapos.feature.dashboard.mvi

/**
 * Sealed interface representing every user-driven action on the dashboard.
 */
sealed interface DashboardIntent {
    /** Initial data load triggered when the screen enters composition. */
    data object LoadDashboard : DashboardIntent

    /**
     * Silent background refresh — does NOT set [DashboardState.isLoading] to true.
     * Triggered automatically by sync cycle completion or the 30-second periodic timer.
     * Also used for pull-to-refresh (user gesture).
     */
    data object Refresh : DashboardIntent

    /** User tapped the logout button in the profile header. */
    data object Logout : DashboardIntent
}

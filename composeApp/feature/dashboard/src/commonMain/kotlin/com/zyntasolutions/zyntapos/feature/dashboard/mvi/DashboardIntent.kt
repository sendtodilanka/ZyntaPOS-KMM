package com.zyntasolutions.zyntapos.feature.dashboard.mvi

/**
 * Sealed interface representing every user-driven action on the dashboard.
 */
sealed interface DashboardIntent {
    /** Initial data load triggered when the screen enters composition. */
    data object LoadDashboard : DashboardIntent

    /** User tapped the logout button in the profile header. */
    data object Logout : DashboardIntent
}

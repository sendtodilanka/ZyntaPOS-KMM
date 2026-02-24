package com.zyntasolutions.zyntapos.feature.dashboard.mvi

/**
 * One-shot side effects emitted by [DashboardViewModel].
 *
 * Collected via `LaunchedEffect(Unit) { viewModel.effects.collect { … } }`.
 */
sealed interface DashboardEffect {
    /** An unrecoverable error occurred while loading dashboard data. */
    data class ShowError(val message: String) : DashboardEffect
}

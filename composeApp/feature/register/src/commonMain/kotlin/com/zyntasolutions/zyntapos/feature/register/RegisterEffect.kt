package com.zyntasolutions.zyntapos.feature.register

/**
 * One-shot side-effects emitted by [RegisterViewModel] to the UI layer.
 *
 * Effects are delivered exactly once via a [kotlinx.coroutines.channels.Channel].
 * Compose screens collect [RegisterViewModel.effects] inside a `LaunchedEffect`.
 */
sealed interface RegisterEffect {

    /** Navigate to the Open Register screen (no open session exists). */
    data object NavigateToOpenRegister : RegisterEffect

    /** Navigate to the Register Dashboard (session successfully opened). */
    data object NavigateToDashboard : RegisterEffect

    /** Navigate to the Close Register screen. */
    data object NavigateToCloseRegister : RegisterEffect

    /** Navigate to the Z-Report screen for the given session. */
    data class NavigateToZReport(val sessionId: String) : RegisterEffect

    /** Show a transient success snackbar. */
    data class ShowSuccess(val message: String) : RegisterEffect

    /** Show a transient error snackbar. */
    data class ShowError(val message: String) : RegisterEffect
}

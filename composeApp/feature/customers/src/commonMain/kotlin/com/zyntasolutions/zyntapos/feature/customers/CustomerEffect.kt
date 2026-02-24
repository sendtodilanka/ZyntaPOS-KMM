package com.zyntasolutions.zyntapos.feature.customers

/**
 * One-shot side effects emitted by [CustomerViewModel].
 *
 * Consumed by the UI exactly once via `LaunchedEffect` + `collectLatest`.
 * Used for navigation events, toasts, and other actions that should not
 * survive recomposition.
 */
sealed interface CustomerEffect {

    /** Navigate to the customer detail screen with the given [customerId]. */
    data class NavigateToDetail(val customerId: String?) : CustomerEffect

    /** Navigate to the customer wallet screen for [customerId]. */
    data class NavigateToWallet(val customerId: String) : CustomerEffect

    /** Navigate back to the customer list. */
    data object NavigateToList : CustomerEffect

    /** Show a brief error snackbar with [message]. */
    data class ShowError(val message: String) : CustomerEffect

    /** Show a brief success snackbar with [message]. */
    data class ShowSuccess(val message: String) : CustomerEffect
}

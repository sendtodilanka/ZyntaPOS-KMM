package com.zyntasolutions.zyntapos.feature.admin

/**
 * One-shot side effects emitted by [AdminViewModel] to the UI.
 *
 * Delivered via a buffered [Channel] — each effect is received exactly once.
 */
sealed interface AdminEffect {
    /** Show a non-blocking snackbar message. */
    data class ShowSnackbar(val message: String) : AdminEffect
    /** Application must restart after a successful restore. */
    data object RestartRequired : AdminEffect
}

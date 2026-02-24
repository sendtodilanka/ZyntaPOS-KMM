package com.zyntasolutions.zyntapos.debug.mvi

/**
 * One-shot side effects emitted by [DebugViewModel].
 *
 * Collected by [DebugScreen] via the [BaseViewModel.effects] Flow.
 */
sealed class DebugEffect {
    /** Show a transient snackbar message outside the main state flow. */
    data class ShowSnackbar(val message: String) : DebugEffect()

    /** Pop the debug screen off the back stack. */
    data object NavigateUp : DebugEffect()

    /**
     * Trigger a platform-level file share / save dialog.
     * @param filePath Absolute path to the exported file.
     */
    data class ShareFile(val filePath: String) : DebugEffect()
}

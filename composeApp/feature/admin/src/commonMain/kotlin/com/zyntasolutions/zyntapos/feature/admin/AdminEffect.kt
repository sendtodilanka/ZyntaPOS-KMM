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
    /**
     * Carries a CSV-formatted audit log export for the platform to save or share.
     * The platform layer (Android: ShareSheet / Desktop: SaveFileDialog) handles the
     * actual file I/O; the ViewModel only generates the content.
     *
     * @property csvContent Full UTF-8 CSV string (header + one row per entry).
     * @property fileName   Suggested file name (e.g., `audit_log_2026-03-04.csv`).
     */
    data class ShareCsvExport(val csvContent: String, val fileName: String) : AdminEffect
}

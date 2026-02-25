package com.zyntasolutions.zyntapos.feature.media

/**
 * All user-triggered and system-driven events for the Media Library feature.
 *
 * Dispatched by Composable screens → [MediaViewModel.handleIntent].
 *
 * ### Categories
 * - **Lifecycle:** [LoadMediaForEntity]
 * - **File selection:** [SelectFile], [ClearSelection]
 * - **Add media:** [ShowAddDialog], [HideAddDialog], [UpdateFilePath], [ConfirmAddFile]
 * - **Primary image:** [SetAsPrimary]
 * - **Delete:** [DeleteFile]
 * - **UI:** [DismissError], [DismissSuccess]
 */
sealed interface MediaIntent {

    // ── Lifecycle ─────────────────────────────────────────────────────────
    /** Scopes the media library to a specific entity. Re-emits on entity change. */
    data class LoadMediaForEntity(val entityType: String, val entityId: String) : MediaIntent

    // ── File selection ────────────────────────────────────────────────────
    data class SelectFile(val fileId: String) : MediaIntent
    data object ClearSelection : MediaIntent

    // ── Add media ─────────────────────────────────────────────────────────
    data object ShowAddDialog : MediaIntent
    data object HideAddDialog : MediaIntent
    /** Update the file path field in the add-media dialog. */
    data class UpdateFilePath(val path: String) : MediaIntent
    /** Validate and save the entered file path as a new MediaFile record. */
    data object ConfirmAddFile : MediaIntent

    // ── Primary image ─────────────────────────────────────────────────────
    /** Marks the selected file as the primary image for the entity. */
    data class SetAsPrimary(val fileId: String) : MediaIntent

    // ── Delete ────────────────────────────────────────────────────────────
    data class DeleteFile(val fileId: String) : MediaIntent

    // ── UI Feedback ────────────────────────────────────────────────────────
    data object DismissError : MediaIntent
    data object DismissSuccess : MediaIntent
}

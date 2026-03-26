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

    // ── Full-screen preview (G15) ─────────────────────────────────────────
    /** Open full-screen preview for the given file. */
    data class ShowFullScreenPreview(val fileId: String) : MediaIntent
    /** Close the full-screen preview. */
    data object HideFullScreenPreview : MediaIntent

    // ── Primary image ─────────────────────────────────────────────────────
    /** Marks the selected file as the primary image for the entity. */
    data class SetAsPrimary(val fileId: String) : MediaIntent

    // ── Delete ────────────────────────────────────────────────────────────
    data class DeleteFile(val fileId: String) : MediaIntent

    // ── Image crop/compress (G15) ───────────────────────────────────────────
    /** Opens the crop/compress editor for the given file. */
    data class OpenImageEditor(val fileId: String) : MediaIntent
    /** Closes the crop/compress editor without saving. */
    data object CloseImageEditor : MediaIntent
    /** Sets the crop aspect ratio in the editor. */
    data class SetCropAspectRatio(val ratio: CropAspectRatio) : MediaIntent
    /** Sets the JPEG compression quality (1–100). */
    data class SetCompressionQuality(val quality: Int) : MediaIntent
    /** Sets the max width for resize (0 = no resize). */
    data class SetResizeMaxWidth(val maxWidth: Int) : MediaIntent
    /** Applies crop/compress settings and saves the processed image. */
    data object ApplyImageProcessing : MediaIntent

    // ── Batch Upload (G15) ─────────────────────────────────────────────────
    /** Show the batch upload dialog. */
    data object ShowBatchDialog : MediaIntent
    /** Dismiss the batch upload dialog. */
    data object DismissBatchDialog : MediaIntent
    /** Add file paths to the batch. */
    data class AddBatchFiles(val paths: List<String>) : MediaIntent
    /** Remove a file from the batch. */
    data class RemoveBatchFile(val path: String) : MediaIntent
    /** Execute the batch upload. */
    data object ExecuteBatchUpload : MediaIntent

    // ── UI Feedback ────────────────────────────────────────────────────────
    data object DismissError : MediaIntent
    data object DismissSuccess : MediaIntent
}

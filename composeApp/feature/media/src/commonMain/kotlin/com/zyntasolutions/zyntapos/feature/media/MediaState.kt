package com.zyntasolutions.zyntapos.feature.media

import com.zyntasolutions.zyntapos.domain.model.MediaFile

/**
 * Immutable UI state for the Media Library feature (Sprints 16–17).
 *
 * The media feature is entity-scoped: all operations are performed against a
 * specific [entityType] / [entityId] pair (e.g., "Product" / "prod_123").
 *
 * @property entityType    Polymorphic owner type ('Product', 'Category', 'Employee', 'Store').
 * @property entityId      FK to the owning entity.
 * @property mediaFiles    All media files for the current entity, primary file first.
 * @property selectedFile  The currently focused file (for detail view / set-primary / delete).
 * @property showAddDialog Whether the "Add media" input dialog is visible.
 * @property addFilePath   File path being entered in the add-media dialog.
 * @property addFileError  Validation error for the file path field.
 * @property isLoading     True while any async operation is in flight.
 * @property error         Non-null on repository or validation error.
 * @property successMessage One-shot success message (shown as Snackbar, then cleared).
 */
data class MediaState(
    val entityType: String = "",
    val entityId: String = "",
    val mediaFiles: List<MediaFile> = emptyList(),
    val selectedFile: MediaFile? = null,
    /** File currently shown in full-screen preview. Null when preview is closed. */
    val previewFile: MediaFile? = null,
    val showAddDialog: Boolean = false,
    val addFilePath: String = "",
    val addFileError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // ── Image crop/compress (G15) ───────────────────────────────────────────
    /** Non-null when the crop/compress editor is open for a specific file. */
    val editingFile: MediaFile? = null,
    /** Crop aspect ratio: FREE, SQUARE, 4:3, 16:9. */
    val cropAspectRatio: CropAspectRatio = CropAspectRatio.FREE,
    /** JPEG compression quality 1–100 (default 80). */
    val compressionQuality: Int = 80,
    /** Target max width in pixels for resize. 0 = no resize. */
    val resizeMaxWidth: Int = 0,
    /** Whether a crop/compress operation is in progress. */
    val isProcessing: Boolean = false,
)

/**
 * Predefined crop aspect ratios for the image editor (G15).
 */
enum class CropAspectRatio(val label: String, val widthRatio: Float, val heightRatio: Float) {
    FREE("Free", 0f, 0f),
    SQUARE("1:1", 1f, 1f),
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_3_4("3:4", 3f, 4f),
}

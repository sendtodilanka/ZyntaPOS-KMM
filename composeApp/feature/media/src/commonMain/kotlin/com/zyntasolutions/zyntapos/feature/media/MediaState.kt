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
    val showAddDialog: Boolean = false,
    val addFilePath: String = "",
    val addFileError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

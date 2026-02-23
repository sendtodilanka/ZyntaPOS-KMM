package com.zyntasolutions.zyntapos.designsystem.util

import androidx.compose.runtime.Composable

// ─────────────────────────────────────────────────────────────────────────────
// PlatformFilePicker — Cross-platform file selection for ZyntaPOS
//
// Provides a composable callback mechanism for picking files from the
// device's file system. Used by:
//   - BackupSettingsScreen (restore from .db backup file)
//   - ProductDetailScreen  (image picker for product photos)
//   - GeneralSettingsScreen (store logo upload)
//
// Platform implementations:
//   Android  → rememberLauncherForActivityResult with OpenDocument contract
//   Desktop  → javax.swing.JFileChooser on Dispatchers.IO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Result of a file pick operation.
 *
 * @property path    Absolute file path or content URI string.
 * @property name    Display name of the selected file (e.g. "backup.db").
 * @property sizeBytes  File size in bytes, or -1 if unknown.
 */
data class PickedFile(
    val path: String,
    val name: String,
    val sizeBytes: Long = -1L,
)

/**
 * File type filter for the file picker dialog.
 */
enum class FilePickerMode {
    /** Database files (.db, .sqlite, .sqlite3) for backup/restore. */
    DATABASE,

    /** Image files (.jpg, .jpeg, .png, .webp) for product/logo images. */
    IMAGE,

    /** All file types. */
    ANY,
}

/**
 * Shows a platform-native file picker dialog.
 *
 * This composable function integrates with the platform's file selection UI:
 * - **Android:** Launches `ActivityResultContracts.OpenDocument` via the
 *   Activity result API. Returns a content:// URI as the file path.
 * - **Desktop:** Opens a `JFileChooser` dialog on a background thread.
 *   Returns an absolute file system path.
 *
 * @param show        When `true`, the file picker is launched. Reset to `false`
 *                    by the caller after [onResult] is invoked.
 * @param mode        The type of files to filter for in the picker dialog.
 * @param onResult    Callback invoked with the selected [PickedFile], or `null`
 *                    if the user cancelled the dialog.
 */
@Composable
expect fun PlatformFilePicker(
    show: Boolean,
    mode: FilePickerMode = FilePickerMode.ANY,
    onResult: (PickedFile?) -> Unit,
)

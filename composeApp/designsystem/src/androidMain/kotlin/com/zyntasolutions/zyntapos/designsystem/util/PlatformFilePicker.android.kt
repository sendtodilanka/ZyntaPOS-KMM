package com.zyntasolutions.zyntapos.designsystem.util

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

// ─────────────────────────────────────────────────────────────────────────────
// Android actual — PlatformFilePicker
//
// Uses the Activity Result API (ActivityResultContracts.OpenDocument) which
// launches the system document picker. Supports MIME-type filtering for
// database and image files.
//
// Returns a content:// URI as the file path. Callers that need to copy the
// file contents should use ContentResolver.openInputStream(uri).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
actual fun PlatformFilePicker(
    show: Boolean,
    mode: FilePickerMode,
    onResult: (PickedFile?) -> Unit,
) {
    val context = LocalContext.current

    val mimeTypes = when (mode) {
        FilePickerMode.DATABASE -> arrayOf(
            "application/octet-stream",
            "application/x-sqlite3",
            "*/*",
        )
        FilePickerMode.IMAGE -> arrayOf(
            "image/jpeg",
            "image/png",
            "image/webp",
        )
        FilePickerMode.ANY -> arrayOf("*/*")
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }

        val cursor = context.contentResolver.query(uri, null, null, null, null)
        var displayName = uri.lastPathSegment ?: "file"
        var size = -1L

        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = it.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0) size = it.getLong(sizeIdx)
            }
        }

        onResult(
            PickedFile(
                path = uri.toString(),
                name = displayName,
                sizeBytes = size,
            ),
        )
    }

    LaunchedEffect(show) {
        if (show) {
            launcher.launch(mimeTypes)
        }
    }
}

package com.zyntasolutions.zyntapos.designsystem.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// ─────────────────────────────────────────────────────────────────────────────
// Desktop (JVM) actual — PlatformFilePicker
//
// Opens a Swing JFileChooser dialog on Dispatchers.IO to avoid blocking the
// Compose render thread. Returns the absolute file path of the selected file.
//
// File type filters are applied based on the FilePickerMode:
//   DATABASE → .db, .sqlite, .sqlite3
//   IMAGE   → .jpg, .jpeg, .png, .webp
//   ANY     → no filter (all files)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
actual fun PlatformFilePicker(
    show: Boolean,
    mode: FilePickerMode,
    onResult: (PickedFile?) -> Unit,
) {
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect

        val result = withContext(Dispatchers.IO) {
            val chooser = JFileChooser().apply {
                dialogTitle = when (mode) {
                    FilePickerMode.DATABASE -> "Select Backup File"
                    FilePickerMode.IMAGE -> "Select Image"
                    FilePickerMode.ANY -> "Select File"
                }
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = mode == FilePickerMode.ANY

                when (mode) {
                    FilePickerMode.DATABASE -> {
                        fileFilter = FileNameExtensionFilter(
                            "Database Files (*.db, *.sqlite, *.sqlite3)",
                            "db", "sqlite", "sqlite3",
                        )
                    }
                    FilePickerMode.IMAGE -> {
                        fileFilter = FileNameExtensionFilter(
                            "Image Files (*.jpg, *.png, *.webp)",
                            "jpg", "jpeg", "png", "webp",
                        )
                    }
                    FilePickerMode.ANY -> { /* no filter */ }
                }
            }

            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file: File = chooser.selectedFile
                PickedFile(
                    path = file.absolutePath,
                    name = file.name,
                    sizeBytes = file.length(),
                )
            } else {
                null
            }
        }

        onResult(result)
    }
}

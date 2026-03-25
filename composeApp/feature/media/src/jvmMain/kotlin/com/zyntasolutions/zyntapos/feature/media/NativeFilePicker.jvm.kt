package com.zyntasolutions.zyntapos.feature.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * JVM/Desktop implementation: opens a [JFileChooser] on an IO thread so the Compose UI
 * thread is not blocked while the native dialog is displayed.
 *
 * Returns a `() -> Unit` that shows the dialog when called.
 * [onFilePicked] receives the selected file's absolute path, or `null` on cancel.
 */
@Composable
actual fun rememberNativeFilePicker(onFilePicked: (path: String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope, onFilePicked) {
        {
            scope.launch(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Select Image"
                    isMultiSelectionEnabled = false
                    fileFilter = FileNameExtensionFilter(
                        "Images (JPG, PNG, GIF, WEBP)",
                        "jpg", "jpeg", "png", "gif", "webp", "bmp",
                    )
                }
                val result = chooser.showOpenDialog(null)
                withContext(Dispatchers.Main) {
                    if (result == JFileChooser.APPROVE_OPTION) {
                        onFilePicked(chooser.selectedFile.absolutePath)
                    } else {
                        onFilePicked(null)
                    }
                }
            }
        }
    }
}

package com.zyntasolutions.zyntapos.feature.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android implementation: uses [ActivityResultContracts.GetContent] with an image MIME
 * filter to open the system media picker or file explorer.
 *
 * Returns a `() -> Unit` that launches the picker when called.
 * [onFilePicked] receives the content URI as a string (e.g. content://...), or `null` on cancel.
 */
@Composable
actual fun rememberNativeFilePicker(onFilePicked: (path: String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> onFilePicked(uri?.toString()) },
    )
    return remember(launcher) { { launcher.launch("image/*") } }
}

package com.zyntasolutions.zyntapos.feature.media

import android.content.Context
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android implementation: uses [ActivityResultContracts.TakePicture] to launch the system camera.
 *
 * Creates a temporary file in the app's external pictures directory and passes a content URI
 * (via [FileProvider]) to the camera intent. On success, [onPhotoCaptured] receives the
 * absolute file path; on cancel it receives `null`.
 */
@Composable
actual fun rememberNativeCameraLauncher(onPhotoCaptured: (path: String?) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val photoFile = remember { createImageFile(context) }
    val photoUri = remember(photoFile) {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                onPhotoCaptured(photoFile.absolutePath)
            } else {
                onPhotoCaptured(null)
            }
        },
    )
    return remember(launcher, photoUri) { { launcher.launch(photoUri) } }
}

private fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("ZYNTA_${timeStamp}_", ".jpg", storageDir)
}

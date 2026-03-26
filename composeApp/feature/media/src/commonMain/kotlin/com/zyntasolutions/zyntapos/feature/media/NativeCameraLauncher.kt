package com.zyntasolutions.zyntapos.feature.media

import androidx.compose.runtime.Composable

/**
 * Platform-specific camera launcher.
 *
 * Returns a callback to open the camera. The captured image path is returned via [onPhotoCaptured].
 * Returns `null` if camera capture is not available on this platform (e.g. JVM/Desktop).
 *
 * **IMPORTANT:** Call this composable unconditionally at the screen level —
 * not inside conditionally-shown dialogs — because rememberLauncherForActivityResult
 * (Android) must be called from a stable Composition node.
 */
@Composable
expect fun rememberNativeCameraLauncher(onPhotoCaptured: (path: String?) -> Unit): (() -> Unit)?

package com.zyntasolutions.zyntapos.feature.media

import androidx.compose.runtime.Composable

/**
 * Returns a platform-native image picker callback (G15).
 *
 * When invoked, opens the platform system image picker:
 * - **Android**: ActivityResultLauncher with GetContent (image MIME) contract
 * - **JVM/Desktop**: JFileChooser in a non-blocking coroutine
 *
 * [onFilePicked] is called with the selected file path (or URI string on Android),
 * or `null` if the user cancelled.
 *
 * **IMPORTANT:** Call this composable unconditionally at the screen level —
 * not inside conditionally-shown dialogs — because rememberLauncherForActivityResult
 * (Android) and rememberCoroutineScope (JVM) must be called from a stable
 * Composition node.
 */
@Composable
expect fun rememberNativeFilePicker(onFilePicked: (path: String?) -> Unit): () -> Unit

package com.zyntasolutions.zyntapos.feature.media

import androidx.compose.runtime.Composable

/** Camera capture is not available on JVM/Desktop. Returns null to signal unavailability. */
@Composable
actual fun rememberNativeCameraLauncher(onPhotoCaptured: (path: String?) -> Unit): (() -> Unit)? = null

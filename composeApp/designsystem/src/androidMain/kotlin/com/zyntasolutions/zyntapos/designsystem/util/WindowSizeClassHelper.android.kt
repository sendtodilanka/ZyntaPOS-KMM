package com.zyntasolutions.zyntapos.designsystem.util

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass

// ─────────────────────────────────────────────────────────────────────────────
// Android actual — currentWindowSize()
//
// Delegates to Jetpack's currentWindowAdaptiveInfo() which reads the Activity
// window metrics via WindowMetricsCalculator for accurate values including
// multi-window, foldable, and large-screen scenarios.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Android actual for [currentWindowSize].
 *
 * Uses [currentWindowAdaptiveInfo] from `androidx.compose.material3.adaptive`
 * to retrieve the canonical [WindowWidthSizeClass] and maps it to
 * ZentaPOS [WindowSize] buckets.
 */
@Composable
actual fun currentWindowSize(): WindowSize {
    val widthSizeClass = currentWindowAdaptiveInfo()
        .windowSizeClass
        .windowWidthSizeClass

    return when (widthSizeClass) {
        WindowWidthSizeClass.EXPANDED -> WindowSize.EXPANDED
        WindowWidthSizeClass.MEDIUM   -> WindowSize.MEDIUM
        else                          -> WindowSize.COMPACT  // COMPACT + any future unknowns
    }
}

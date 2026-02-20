package com.zyntasolutions.zyntapos.designsystem.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Desktop (JVM) actual — currentWindowSize()
//
// Reads the Compose window container size in pixels from LocalWindowInfo,
// converts to dp using LocalDensity, then applies the same thresholds used
// by the Material 3 WindowSizeClass spec:
//   < 600 dp  → COMPACT
//   600–840dp → MEDIUM
//   > 840 dp  → EXPANDED
//
// This approach recomposes automatically whenever the user resizes the window.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Desktop actual for [currentWindowSize].
 *
 * Computes the window size class from [LocalWindowInfo.current.containerSize]
 * using density-independent dp thresholds matching the Material 3 spec.
 */
@Composable
actual fun currentWindowSize(): WindowSize {
    val density = LocalDensity.current
    val containerSizePx = LocalWindowInfo.current.containerSize

    val windowWidthDp = with(density) { containerSizePx.width.toDp() }

    return when {
        windowWidthDp >= 840.dp -> WindowSize.EXPANDED
        windowWidthDp >= 600.dp -> WindowSize.MEDIUM
        else                    -> WindowSize.COMPACT
    }
}

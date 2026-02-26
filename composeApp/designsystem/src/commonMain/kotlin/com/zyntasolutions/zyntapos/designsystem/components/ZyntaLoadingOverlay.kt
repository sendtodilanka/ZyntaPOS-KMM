package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaLoadingOverlay — Semi-transparent scrim + CircularProgressIndicator.
// Placed in a Box above the screen content when isLoading=true.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-area loading overlay that dims content and shows a progress indicator.
 *
 * Usage:
 * ```
 * Box(modifier = Modifier.fillMaxSize()) {
 *     // Screen content
 *     YourScreenContent()
 *     ZyntaLoadingOverlay(isLoading = viewState.isLoading)
 * }
 * ```
 *
 * @param isLoading When true, the scrim and progress indicator are rendered.
 * @param modifier Optional [Modifier] for the overlay Box.
 * @param scrimAlpha Opacity of the black scrim (default 0.45f).
 * @param progressColor Color of the [CircularProgressIndicator] (default: primary).
 */
@Composable
fun ZyntaLoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    scrimAlpha: Float = 0.45f,
    progressColor: Color = Color.Unspecified,
) {
    if (!isLoading) return

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = if (progressColor == Color.Unspecified)
                MaterialTheme.colorScheme.primary
            else progressColor,
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaLoadingOverlayPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaLoadingOverlay(isLoading = true)
    }
}

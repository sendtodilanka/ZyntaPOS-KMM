package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaSnackbarHost — M3 SnackbarHost with SUCCESS/ERROR/INFO variants.
// Uses ZyntaSnackbarVisuals to carry variant metadata.
// ─────────────────────────────────────────────────────────────────────────────

/** Semantic variant for [ZyntaSnackbarVisuals]. */
enum class SnackbarVariant { SUCCESS, ERROR, INFO }

/**
 * Custom [SnackbarVisuals] carrying a [variant] for color and icon selection.
 *
 * @param message The message body text.
 * @param variant Semantic type — SUCCESS (green), ERROR (red), INFO (blue).
 * @param actionLabel Optional action button label.
 * @param withDismissAction Whether to show the built-in dismiss button.
 * @param duration How long the snackbar is displayed.
 */
data class ZyntaSnackbarVisuals(
    override val message: String,
    val variant: SnackbarVariant = SnackbarVariant.INFO,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

/**
 * A [SnackbarHost] that renders [ZyntaSnackbarVisuals] with leading icons and
 * semantic color coding.
 *
 * Place inside your [Scaffold]'s `snackbarHost` slot:
 * ```
 * val hostState = remember { SnackbarHostState() }
 * Scaffold(
 *     snackbarHost = { ZyntaSnackbarHost(hostState) }
 * ) { ... }
 * ```
 *
 * To show a snackbar:
 * ```
 * hostState.showSnackbar(ZyntaSnackbarVisuals("Saved", SnackbarVariant.SUCCESS))
 * ```
 *
 * @param hostState The [SnackbarHostState] to bind.
 * @param modifier Optional [Modifier].
 */
@Composable
fun ZyntaSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { snackbarData ->
        val visuals = snackbarData.visuals
        val (containerColor, contentColor, icon) = when {
            visuals is ZyntaSnackbarVisuals && visuals.variant == SnackbarVariant.SUCCESS ->
                Triple(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, Icons.Default.CheckCircle as ImageVector)

            visuals is ZyntaSnackbarVisuals && visuals.variant == SnackbarVariant.ERROR ->
                Triple(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, Icons.Default.Error as ImageVector)

            else -> // INFO default
                Triple(MaterialTheme.colorScheme.inverseSurface, MaterialTheme.colorScheme.inverseOnSurface, Icons.Default.Info as ImageVector)
        }

        Snackbar(
            snackbarData = snackbarData,
            containerColor = containerColor,
            contentColor = contentColor,
            actionColor = contentColor.copy(alpha = 0.85f),
            dismissActionContentColor = contentColor.copy(alpha = 0.7f),
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaSnackbarHostPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaSnackbarHost(hostState = androidx.compose.material3.SnackbarHostState())
    }
}

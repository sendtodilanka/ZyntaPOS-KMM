package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// PaymentSuccessOverlay — Animated full-screen success state.
// Shows animated checkmark + success fill, auto-dismisses after 1 500 ms.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen overlay displayed immediately after a successful payment.
 *
 * ### Animation sequence
 * 1. **Fade in** — the background fades from transparent to [successContainerColor]
 *    over 300 ms using `animateFloatAsState`.
 * 2. **Scale in** — the checkmark circle scales from 0.0 → 1.0 with an
 *    [EaseOutBack] easing for a satisfying "pop" effect.
 * 3. **Auto-dismiss** — after [dismissDelayMs] total milliseconds the composable
 *    calls [onDismissed], which should navigate to the receipt screen.
 *
 * ### Usage
 * ```kotlin
 * if (showSuccessOverlay) {
 *     PaymentSuccessOverlay(onDismissed = {
 *         showSuccessOverlay = false
 *         navController.navigate(ZentaRoute.Receipt(orderId))
 *     })
 * }
 * ```
 *
 * @param onDismissed     Called after [dismissDelayMs] to trigger navigation.
 * @param dismissDelayMs  Total visible duration before auto-dismiss (default 1 500 ms).
 * @param successContainerColor Background fill colour (defaults to [ColorScheme.tertiaryContainer]).
 */
@Composable
fun PaymentSuccessOverlay(
    onDismissed: () -> Unit,
    dismissDelayMs: Long = 1_500L,
    successContainerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
) {
    // ── Animation state ────────────────────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }

    // Background alpha: 0 → 1 on entry
    val bgAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "bgAlpha",
    )

    // Circle scale: 0 → 1 with pop easing
    val circleScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "circleScale",
    )

    // Icon alpha: fade in slightly after scale
    val iconAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "iconAlpha",
    )

    // ── Lifecycle ────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        visible = true
        delay(dismissDelayMs)
        onDismissed()
    }

    // ── Full-screen dialog overlay ────────────────────────────────────────────
    Dialog(
        onDismissRequest = { /* non-dismissable — auto-dismisses after delay */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(bgAlpha)
                .background(successContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // ── Animated checkmark circle ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(circleScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Payment Successful",
                        modifier = Modifier
                            .size(72.dp)
                            .alpha(iconAlpha),
                        tint = MaterialTheme.colorScheme.onTertiary,
                    )
                }

                // ── Success text ──────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Payment Successful",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.alpha(iconAlpha),
                    )
                    Text(
                        text = "Preparing receipt…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.alpha(iconAlpha),
                    )
                }
            }
        }
    }
}

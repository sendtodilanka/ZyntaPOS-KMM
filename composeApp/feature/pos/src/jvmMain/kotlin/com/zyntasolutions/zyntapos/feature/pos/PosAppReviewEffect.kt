package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * JVM Desktop actual implementation of [PosAppReviewEffect] — no-op.
 *
 * The Google Play In-App Review API is Android-only. On JVM Desktop the trigger
 * is immediately consumed so the state resets cleanly.
 */
@Composable
actual fun PosAppReviewEffect(trigger: Boolean, onConsumed: () -> Unit) {
    LaunchedEffect(trigger) {
        if (trigger) onConsumed()
    }
}

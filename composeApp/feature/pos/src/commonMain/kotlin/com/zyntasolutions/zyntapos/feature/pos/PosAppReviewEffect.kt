package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable that triggers the Google Play In-App Review dialog (Android)
 * or performs a no-op (JVM desktop — Play Store is Android-only).
 *
 * Place inside [PosScreen] and toggle [trigger] to `true` when
 * [PosEffect.RequestAppReview] is received from the ViewModel.
 *
 * ### Usage
 * ```kotlin
 * var reviewTrigger by remember { mutableStateOf(false) }
 * // In effect handler:
 * is PosEffect.RequestAppReview -> reviewTrigger = true
 * // In the composable body:
 * PosAppReviewEffect(trigger = reviewTrigger) { reviewTrigger = false }
 * ```
 *
 * @param trigger  Set to `true` to launch the review flow. Reset to `false` in [onConsumed].
 * @param onConsumed  Called after the review dialog completes (success or failure).
 *                    Always reset [trigger] to `false` here to prevent re-triggering.
 */
@Composable
expect fun PosAppReviewEffect(trigger: Boolean, onConsumed: () -> Unit)

package com.zyntasolutions.zyntapos.feature.pos

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Android actual implementation of [PosAppReviewEffect].
 *
 * Uses the Play In-App Review API to show the native Google Play review dialog.
 * The dialog appears as an in-app overlay — the user never leaves the POS screen.
 *
 * ### Behaviour
 * - Calls `ReviewManager.requestReviewFlow()` to fetch a [ReviewInfo] token.
 * - On success, calls `ReviewManager.launchReviewFlow(activity, reviewInfo)`.
 * - Always calls [onConsumed] — even if the API call fails — so the trigger is reset.
 *
 * ### Notes
 * - Google throttles review prompts per user (exact frequency is not disclosed).
 *   The ViewModel only emits [PosEffect.RequestAppReview] every 5 completed orders,
 *   but Google may suppress the dialog silently if shown too recently.
 * - Do not gate core POS functionality on the review dialog result.
 */
@Composable
actual fun PosAppReviewEffect(trigger: Boolean, onConsumed: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect
        val manager = ReviewManagerFactory.create(context)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                (context as? Activity)?.let { activity ->
                    manager.launchReviewFlow(activity, task.result)
                        .addOnCompleteListener { onConsumed() }
                } ?: onConsumed()
            } else {
                // requestReviewFlow failed — silently ignore and reset trigger
                onConsumed()
            }
        }
    }
}

package com.zyntasolutions.zyntapos.feature.media

/** One-shot side effects emitted by [MediaViewModel] to the UI. */
sealed interface MediaEffect {
    data class ShowSnackbar(val message: String) : MediaEffect
}

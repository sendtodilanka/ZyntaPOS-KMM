package com.zyntasolutions.zyntapos.feature.onboarding.mvi

/**
 * One-shot side effects emitted by [OnboardingViewModel].
 *
 * Collected via `LaunchedEffect(Unit) { viewModel.effects.collect { … } }`.
 */
sealed interface OnboardingEffect {
    /** Onboarding completed — navigate to the login screen. */
    data object NavigateToLogin : OnboardingEffect

    /** An error occurred that should be surfaced as a snackbar. */
    data class ShowError(val message: String) : OnboardingEffect
}

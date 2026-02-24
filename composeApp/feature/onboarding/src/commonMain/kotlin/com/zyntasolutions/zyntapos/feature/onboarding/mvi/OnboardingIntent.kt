package com.zyntasolutions.zyntapos.feature.onboarding.mvi

/**
 * Sealed interface representing every user-driven action in the onboarding wizard.
 */
sealed interface OnboardingIntent {

    // ── Step 1: Business info ──────────────────────────────────────────────
    data class BusinessNameChanged(val name: String) : OnboardingIntent
    data object NextStep : OnboardingIntent

    // ── Step 2: Admin account ──────────────────────────────────────────────
    data class AdminNameChanged(val name: String) : OnboardingIntent
    data class AdminEmailChanged(val email: String) : OnboardingIntent
    data class AdminPasswordChanged(val password: String) : OnboardingIntent
    data class AdminConfirmPasswordChanged(val confirmPassword: String) : OnboardingIntent
    data object TogglePasswordVisibility : OnboardingIntent

    /** Complete the wizard — persists business name and creates the admin account. */
    data object CompleteOnboarding : OnboardingIntent

    /** Navigate back to the previous step. */
    data object BackStep : OnboardingIntent

    /** Dismiss a displayed error. */
    data object DismissError : OnboardingIntent
}

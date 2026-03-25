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

    // ── Step 3: Store settings (currency & timezone) ───────────────────
    data class CurrencyChanged(val currencyCode: String) : OnboardingIntent
    data class TimezoneChanged(val timezoneId: String) : OnboardingIntent

    // ── Step 4: Tax setup (optional) ────────────────────────────────────
    data class TaxGroupNameChanged(val name: String) : OnboardingIntent
    data class TaxRateChanged(val rate: String) : OnboardingIntent
    data class TaxIsInclusiveChanged(val inclusive: Boolean) : OnboardingIntent
    /** Skip the optional tax setup step and complete onboarding without a tax group. */
    data object SkipTaxSetup : OnboardingIntent

    /** Complete the wizard — persists settings and creates the admin account. */
    data object CompleteOnboarding : OnboardingIntent

    /** Navigate back to the previous step. */
    data object BackStep : OnboardingIntent

    /** Dismiss a displayed error. */
    data object DismissError : OnboardingIntent
}

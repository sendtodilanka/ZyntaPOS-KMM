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
    /** Skip the optional tax setup step and advance to receipt format. */
    data object SkipTaxSetup : OnboardingIntent

    // ── Step 5: Receipt format (optional) ────────────────────────────────
    data class ReceiptHeaderChanged(val header: String) : OnboardingIntent
    data class ReceiptFooterChanged(val footer: String) : OnboardingIntent
    data class ReceiptPaperWidthChanged(val widthMm: Int) : OnboardingIntent
    data class ReceiptAutoPrintChanged(val enabled: Boolean) : OnboardingIntent
    /** Skip receipt format and advance to multi-store setup. */
    data object SkipReceiptFormat : OnboardingIntent

    // ── Step 6: Multi-store setup (optional, G2) ────────────────────────
    /** Update the name of a new store being added. */
    data class NewStoreNameChanged(val name: String) : OnboardingIntent
    /** Add the current new store entry to the list. */
    data object AddAdditionalStore : OnboardingIntent
    /** Remove an additional store from the list by index. */
    data class RemoveAdditionalStore(val index: Int) : OnboardingIntent
    /** Skip multi-store setup and complete onboarding. */
    data object SkipMultiStoreSetup : OnboardingIntent

    /** Complete the wizard — persists settings and creates the admin account. */
    data object CompleteOnboarding : OnboardingIntent

    /** Navigate back to the previous step. */
    data object BackStep : OnboardingIntent

    /** Dismiss a displayed error. */
    data object DismissError : OnboardingIntent
}

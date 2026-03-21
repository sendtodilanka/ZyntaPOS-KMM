package com.zyntasolutions.zyntapos.feature.onboarding.mvi

/**
 * Immutable UI state for the onboarding wizard.
 *
 * The wizard has three steps:
 * 1. [Step.BUSINESS_INFO] — collect the store/business name.
 * 2. [Step.ADMIN_ACCOUNT] — collect admin email + password.
 * 3. [Step.STORE_SETTINGS] — select currency and timezone.
 */
data class OnboardingState(
    // ── Step tracking ──────────────────────────────────────────────────────
    val currentStep: Step = Step.BUSINESS_INFO,

    // ── Step 1: Business info ──────────────────────────────────────────────
    val businessName: String = "",
    val businessNameError: String? = null,

    // ── Step 2: Admin account ──────────────────────────────────────────────
    val adminName: String = "",
    val adminEmail: String = "",
    val adminPassword: String = "",
    val adminConfirmPassword: String = "",
    val adminNameError: String? = null,
    val adminEmailError: String? = null,
    val adminPasswordError: String? = null,
    val adminConfirmPasswordError: String? = null,
    val isPasswordVisible: Boolean = false,

    // ── Step 3: Store settings (currency & timezone) ─────────────────────
    val currencyCode: String = "LKR",
    val timezoneId: String = "Asia/Colombo",

    // ── Async ──────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** Wizard step identifiers. */
    enum class Step { BUSINESS_INFO, ADMIN_ACCOUNT, STORE_SETTINGS }

    /** Total number of wizard steps. */
    val totalSteps: Int get() = Step.entries.size

    /** 1-based index of the current step. */
    val stepNumber: Int get() = currentStep.ordinal + 1

    /** `true` when the user is on the last step of the wizard. */
    val isLastStep: Boolean get() = currentStep == Step.STORE_SETTINGS
}

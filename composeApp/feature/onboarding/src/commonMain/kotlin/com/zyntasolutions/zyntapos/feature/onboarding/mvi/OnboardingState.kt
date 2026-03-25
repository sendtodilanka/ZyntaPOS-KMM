package com.zyntasolutions.zyntapos.feature.onboarding.mvi

/**
 * Immutable UI state for the onboarding wizard.
 *
 * The wizard has four steps:
 * 1. [Step.BUSINESS_INFO] — collect the store/business name.
 * 2. [Step.ADMIN_ACCOUNT] — collect admin email + password.
 * 3. [Step.STORE_SETTINGS] — select currency and timezone.
 * 4. [Step.TAX_SETUP] — optional basic tax group setup (can be skipped).
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

    // ── Step 4: Tax setup (optional) ─────────────────────────────────────
    /** Name of the default tax group (e.g. "VAT"). Empty = user skipped. */
    val taxGroupName: String = "VAT",
    /** Tax rate percentage (0–100). */
    val taxRate: String = "15",
    /** True when the tax is inclusive in the product price. */
    val taxIsInclusive: Boolean = false,
    val taxRateError: String? = null,

    // ── Async ──────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** Wizard step identifiers. */
    enum class Step { BUSINESS_INFO, ADMIN_ACCOUNT, STORE_SETTINGS, TAX_SETUP }

    /** Total number of wizard steps. */
    val totalSteps: Int get() = Step.entries.size

    /** 1-based index of the current step. */
    val stepNumber: Int get() = currentStep.ordinal + 1

    /** `true` when the user is on the last step of the wizard. */
    val isLastStep: Boolean get() = currentStep == Step.TAX_SETUP
}
